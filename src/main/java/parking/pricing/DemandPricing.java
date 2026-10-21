package parking.pricing;
import java.util.Objects;
public final class DemandPricing implements PricingStrategy {
    private final OccupancySource source;
    public DemandPricing(OccupancySource source) { this.source = Objects.requireNonNull(source); }
    @Override public int amountFor(int hours) {
        DurationRules.requireValid(hours);
        double occupancy = source.occupancy();
        if (!Double.isFinite(occupancy) || occupancy < 0 || occupancy > 1)
            throw new IllegalArgumentException("Sensor occupancy must be between zero and one");
        // First two hours have no surcharge; later hours respond to live sensor data.
        return hours * 500 + (int) Math.ceil(occupancy * 250) * Math.max(0, hours - 2);
    }
}
