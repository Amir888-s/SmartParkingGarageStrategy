package parking.app;

import java.nio.channels.*;
import java.nio.file.*;
import java.time.Clock;
import java.io.IOException;
import parking.store.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;
import parking.facade.*;
import parking.pricing.*;
import parking.allocation.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Composition root and single-writer lock; closes the lock on real restarts. */
public final class GarageApplication implements AutoCloseable {
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final ParkingFacade parking;
    private final AdminFacade admin;
    private final PricingPolicy pricing = new PricingPolicy();
    private final SpaceSensorGrid sensors = new SpaceSensorGrid();
    private final SimulatedOccupancySource occupancy = new SimulatedOccupancySource(0.0);
    private final PricingSelector pricingSelector = new PricingSelector(occupancy);
    private final AllocationSelector allocationSelector = new AllocationSelector();
    private final Clock clock;
    private final ZoneId zone = ZoneId.of("Asia/Almaty");
    private boolean preferLift;
    public GarageApplication(Path directory, Clock clock) {
        this.clock = clock;
        CsvStore store = new CsvStore(directory);
        try {
            lockChannel = FileChannel.open(directory.resolve("garage.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired;
            try { acquired = lockChannel.tryLock(); }
            catch (OverlappingFileLockException e) { acquired = null; }
            if (acquired == null) { lockChannel.close(); throw new IOException("Another garage process already owns this store"); }
            lock = acquired;
        } catch (IOException e) { throw new StorageException("Cannot lock garage store", e); }
        GateController gate = new GateController(new GateRepository(store), new LicensePlateReader());
        ReservationManager spaces = new ReservationManager(new SpaceRepository(store), sensors);
        PaymentTerminal billing = new PaymentTerminal(new BillingRepository(store), pricing, new OverstayAlert(), clock);
        WorkflowRepository workflows = new WorkflowRepository(store);
        parking = new ParkingFacade(gate, spaces, billing, workflows);
        admin = new AdminFacade(gate, spaces, billing, workflows, parking);
    }
    /** Composition configuration only; the facade still owns every workflow step. */
    public void configureStrategies(String pricingMode, String allocationMode, double sensorOccupancy, boolean preferLift) {
        // Validate the complete request before mutating the live configuration.
        PricingSelector checkPricing = new PricingSelector(occupancy); checkPricing.setMode(pricingMode);
        AllocationSelector checkAllocation = new AllocationSelector(); checkAllocation.setMode(allocationMode);
        new SimulatedOccupancySource(sensorOccupancy);
        pricingSelector.setMode(pricingMode); allocationSelector.setMode(allocationMode);
        occupancy.setOccupancy(sensorOccupancy); this.preferLift = preferLift;
    }
    /** Called for new requests; automatic rules use the actual requested duration. */
    public void prepareStrategies(int hours) {
        int hour = ZonedDateTime.now(clock.withZone(zone)).getHour();
        pricing.setStrategy(pricingSelector.select(new PricingSituation(hours, hour)));
        sensors.setStrategy(allocationSelector.select(new AllocationSituation(preferLift, hour)));
    }
    public String strategyModes() { return "pricing=" + pricingSelector.mode() + ", allocation=" + allocationSelector.mode(); }
    public ParkingFacade parking() { return parking; }
    public AdminFacade admin() { return admin; }
    @Override public void close() {
        try { lock.release(); lockChannel.close(); }
        catch (IOException e) { throw new StorageException("Cannot close garage store", e); }
    }
}
