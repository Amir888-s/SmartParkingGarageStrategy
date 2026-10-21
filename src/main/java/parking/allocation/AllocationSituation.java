package parking.allocation;
public record AllocationSituation(boolean preferLift, int localHour) {
    public AllocationSituation {
        if (localHour < 0 || localHour > 23) throw new IllegalArgumentException("Hour must be 0-23");
    }
}
