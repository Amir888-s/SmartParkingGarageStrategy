package parking.facade;

import java.util.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;

/** Narrow operational interface: inspection, sensor controls and manual rollback. */
public final class AdminFacade {
    public record Snapshot(String id, Workflow.Status workflow, String plate, String gate,
            String reservation, int space, String payment, int amount, String dueAt, String failure, List<String> events) {}
    private final GateController gate;
    private final ReservationManager spaces;
    private final PaymentTerminal billing;
    private final WorkflowRepository workflows;
    private final ParkingFacade parking;
    public AdminFacade(GateController gate, ReservationManager spaces, PaymentTerminal billing, WorkflowRepository workflows, ParkingFacade parking) {
        this.gate = gate; this.spaces = spaces; this.billing = billing; this.workflows = workflows; this.parking = parking;
    }
    public Snapshot inspect(String id) {
        Workflow workflow = workflows.get(id);
        Optional<GatePermit> permit = gate.find(id);
        Optional<ParkingReservation> reservation = spaces.find(id);
        Optional<Payment> payment = billing.find(id);
        return new Snapshot(id, workflow.status(), workflow.plate(), permit.map(p -> p.status().name()).orElse("NONE"),
            reservation.map(r -> r.status().name()).orElse("NONE"), reservation.map(ParkingReservation::space).orElse(0),
            payment.map(p -> p.status().name()).orElse("NONE"), payment.map(Payment::amount).orElse(0),
            payment.map(p -> p.dueAt().toString()).orElse(""), workflow.failure(), workflow.events());
    }
    public List<Snapshot> inspectAll() { return workflows.all().stream().map(w -> inspect(w.id())).toList(); }
    public Workflow cancel(String id) { return parking.compensate(id, "Administrator cancelled the parking operation"); }
    public List<Workflow> recoverPending() {
        return workflows.all().stream().filter(w -> w.status() == Workflow.Status.PENDING_A || w.status() == Workflow.Status.PENDING_B ||
            w.status() == Workflow.Status.PENDING_C || w.status() == Workflow.Status.COMPENSATING).map(w -> parking.resume(w.id())).toList();
    }
    public void simulateFullGarage(boolean full) { spaces.setBlockedSpaces(full ? Set.of(1, 2, 3) : Set.of()); }
    public void setSensorsOnline(boolean online) { spaces.setSensorsOnline(online); }
    public void setGateOpen(boolean open) { gate.setOpen(open); }
    public List<String> overdueIds() { return billing.overdueIds(); }
}
