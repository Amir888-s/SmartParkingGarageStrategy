package parking.allocation;
public final class FirstFreeAllocation implements AllocationStrategy {
    @Override public int choose(SpaceAvailability spaces) {
        return spaces.freeSpaces().stream().min(Integer::compareTo).orElseThrow();
    }
}
