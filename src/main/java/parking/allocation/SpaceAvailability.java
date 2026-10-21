package parking.allocation;
import java.util.*;
import java.util.stream.IntStream;
import parking.space.SpaceSensorGrid;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.*;
/** Immutable input snapshot shared by every allocation algorithm. */
public record SpaceAvailability(List<Integer> freeSpaces, Map<Integer, Long> previousUses) {
    public SpaceAvailability {
        freeSpaces = List.copyOf(freeSpaces); previousUses = Map.copyOf(previousUses);
        if (freeSpaces.isEmpty()) throw new ParkingException(NO_SPACE, "No space available: sensor grid reports a full garage");
        if (freeSpaces.stream().anyMatch(n -> n < 1 || n > SpaceSensorGrid.CAPACITY)
                || previousUses.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("Invalid bay or negative use count");
    }
    public static SpaceAvailability from(Set<Integer> occupied, Set<Integer> blocked, boolean online, Map<Integer, Long> uses) {
        if (!online) throw new ParkingException(SENSOR_OFFLINE, "Space sensors are offline");
        return new SpaceAvailability(IntStream.rangeClosed(1, SpaceSensorGrid.CAPACITY)
                .filter(n -> !occupied.contains(n) && !blocked.contains(n)).boxed().toList(), uses);
    }
}
