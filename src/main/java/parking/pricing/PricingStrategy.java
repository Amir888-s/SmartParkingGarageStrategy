package parking.pricing;
@FunctionalInterface
public interface PricingStrategy { int amountFor(int hours); }
