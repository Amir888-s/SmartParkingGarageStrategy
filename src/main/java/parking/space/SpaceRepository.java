package parking.space;

import java.util.*;
import java.util.stream.Collectors;
import parking.store.RowStore;

public final class SpaceRepository {
    private final RowStore store;
    public SpaceRepository(RowStore store) { this.store = store; }
    public List<ParkingReservation> all() {
        return store.read("space").stream().map(r -> new ParkingReservation(r[0], Integer.parseInt(r[1]), ParkingReservation.Status.valueOf(r[2]))).toList();
    }
    public Optional<ParkingReservation> find(String id) { return all().stream().filter(r -> r.id().equals(id)).findFirst(); }
    public void save(ParkingReservation reservation) {
        List<ParkingReservation> reservations = new ArrayList<>(all());
        reservations.removeIf(r -> r.id().equals(reservation.id())); reservations.add(reservation);
        store.write("space", reservations.stream().map(r -> new String[]{r.id(), String.valueOf(r.space()), r.status().name()}).toList());
    }
    public Set<Integer> blockedSpaces() { return store.read("space_blocks").stream().map(r -> Integer.parseInt(r[0])).collect(Collectors.toSet()); }
    public void setBlockedSpaces(Set<Integer> blocked) { store.write("space_blocks", blocked.stream().sorted().map(n -> new String[]{n.toString()}).toList()); }
    public boolean sensorsOnline() { return store.read("sensor_settings").stream().findFirst().map(r -> Boolean.parseBoolean(r[0])).orElse(true); }
    public void setSensorsOnline(boolean online) { store.write("sensor_settings", Collections.singletonList(new String[]{String.valueOf(online)})); }
}
