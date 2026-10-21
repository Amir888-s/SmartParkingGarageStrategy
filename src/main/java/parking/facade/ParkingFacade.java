package parking.facade;

import java.util.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;
import parking.store.*;
import static parking.store.ParkingException.Reason.INVALID_INPUT;
import static parking.facade.Workflow.Status.*;

/** Client-facing A -> B -> C workflow, with recoverable reverse compensation. */
public final class ParkingFacade {
    public enum Checkpoint { NONE, AFTER_A, AFTER_B }
    private final GateController gate;
    private final ReservationManager spaces;
    private final PaymentTerminal billing;
    private final WorkflowRepository workflows;
    public ParkingFacade(GateController gate, ReservationManager spaces, PaymentTerminal billing, WorkflowRepository workflows) {
        this.gate = gate; this.spaces = spaces; this.billing = billing; this.workflows = workflows;
    }
    public Workflow park(String plate, int hours, PaymentTerminal.Card card) { return begin(plate, hours, card, Checkpoint.NONE); }
    public Workflow begin(String plate, int hours, PaymentTerminal.Card card, Checkpoint checkpoint) {
        if (hours < 1 || hours > 24 || card == null || checkpoint == null)
            throw new ParkingException(INVALID_INPUT, "Use 1-24 hours and a payment decision/checkpoint");
        Workflow workflow = new Workflow(UUID.randomUUID().toString(), plate == null ? "" : plate, hours, card, PENDING_A, "", List.of());
        workflows.save(workflow); // Intent is on disk BEFORE the first side effect.
        return advance(workflow, checkpoint);
    }
    public Workflow resume(String id) {
        Workflow workflow = workflows.get(id);
        if (workflow.status() == COMPENSATING) return compensate(id, workflow.failure());
        if (workflow.status() == COMPLETED || workflow.status() == COMPENSATED || workflow.status() == FAILED) return workflow;
        return advance(workflow, Checkpoint.NONE);
    }
    private Workflow advance(Workflow workflow, Checkpoint checkpoint) {
        try {
            // Same ID on every stage. Services reload their own disk state and are idempotent.
            gate.authorize(workflow.id(), workflow.plate());
            workflow.record("A committed"); workflow.status(PENDING_B); workflows.save(workflow);
            if (checkpoint == Checkpoint.AFTER_A) return workflow;
            spaces.reserve(workflow.id());
            workflow.record("B committed"); workflow.status(PENDING_C); workflows.save(workflow);
            if (checkpoint == Checkpoint.AFTER_B) return workflow;
            billing.confirm(workflow.id(), workflow.hours(), workflow.card());
            workflow.record("C committed"); workflow.status(COMPLETED); workflows.save(workflow);
            return workflow;
        } catch (ParkingException | StorageException failure) {
            return compensate(workflow.id(), failure.getMessage());
        }
    }
    /** Package access: the admin facade can request rollback, clients use park/resume. */
    Workflow compensate(String id, String reason) {
        Workflow workflow = workflows.get(id);
        if (workflow.status() == COMPENSATED || workflow.status() == FAILED) return workflow;
        workflow.failure(reason); workflow.status(COMPENSATING); workflows.save(workflow);
        // Persisted entities, not the workflow status or an in-memory committed flag,
        // decide which effects exist. A failed rollback leaves COMPENSATING for retry.
        Optional<Payment> payment = billing.find(id);
        if (payment.isPresent() && (payment.get().status() == Payment.Status.PAID || payment.get().status() == Payment.Status.NEW)) {
            billing.reverse(id); workflow.record("C reversed"); workflows.save(workflow);
        } else if (payment.isPresent() && (payment.get().status() == Payment.Status.REFUNDED || payment.get().status() == Payment.Status.CANCELLED)) {
            workflow.record("C reversed"); workflows.save(workflow);
        }
        Optional<ParkingReservation> reservation = spaces.find(id);
        if (reservation.isPresent()) {
            spaces.release(id); workflow.record("B released"); workflows.save(workflow);
        }
        Optional<GatePermit> permit = gate.find(id);
        if (permit.isPresent()) {
            gate.revoke(id); workflow.record("A revoked"); workflows.save(workflow);
        }
        workflow.status(permit.isPresent() || reservation.isPresent() || payment.isPresent() ? COMPENSATED : FAILED);
        workflows.save(workflow);
        return workflow;
    }
}
