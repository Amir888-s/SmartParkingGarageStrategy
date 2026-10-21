package parking.space;
import java.util.*;
import parking.allocation.*;
/** Allocation context; existing firstFree API remains compatible. */
public final class SpaceSensorGrid {
    public static final int CAPACITY = 3;
    private AllocationStrategy strategy;
    public SpaceSensorGrid() { this(new FirstFreeAllocation()); }
    public SpaceSensorGrid(AllocationStrategy strategy) { setStrategy(strategy); }
    public void setStrategy(AllocationStrategy strategy) { this.strategy = Objects.requireNonNull(strategy); }
    public int choose(SpaceAvailability spaces) { return strategy.choose(spaces); }
    public int firstFree(Set<Integer> occupied, Set<Integer> blocked, boolean online) {
        return firstFree(occupied, blocked, online, Map.of());
    }
    public int firstFree(Set<Integer> occupied, Set<Integer> blocked, boolean online, Map<Integer, Long> uses) {
        return choose(SpaceAvailability.from(occupied, blocked, online, uses));
    }
}
