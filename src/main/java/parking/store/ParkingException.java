package parking.store;

/** A domain failure the CLI can explain without showing a stack trace. */
public class ParkingException extends RuntimeException {
    public enum Reason { INVALID_INPUT, GATE_CLOSED, DUPLICATE_PLATE, NO_SPACE,
        SENSOR_OFFLINE, PAYMENT_DECLINED, ILLEGAL_TRANSITION, UNKNOWN_WORKFLOW, CONFLICT }
    private final Reason reason;
    public ParkingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
