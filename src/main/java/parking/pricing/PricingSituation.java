package parking.pricing;
public record PricingSituation(int hours, int localHour) {
    public PricingSituation {
        DurationRules.requireValid(hours);
        if (localHour < 0 || localHour > 23) throw new IllegalArgumentException("Hour must be 0-23");
    }
}
