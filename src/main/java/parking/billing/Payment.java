package parking.billing;

import java.time.Instant;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.ILLEGAL_TRANSITION;

public final class Payment {
    public enum Status { NEW, PAID, DECLINED, REFUNDED, CANCELLED }
    private final String id;
    private final int amount;
    private final Instant dueAt;
    private Status status;
    public Payment(String id, int amount, Instant dueAt, Status status) { this.id = id; this.amount = amount; this.dueAt = dueAt; this.status = status; }
    public void pay() { transition(Status.NEW, Status.PAID); }
    public void decline() { transition(Status.NEW, Status.DECLINED); }
    public void refund() { transition(Status.PAID, Status.REFUNDED); }
    public void cancel() { transition(Status.NEW, Status.CANCELLED); }
    private void transition(Status from, Status to) {
        if (status != from) throw new ParkingException(ILLEGAL_TRANSITION, "Payment cannot move from " + status + " to " + to);
        status = to;
    }
    public String id() { return id; }
    public int amount() { return amount; }
    public Instant dueAt() { return dueAt; }
    public Status status() { return status; }
}
