package parking.gate;

import java.util.*;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.*;

/** A commits an admission permit, not an irreversible movement of a real car. */
public final class GateController {
    private final GateRepository repository;
    private final LicensePlateReader reader;
    public GateController(GateRepository repository, LicensePlateReader reader) { this.repository = repository; this.reader = reader; }
    public GatePermit authorize(String id, String input) {
        String plate = reader.read(input);
        Optional<GatePermit> old = repository.find(id);
        if (old.isPresent()) {
            GatePermit permit = old.get();
            if (!permit.plate().equals(plate) || permit.status() != GatePermit.Status.AUTHORIZED)
                throw new ParkingException(CONFLICT, "Existing gate operation does not match this request");
            return permit;
        }
        if (!repository.isOpen()) throw new ParkingException(GATE_CLOSED, "Gate is closed for maintenance");
        if (repository.all().stream().anyMatch(p -> p.plate().equals(plate) && p.status() == GatePermit.Status.AUTHORIZED))
            throw new ParkingException(DUPLICATE_PLATE, "This plate already has an active permit");
        GatePermit permit = new GatePermit(id, plate, GatePermit.Status.NEW);
        permit.authorize(); repository.save(permit); return permit;
    }
    public void revoke(String id) {
        repository.find(id).filter(p -> p.status() == GatePermit.Status.AUTHORIZED).ifPresent(p -> { p.revoke(); repository.save(p); });
    }
    public Optional<GatePermit> find(String id) { return repository.find(id); }
    public List<GatePermit> all() { return repository.all(); }
    public void setOpen(boolean open) { repository.setOpen(open); }
}
