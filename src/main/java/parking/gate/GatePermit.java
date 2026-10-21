package parking.gate;

import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.ILLEGAL_TRANSITION;

public final class GatePermit {
    public enum Status { NEW, AUTHORIZED, REVOKED }
    private final String id;
    private final String plate;
    private Status status;
    public GatePermit(String id, String plate, Status status) {
        this.id = id; this.plate = plate; this.status = status;
    }
    public void authorize() { transition(Status.NEW, Status.AUTHORIZED); }
    public void revoke() { transition(Status.AUTHORIZED, Status.REVOKED); }
    private void transition(Status from, Status to) {
        if (status != from) throw new ParkingException(ILLEGAL_TRANSITION, "Gate permit cannot move from " + status + " to " + to);
        status = to;
    }
    public String id() { return id; }
    public String plate() { return plate; }
    public Status status() { return status; }
}
