package parking.facade;

import java.util.*;
import parking.billing.PaymentTerminal.Card;

/** Durable coordination record. Domain entities still enforce their own rules. */
public final class Workflow {
    public enum Status { PENDING_A, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED, FAILED }
    private final String id;
    private final String plate;
    private final int hours;
    private final Card card;
    private Status status;
    private String failure;
    private final List<String> events;
    public Workflow(String id, String plate, int hours, Card card, Status status, String failure, List<String> events) {
        this.id = id; this.plate = plate; this.hours = hours; this.card = card;
        this.status = status; this.failure = failure; this.events = new ArrayList<>(events);
    }
    void status(Status value) { status = value; }
    void failure(String value) { failure = value; }
    void record(String event) { if (!events.contains(event)) events.add(event); }
    public String id() { return id; }
    public String plate() { return plate; }
    public int hours() { return hours; }
    public Card card() { return card; }
    public Status status() { return status; }
    public String failure() { return failure; }
    public List<String> events() { return List.copyOf(events); }
}
