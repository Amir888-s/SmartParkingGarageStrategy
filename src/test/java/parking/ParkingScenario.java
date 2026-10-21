package parking;

import java.nio.file.Path;
import java.time.*;
import parking.app.GarageApplication;
import parking.billing.PaymentTerminal.Card;
import parking.facade.*;
import static org.junit.jupiter.api.Assertions.*;

/** Small testing language: aGarage ... parks ... then... */
final class ParkingScenario implements AutoCloseable {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC);
    private final GarageApplication app;
    private Workflow result;
    private ParkingScenario(Path directory) { app = new GarageApplication(directory, CLOCK); }
    static ParkingScenario aGarage(Path directory) { return new ParkingScenario(directory); }
    ParkingScenario thatIsFull() { app.admin().simulateFullGarage(true); return this; }
    ParkingScenario parks(String plate, Card card) { result = app.parking().park(plate, 2, card); return this; }
    AdminFacade.Snapshot snapshot() { return app.admin().inspect(result.id()); }
    void thenOnlyTheGateIsRevoked() {
        AdminFacade.Snapshot s = snapshot();
        assertAll(() -> assertEquals(Workflow.Status.COMPENSATED, s.workflow()), () -> assertEquals("REVOKED", s.gate()),
            () -> assertEquals("NONE", s.reservation()), () -> assertEquals("NONE", s.payment()),
            () -> assertEquals(java.util.List.of("A committed", "A revoked"), s.events()));
    }
    void thenTheSpaceIsReleasedBeforeTheGateIsRevoked() {
        AdminFacade.Snapshot s = snapshot();
        assertAll(() -> assertEquals(Workflow.Status.COMPENSATED, s.workflow()), () -> assertEquals("REVOKED", s.gate()),
            () -> assertEquals("RELEASED", s.reservation()), () -> assertEquals("DECLINED", s.payment()),
            () -> assertEquals(java.util.List.of("A committed", "B committed", "B released", "A revoked"), s.events()));
    }
    @Override public void close() { app.close(); }
}
