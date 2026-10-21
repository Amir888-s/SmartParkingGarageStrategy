package parking.gate;

import java.util.*;
import parking.store.RowStore;

public final class GateRepository {
    private final RowStore store;
    public GateRepository(RowStore store) { this.store = store; }
    public List<GatePermit> all() {
        return store.read("gate").stream().map(r -> new GatePermit(r[0], r[1], GatePermit.Status.valueOf(r[2]))).toList();
    }
    public Optional<GatePermit> find(String id) { return all().stream().filter(p -> p.id().equals(id)).findFirst(); }
    public void save(GatePermit permit) {
        List<GatePermit> permits = new ArrayList<>(all());
        permits.removeIf(p -> p.id().equals(permit.id())); permits.add(permit);
        store.write("gate", permits.stream().map(p -> new String[]{p.id(), p.plate(), p.status().name()}).toList());
    }
    public boolean isOpen() {
        return store.read("gate_settings").stream().findFirst().map(r -> Boolean.parseBoolean(r[0])).orElse(true);
    }
    public void setOpen(boolean open) { store.write("gate_settings", Collections.singletonList(new String[]{String.valueOf(open)})); }
}
