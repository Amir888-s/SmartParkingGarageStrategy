package parking.pricing;
public final class HourlyPricing implements PricingStrategy {
    @Override public int amountFor(int hours) { return DurationRules.requireValid(hours) * 500; }
}
