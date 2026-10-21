package parking.pricing;
import java.util.Objects;
/** Selection is isolated from the context. Automatic rules have explicit priority. */
public final class PricingSelector {
    private final OccupancySource source;
    private String mode = "hourly";
    public PricingSelector(OccupancySource source) { this.source = Objects.requireNonNull(source); }
    public void setMode(String mode) {
        if (!"auto".equals(mode)) manual(mode); // Validate without changing current configuration.
        this.mode = mode;
    }
    public String mode() { return mode; }
    public PricingStrategy manual(String mode) {
        return switch (mode) {
            case "hourly" -> new HourlyPricing();
            case "capped" -> new CappedPricing();
            case "demand" -> new DemandPricing(source);
            default -> throw new IllegalArgumentException("Use hourly, capped, demand or auto");
        };
    }
    public PricingStrategy select(PricingSituation situation) {
        if (!"auto".equals(mode)) return manual(mode);
        if (situation.hours() >= 6) return new CappedPricing();
        if (situation.localHour() < 6 || situation.localHour() >= 22) return new CappedPricing();
        if (source.occupancy() >= 0.8) return new DemandPricing(source);
        return new HourlyPricing();
    }
}
