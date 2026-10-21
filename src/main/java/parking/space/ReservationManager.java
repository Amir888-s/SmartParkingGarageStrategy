package parking.space;

import java.util.*;
import java.util.stream.Collectors;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.CONFLICT;

public final class ReservationManager {
    private final SpaceRepository repository;
    private final SpaceSensorGrid sensors;
    public ReservationManager(SpaceRepository repository, SpaceSensorGrid sensors) { this.repository = repository; this.sensors = sensors; }
    public ParkingReservation reserve(String id) {
        Optional<ParkingReservation> old = repository.find(id);
        if (old.isPresent()) {
            if (old.get().status() != ParkingReservation.Status.RESERVED) throw new ParkingException(CONFLICT, "Reservation has already been released");
            return old.get();
        }
        Set<Integer> occupied = repository.all().stream().filter(r -> r.status() == ParkingReservation.Status.RESERVED).map(ParkingReservation::space).collect(Collectors.toSet());
        Map<Integer, Long> uses = repository.all().stream().collect(Collectors.groupingBy(ParkingReservation::space, Collectors.counting()));
        int space = sensors.firstFree(occupied, repository.blockedSpaces(), repository.sensorsOnline(), uses);
        ParkingReservation reservation = new ParkingReservation(id, space, ParkingReservation.Status.NEW);
        reservation.reserve(); repository.save(reservation); return reservation;
    }
    public void release(String id) {
        repository.find(id).filter(r -> r.status() == ParkingReservation.Status.RESERVED).ifPresent(r -> { r.release(); repository.save(r); });
    }
    public Optional<ParkingReservation> find(String id) { return repository.find(id); }
    public List<ParkingReservation> all() { return repository.all(); }
    public void setBlockedSpaces(Set<Integer> blocked) { repository.setBlockedSpaces(blocked); }
    public void setSensorsOnline(boolean online) { repository.setSensorsOnline(online); }
}
