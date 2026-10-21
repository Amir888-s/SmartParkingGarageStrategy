package parking.allocation;
@FunctionalInterface
public interface AllocationStrategy { int choose(SpaceAvailability spaces); }
