package parking.strategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import parking.allocation.*;
import parking.app.GarageApplication;
import parking.billing.*;
import parking.facade.*;
import parking.pricing.*;
import parking.space.*;
import parking.store.*;
import static org.junit.jupiter.api.Assertions.*;
import static parking.billing.PaymentTerminal.Card.*;

class StrategyTest {
    @TempDir Path directory;
    private record FakeOccupancySource(double value) implements OccupancySource {
        @Override public double occupancy() { return value; }
    }
    static Stream<PricingStrategy> prices() {
        return Stream.of(new HourlyPricing(), new CappedPricing(), new DemandPricing(new FakeOccupancySource(0.8)));
    }
    static Stream<AllocationStrategy> allocations() {
        return Stream.of(new FirstFreeAllocation(), new NearLiftAllocation(), new LeastUsedAllocation());
    }
    @ParameterizedTest @MethodSource("prices")
    void everyPricingImplementationAcceptsAllDurationsAndRejectsBothBoundaries(PricingStrategy strategy) {
        for (int hours=1; hours<=24; hours++) assertTrue(strategy.amountFor(hours)>=0);
        assertThrows(ParkingException.class, () -> strategy.amountFor(0));
        assertThrows(ParkingException.class, () -> strategy.amountFor(25));
    }
    @Test void hourlyIsLinear() { assertEquals(4000, new HourlyPricing().amountFor(8)); }
    @Test void capIsNonlinearAfterSixHours() {
        CappedPricing capped = new CappedPricing();
        assertEquals(2500, capped.amountFor(5)); assertEquals(3000,capped.amountFor(6)); assertEquals(3000,capped.amountFor(24));
    }
    @Test void demandUsesFixedFakeSensorWithGracePeriodAndRounding() {
        DemandPricing demand = new DemandPricing(new FakeOccupancySource(0.801));
        assertEquals(1000, demand.amountFor(2)); assertEquals(1701, demand.amountFor(3));
        assertEquals(5200, new DemandPricing(new FakeOccupancySource(0.8)).amountFor(8));
    }
    @ParameterizedTest @ValueSource(doubles={-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY})
    void sensorBoundaryRejectsInvalidReadings(double value) {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedOccupancySource(value));
        assertThrows(IllegalArgumentException.class, () -> new DemandPricing(new FakeOccupancySource(value)).amountFor(3));
    }
    @Test void simulatedSourceUpdatesWithoutReplacingAlgorithm() {
        SimulatedOccupancySource source = new SimulatedOccupancySource(0);
        DemandPricing demand = new DemandPricing(source);
        assertEquals(4000,demand.amountFor(8)); source.setOccupancy(1); assertEquals(5500,demand.amountFor(8));
    }
    @ParameterizedTest @CsvSource({
        "6,12,1,capped", "24,23,1,capped", "5,22,1,capped", "5,23,1,capped", "5,0,1,capped", "5,5,1,capped",
        "5,6,0.8,demand", "5,21,0.8,demand", "3,12,1,demand", "3,12,0.799,hourly", "1,6,0,hourly"})
    void everyAutomaticPricingRuleAndBoundary(int hours,int hour,double occupancy,String expected) {
        PricingSelector selector = new PricingSelector(new FakeOccupancySource(occupancy)); selector.setMode("auto");
        assertEquals(selector.manual(expected).getClass(),selector.select(new PricingSituation(hours,hour)).getClass());
    }
    @ParameterizedTest @ValueSource(strings={"hourly","capped","demand"})
    void eachManualPricingChoiceOverridesAutomaticRules(String mode) {
        PricingSelector selector=new PricingSelector(new FakeOccupancySource(1)); selector.setMode(mode);
        assertEquals(selector.manual(mode).getClass(),selector.select(new PricingSituation(24,23)).getClass());
    }
    @Test void pricingContextSwitchesAndAcceptsAnUnregisteredExtension() {
        PricingPolicy context=new PricingPolicy(new HourlyPricing()); assertEquals(4000,context.amountFor(8));
        context.setStrategy(new CappedPricing()); assertEquals(3000,context.amountFor(8));
        context.setStrategy(hours -> 42); assertEquals(42,context.amountFor(8));
        assertThrows(NullPointerException.class, () -> context.setStrategy(null));
    }
    private SpaceAvailability sample() { return new SpaceAvailability(List.of(3,1,2),Map.of(1,5L,2,2L,3,0L)); }
    @Test void firstFreeUsesSmallestIdRatherThanInputOrder() { assertEquals(1,new FirstFreeAllocation().choose(sample())); }
    @Test void nearLiftUsesDistanceThenSmallerId() {
        NearLiftAllocation strategy=new NearLiftAllocation(); assertEquals(2,strategy.choose(sample()));
        assertEquals(1,strategy.choose(new SpaceAvailability(List.of(3,1),Map.of())));
        assertEquals(3,strategy.choose(new SpaceAvailability(List.of(3),Map.of())));
    }
    @Test void leastUsedUsesHistoryThenSmallerIdWithZeroForMissingHistory() {
        LeastUsedAllocation strategy=new LeastUsedAllocation(); assertEquals(3,strategy.choose(sample()));
        assertEquals(1,strategy.choose(new SpaceAvailability(List.of(3,1),Map.of(1,2L,3,2L))));
        assertEquals(2,strategy.choose(new SpaceAvailability(List.of(1,2),Map.of(1,5L))));
    }
    @ParameterizedTest @MethodSource("allocations")
    void everyAllocationReturnsOnlyFreeUnblockedBayAndRejectsOfflineAndFull(AllocationStrategy strategy) {
        SpaceSensorGrid context=new SpaceSensorGrid(strategy);
        assertEquals(3,context.firstFree(Set.of(1),Set.of(2),true));
        assertEquals(ParkingException.Reason.SENSOR_OFFLINE,assertThrows(ParkingException.class,
            () -> context.firstFree(Set.of(),Set.of(),false)).reason());
        assertEquals(ParkingException.Reason.NO_SPACE,assertThrows(ParkingException.class,
            () -> context.firstFree(Set.of(1,2,3),Set.of(),true)).reason());
        for(int mask=0;mask<7;mask++) {
            Set<Integer> occupied=new HashSet<>(); for(int n=1;n<=3;n++) if((mask & (1 << (n-1))) != 0) occupied.add(n);
            assertFalse(occupied.contains(context.firstFree(occupied,Set.of(),true)));
        }
    }
    @ParameterizedTest @CsvSource({"true,7,lift","true,8,lift","true,17,lift","true,18,lift",
        "false,0,first","false,7,first","false,8,balanced","false,17,balanced","false,18,first","false,23,first"})
    void everyAutomaticAllocationRuleAndBoundary(boolean lift,int hour,String expected) {
        AllocationSelector selector=new AllocationSelector(); selector.setMode("auto");
        assertEquals(selector.manual(expected).getClass(),selector.select(new AllocationSituation(lift,hour)).getClass());
    }
    @ParameterizedTest @ValueSource(strings={"first","lift","balanced"})
    void manualAllocationChoiceOverridesSituation(String mode) {
        AllocationSelector selector=new AllocationSelector(); selector.setMode(mode);
        assertEquals(selector.manual(mode).getClass(),selector.select(new AllocationSituation(true,12)).getClass());
    }
    @Test void allocationContextSwitchesAndAcceptsAnUnregisteredExtension() {
        SpaceSensorGrid context=new SpaceSensorGrid(); assertEquals(1,context.choose(sample()));
        context.setStrategy(new NearLiftAllocation()); assertEquals(2,context.choose(sample()));
        context.setStrategy(new LeastUsedAllocation()); assertEquals(3,context.choose(sample()));
        context.setStrategy(input -> input.freeSpaces().get(0)); assertEquals(3,context.choose(sample()));
        assertThrows(NullPointerException.class, () -> context.setStrategy(null));
    }
    @Test void snapshotIsImmutableAndRejectsInvalidInputs() {
        List<Integer> bays=new ArrayList<>(List.of(1)); Map<Integer,Long> uses=new HashMap<>();
        SpaceAvailability snapshot=new SpaceAvailability(bays,uses); bays.clear(); uses.put(1,99L);
        assertEquals(List.of(1),snapshot.freeSpaces()); assertTrue(snapshot.previousUses().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.freeSpaces().clear());
        assertThrows(IllegalArgumentException.class, () -> new SpaceAvailability(List.of(0),Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new SpaceAvailability(List.of(1),Map.of(1,-1L)));
    }
    @Test void selectorsValidateModesAndSituationsWithoutLosingConfiguration() {
        PricingSelector price=new PricingSelector(() -> 0); price.setMode("capped");
        assertThrows(IllegalArgumentException.class, () -> price.setMode("unknown")); assertEquals("capped",price.mode());
        AllocationSelector space=new AllocationSelector(); space.setMode("lift");
        assertThrows(IllegalArgumentException.class, () -> space.setMode("unknown")); assertEquals("lift",space.mode());
        for(int h:List.of(-1,24)) {
            assertThrows(IllegalArgumentException.class, () -> new PricingSituation(1,h));
            assertThrows(IllegalArgumentException.class, () -> new AllocationSituation(false,h));
        }
        assertThrows(ParkingException.class, () -> new PricingSituation(0,12));
    }
    @Test void leastUsedReadsPersistedReleasedReservationHistory() {
        CsvStore store=new CsvStore(directory); SpaceRepository repository=new SpaceRepository(store);
        ReservationManager manager=new ReservationManager(repository,new SpaceSensorGrid(new LeastUsedAllocation()));
        assertEquals(1,manager.reserve("old").space()); manager.release("old");
        assertEquals(2,new ReservationManager(new SpaceRepository(store),new SpaceSensorGrid(new LeastUsedAllocation())).reserve("new").space());
    }
    @Test void actualFacadeUsesConfiguredStrategiesThenSwitchesBackAtRuntime() {
        Clock clock=Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"),ZoneOffset.UTC);
        try(GarageApplication app=new GarageApplication(directory,clock)) {
            app.configureStrategies("capped","lift",0.8,true); app.prepareStrategies(8);
            Workflow first=app.parking().park("STRAT01",8,APPROVE);
            assertEquals(Workflow.Status.COMPLETED,first.status());
            assertEquals(3000,app.admin().inspect(first.id()).amount()); assertEquals(2,app.admin().inspect(first.id()).space());
            app.admin().cancel(first.id());
            app.configureStrategies("hourly","first",0,false); app.prepareStrategies(8);
            Workflow second=app.parking().park("STRAT02",8,APPROVE);
            assertEquals(4000,app.admin().inspect(second.id()).amount()); assertEquals(1,app.admin().inspect(second.id()).space());
        }
    }
    @Test void automaticSelectionReevaluatesActualDurationForEachNewRequest() {
        Clock clock=Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"),ZoneOffset.UTC);
        try(GarageApplication app=new GarageApplication(directory,clock)) {
            app.configureStrategies("auto","auto",1,true); app.prepareStrategies(3);
            Workflow first=app.parking().park("AUTO01",3,APPROVE);
            assertEquals(1750,app.admin().inspect(first.id()).amount()); assertEquals(2,app.admin().inspect(first.id()).space());
            app.admin().cancel(first.id()); app.prepareStrategies(8);
            Workflow second=app.parking().park("AUTO02",8,APPROVE);
            assertEquals(3000,app.admin().inspect(second.id()).amount());
        }
    }
    @ParameterizedTest @CsvSource({"hourly,first","capped,lift","demand,balanced"})
    void bothFacadeCompensationDepthsSurviveStrategies(String price,String allocation) {
        try(GarageApplication app=new GarageApplication(directory,Clock.systemUTC())) {
            app.configureStrategies(price,allocation,0.8,true); app.prepareStrategies(8);
            app.admin().simulateFullGarage(true);
            Workflow b=app.parking().park("BSTRAT01",8,APPROVE);
            assertEquals(List.of("A committed","A revoked"),app.admin().inspect(b.id()).events());
            app.admin().simulateFullGarage(false);
            Workflow c=app.parking().park("CSTRAT01",8,DECLINE);
            assertEquals(List.of("A committed","B committed","B released","A revoked"),app.admin().inspect(c.id()).events());
        }
    }
    @Test void priceQuoteSurvivesChangedStrategyAndRestartAfterBillingSaveFailure() {
        CsvStore disk=new CsvStore(directory);
        RowStore failing=new RowStore() {
            public List<String[]> read(String table) { return disk.read(table); }
            public void write(String table,List<String[]> rows) {
                if(table.equals("billing")) throw new StorageException("Simulated billing write failure",new java.io.IOException());
                disk.write(table,rows);
            }
        };
        PaymentTerminal first=new PaymentTerminal(new BillingRepository(failing),new PricingPolicy(new CappedPricing()),new OverstayAlert(),Clock.systemUTC());
        assertThrows(StorageException.class, () -> first.confirm("retry",8,APPROVE));
        PaymentTerminal restarted=new PaymentTerminal(new BillingRepository(disk),new PricingPolicy(new DemandPricing(() -> 1)),new OverstayAlert(),Clock.systemUTC());
        assertEquals(3000,restarted.confirm("retry",8,APPROVE).amount());
        assertEquals(3000,restarted.confirm("retry",8,APPROVE).amount());
        assertThrows(ParkingException.class, () -> restarted.confirm("retry",9,APPROVE));
    }
    @Test void completedPaymentCanBeResumedAfterRuntimeTariffChange() {
        CsvStore disk=new CsvStore(directory); PricingPolicy price=new PricingPolicy(new DemandPricing(() -> 1));
        PaymentTerminal billing=new PaymentTerminal(new BillingRepository(disk),price,new OverstayAlert(),Clock.systemUTC());
        assertEquals(5500,billing.confirm("paid",8,APPROVE).amount()); price.setStrategy(new CappedPricing());
        assertEquals(5500,billing.confirm("paid",8,APPROVE).amount());
    }
    @Test void invalidLegacyRetryDoesNotPersistAnIncorrectQuote() {
        CsvStore disk=new CsvStore(directory); BillingRepository repository=new BillingRepository(disk);
        repository.save(new Payment("legacy",1000,Instant.parse("2026-10-20T12:00:00Z"),Payment.Status.PAID));
        PaymentTerminal billing=new PaymentTerminal(repository,new PricingPolicy(new CappedPricing()),new OverstayAlert(),Clock.systemUTC());
        assertThrows(ParkingException.class, () -> billing.confirm("legacy",3,APPROVE));
        assertTrue(repository.findQuote("legacy").isEmpty());
        assertEquals(1000,billing.confirm("legacy",2,APPROVE).amount());
    }
}
