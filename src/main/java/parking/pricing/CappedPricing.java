package parking.pricing;
public final class CappedPricing implements PricingStrategy {
    @Override public int amountFor(int hours) { return Math.min(DurationRules.requireValid(hours) * 500, 3000); }
}
