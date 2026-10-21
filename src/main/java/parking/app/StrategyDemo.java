package parking.app;

import java.util.*;
import parking.billing.PricingPolicy;
import parking.space.SpaceSensorGrid;
import parking.pricing.*;
import parking.allocation.*;

/** Deterministic comparison independent of the production store. */
public final class StrategyDemo {
    private StrategyDemo() {}
    public static void main(String[] args) { run(); }
    public static void run() {
        SimulatedOccupancySource source = new SimulatedOccupancySource(0.8);
        PricingSelector prices = new PricingSelector(source);
        PricingPolicy price = new PricingPolicy(new HourlyPricing());
        System.out.println("PRICING: identical input = 8 hours, occupancy 0.8");
        for (String name : List.of("hourly", "capped", "demand")) {
            price.setStrategy(prices.manual(name));
            System.out.println("Runtime switch -> " + name + ": " + price.amountFor(8) + " KZT");
        }
        prices.setMode("auto");
        for (PricingSituation situation : List.of(new PricingSituation(8,12), new PricingSituation(3,12), new PricingSituation(3,23))) {
            PricingStrategy selected = prices.select(situation); price.setStrategy(selected);
            System.out.println("Automatic " + situation + " -> " + selected.getClass().getSimpleName() + ": " + price.amountFor(situation.hours()));
        }
        prices.setMode("hourly");
        price.setStrategy(prices.select(new PricingSituation(8,12)));
        System.out.println("Manual override at peak -> hourly: " + price.amountFor(8));
        price.setStrategy(new DemandPricing(source)); source.setOccupancy(0);
        System.out.println("Same demand strategy, sensor replaced by new reading -> " + price.amountFor(8));

        SpaceAvailability input = new SpaceAvailability(List.of(1,2,3), Map.of(1,5L,2,2L,3,0L));
        SpaceSensorGrid grid = new SpaceSensorGrid(); AllocationSelector allocations = new AllocationSelector();
        System.out.println("ALLOCATION: identical free bays [1,2,3], previous uses {1=5,2=2,3=0}");
        for (String name : List.of("first", "lift", "balanced")) {
            grid.setStrategy(allocations.manual(name));
            System.out.println("Runtime switch -> " + name + ": bay " + grid.choose(input));
        }
        allocations.setMode("auto");
        for (AllocationSituation situation : List.of(new AllocationSituation(true,12),new AllocationSituation(false,12),new AllocationSituation(false,23))) {
            AllocationStrategy selected = allocations.select(situation); grid.setStrategy(selected);
            System.out.println("Automatic " + situation + " -> " + selected.getClass().getSimpleName() + ": bay " + grid.choose(input));
        }
        allocations.setMode("first"); grid.setStrategy(allocations.select(new AllocationSituation(true,12)));
        System.out.println("Manual override near lift -> first: bay " + grid.choose(input));
    }
}
