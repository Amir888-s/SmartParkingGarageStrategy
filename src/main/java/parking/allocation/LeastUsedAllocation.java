package parking.allocation;
import java.util.Comparator;
public final class LeastUsedAllocation implements AllocationStrategy {
    @Override public int choose(SpaceAvailability spaces) {
        return spaces.freeSpaces().stream().min(Comparator.<Integer>comparingLong(n -> spaces.previousUses().getOrDefault(n, 0L))
                .thenComparingInt(Integer::intValue)).orElseThrow();
    }
}
