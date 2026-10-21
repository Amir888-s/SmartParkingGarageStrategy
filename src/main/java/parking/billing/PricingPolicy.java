package parking.billing;
import java.util.Objects;
import parking.pricing.*;
/** Pricing context; no conditional dispatch on concrete strategy types. */
public final class PricingPolicy {
    public static final int HOURLY_RATE = 500;
    private PricingStrategy strategy;
    public PricingPolicy() { this(new HourlyPricing()); }
    public PricingPolicy(PricingStrategy strategy) { setStrategy(strategy); }
    /** Compatibility entry point retained for the unchanged before/after tests. */
    public PricingPolicy(String mode, OccupancySource source) { this(new PricingSelector(source).manual(mode)); }
    public void setStrategy(PricingStrategy strategy) { this.strategy = Objects.requireNonNull(strategy); }
    public int amountFor(int hours) { return strategy.amountFor(hours); }
}
