package parking.allocation;
public final class AllocationSelector {
    private String mode = "first";
    public void setMode(String mode) {
        if (!"auto".equals(mode)) manual(mode);
        this.mode = mode;
    }
    public String mode() { return mode; }
    public AllocationStrategy manual(String mode) {
        return switch (mode) {
            case "first" -> new FirstFreeAllocation();
            case "lift" -> new NearLiftAllocation();
            case "balanced" -> new LeastUsedAllocation();
            default -> throw new IllegalArgumentException("Use first, lift, balanced or auto");
        };
    }
    public AllocationStrategy select(AllocationSituation situation) {
        if (!"auto".equals(mode)) return manual(mode);
        if (situation.preferLift()) return new NearLiftAllocation();
        if (situation.localHour() >= 8 && situation.localHour() < 18) return new LeastUsedAllocation();
        return new FirstFreeAllocation();
    }
}
