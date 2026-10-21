package parking.space;

import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.ILLEGAL_TRANSITION;

public final class ParkingReservation {
    public enum Status { NEW, RESERVED, RELEASED }
    private final String id;
    private final int space;
    private Status status;
    public ParkingReservation(String id, int space, Status status) { this.id = id; this.space = space; this.status = status; }
    public void reserve() { transition(Status.NEW, Status.RESERVED); }
    public void release() { transition(Status.RESERVED, Status.RELEASED); }
    private void transition(Status from, Status to) {
        if (status != from) throw new ParkingException(ILLEGAL_TRANSITION, "Reservation cannot move from " + status + " to " + to);
        status = to;
    }
    public String id() { return id; }
    public int space() { return space; }
    public Status status() { return status; }
}
