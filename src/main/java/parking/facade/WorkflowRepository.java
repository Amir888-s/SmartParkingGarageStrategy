package parking.facade;

import java.nio.charset.StandardCharsets;
import java.util.*;
import parking.billing.PaymentTerminal.Card;
import parking.store.RowStore;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.UNKNOWN_WORKFLOW;

public final class WorkflowRepository {
    private final RowStore store;
    public WorkflowRepository(RowStore store) { this.store = store; }
    public List<Workflow> all() {
        return store.read("workflow").stream().map(r -> new Workflow(r[0], decode(r[1]), Integer.parseInt(r[2]),
            Card.valueOf(r[3]), Workflow.Status.valueOf(r[4]), decode(r[5]),
            decode(r[6]).isEmpty() ? List.of() : List.of(decode(r[6]).split(";")))).toList();
    }
    public Workflow get(String id) {
        return all().stream().filter(w -> w.id().equals(id)).findFirst().orElseThrow(() -> new ParkingException(UNKNOWN_WORKFLOW, "Unknown workflow " + id));
    }
    public void save(Workflow workflow) {
        List<Workflow> workflows = new ArrayList<>(all());
        workflows.removeIf(w -> w.id().equals(workflow.id())); workflows.add(workflow);
        store.write("workflow", workflows.stream().map(w -> new String[]{w.id(), encode(w.plate()), String.valueOf(w.hours()),
            w.card().name(), w.status().name(), encode(w.failure()), encode(String.join(";", w.events()))}).toList());
    }
    private static String encode(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
}
