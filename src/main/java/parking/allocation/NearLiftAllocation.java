package parking.allocation;
import java.util.Comparator;
/** Bay 2 is closest to the lift; equal distances prefer the smaller bay number. */
public final class NearLiftAllocation implements AllocationStrategy {
    @Override public int choose(SpaceAvailability spaces) {
        return spaces.freeSpaces().stream().min(Comparator.<Integer>comparingInt(n -> Math.abs(n - 2))
                .thenComparingInt(Integer::intValue)).orElseThrow();
    }
}
