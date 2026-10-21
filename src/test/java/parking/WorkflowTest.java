package parking;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import parking.app.GarageApplication;
import parking.store.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;
import parking.facade.*;
import static parking.billing.PaymentTerminal.Card.*;
import static parking.facade.ParkingFacade.Checkpoint.*;
import static parking.facade.Workflow.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    @TempDir Path directory;
    private GarageApplication open() { return new GarageApplication(directory, ParkingScenario.CLOCK); }

    @Test void happyPathPersistsAllThreeStagesAndSurvivesRestart() {
        String id;
        try (GarageApplication app = open()) {
            Workflow result = app.parking().park(" 123abc01 ", 2, APPROVE); id = result.id();
            AdminFacade.Snapshot s = app.admin().inspect(id);
            assertAll(() -> assertEquals(COMPLETED, s.workflow()), () -> assertEquals("AUTHORIZED", s.gate()),
                () -> assertEquals("RESERVED", s.reservation()), () -> assertEquals("PAID", s.payment()),
                () -> assertEquals(1, s.space()), () -> assertEquals(1000, s.amount()),
                () -> assertEquals("2026-10-11T12:00:00Z", s.dueAt()),
                () -> assertEquals(List.of("A committed", "B committed", "C committed"), s.events()));
        }
        try (GarageApplication reopened = open()) {
            assertEquals(COMPLETED, reopened.parking().resume(id).status());
            assertEquals(1, reopened.admin().inspectAll().size());
            assertTrue(reopened.admin().recoverPending().isEmpty());
        }
    }
    @Test void bFailureCompensatesAAloneUsingTheTestingLanguage() {
        try (ParkingScenario garage = ParkingScenario.aGarage(directory)) {
            garage.thatIsFull().parks("BFAIL01", APPROVE).thenOnlyTheGateIsRevoked();
        }
        assertEquals("REVOKED", new GateRepository(new CsvStore(directory)).all().get(0).status().name());
        assertTrue(new SpaceRepository(new CsvStore(directory)).all().isEmpty());
    }
    @Test void cFailureCompensatesBThenAUsingTheTestingLanguage() {
        try (ParkingScenario garage = ParkingScenario.aGarage(directory)) {
            garage.parks("CFAIL01", DECLINE).thenTheSpaceIsReleasedBeforeTheGateIsRevoked();
        }
        assertEquals(ParkingReservation.Status.RELEASED, new SpaceRepository(new CsvStore(directory)).all().get(0).status());
        assertEquals(GatePermit.Status.REVOKED, new GateRepository(new CsvStore(directory)).all().get(0).status());
    }
    @Test void closingAndReopeningMidChainContinuesFromPersistedProgress() {
        String id;
        try (GarageApplication app = open()) {
            Workflow paused = app.parking().begin("PAUSE01", 3, APPROVE, AFTER_A); id = paused.id();
            assertEquals(PENDING_B, paused.status());
            assertEquals("NONE", app.admin().inspect(id).reservation());
        }
        try (GarageApplication reopened = open()) {
            assertEquals(COMPLETED, reopened.parking().resume(id).status());
            assertEquals(1500, reopened.admin().inspect(id).amount());
            assertEquals(1, new GateRepository(new CsvStore(directory)).all().size());
        }
    }
    @Test void reloadAfterBDeclinesAndReversesBothEarlierCommits() {
        String id;
        try (GarageApplication app = open()) {
            Workflow paused = app.parking().begin("PAUSE02", 1, DECLINE, AFTER_B); id = paused.id();
            assertEquals(PENDING_C, paused.status());
        }
        try (GarageApplication reopened = open()) {
            assertEquals(COMPENSATED, reopened.parking().resume(id).status());
            AdminFacade.Snapshot s = reopened.admin().inspect(id);
            assertEquals(List.of("A committed", "B committed", "B released", "A revoked"), s.events());
            assertEquals(COMPENSATED, reopened.parking().resume(id).status());
            assertEquals(COMPENSATED, reopened.admin().cancel(id).status());
        }
    }
    @Test void lateBCommitWithStaleJournalIsReusedRatherThanDuplicated() {
        String id;
        try (GarageApplication app = open()) {
            id = app.parking().begin("STALE01", 1, APPROVE, AFTER_A).id();
        }
        // Crash window: B already committed but workflow still says PENDING_B.
        CsvStore disk = new CsvStore(directory);
        new ReservationManager(new SpaceRepository(disk), new SpaceSensorGrid()).reserve(id);
        try (GarageApplication reopened = open()) { assertEquals(COMPLETED, reopened.parking().resume(id).status()); }
        assertEquals(1, new SpaceRepository(disk).all().size());
        assertEquals(1, new BillingRepository(disk).all().size());
    }
    @Test void lateCCommitWithStaleJournalIsNeverChargedTwice() {
        String id;
        try (GarageApplication app = open()) { id = app.parking().begin("STALE02", 2, APPROVE, AFTER_B).id(); }
        CsvStore disk = new CsvStore(directory);
        new PaymentTerminal(new BillingRepository(disk), new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK).confirm(id, 2, APPROVE);
        try (GarageApplication reopened = open()) { assertEquals(COMPLETED, reopened.parking().resume(id).status()); }
        assertEquals(1, new BillingRepository(disk).all().size());
    }
    @Test void invalidPlateAndClosedGateFailBeforeAnySubsystemCommit() {
        try (GarageApplication app = open()) {
            Workflow bad = app.parking().park("!", 1, APPROVE);
            assertEquals(FAILED, bad.status()); assertTrue(bad.events().isEmpty());
            assertEquals(FAILED, app.parking().resume(bad.id()).status());
            assertEquals(FAILED, app.admin().cancel(bad.id()).status());
            app.admin().setGateOpen(false);
            Workflow closed = app.parking().park("CLOSED1", 1, APPROVE);
            assertEquals(FAILED, closed.status()); assertTrue(closed.failure().contains("maintenance"));
            assertEquals("NONE", app.admin().inspect(closed.id()).gate());
            app.admin().setGateOpen(true);
            assertEquals(COMPLETED, app.parking().park("OPEN001", 1, APPROVE).status());
            assertEquals(FAILED, app.parking().park(null, 1, APPROVE).status());
        }
    }
    @Test void invalidHoursCardCheckpointAndUnknownIdsAreRejected() {
        try (GarageApplication app = open()) {
            assertThrows(ParkingException.class, () -> app.parking().park("VALID01", 0, APPROVE));
            assertThrows(ParkingException.class, () -> app.parking().park("VALID01", 25, APPROVE));
            assertThrows(ParkingException.class, () -> app.parking().park("VALID01", 1, null));
            assertThrows(ParkingException.class, () -> app.parking().begin("VALID01", 1, APPROVE, null));
            assertThrows(ParkingException.class, () -> app.parking().resume("missing"));
            assertThrows(ParkingException.class, () -> app.admin().inspect("missing"));
            assertTrue(app.admin().inspectAll().isEmpty());
        }
    }
    @Test void offlineSensorsCompensateAndSettingsSurviveRestart() {
        try (GarageApplication app = open()) { app.admin().setSensorsOnline(false); }
        try (GarageApplication reopened = open()) {
            Workflow result = reopened.parking().park("OFFLINE1", 1, APPROVE);
            assertEquals(COMPENSATED, result.status()); assertTrue(result.failure().contains("offline"));
            reopened.admin().setSensorsOnline(true);
            assertEquals(COMPLETED, reopened.parking().park("ONLINE01", 1, APPROVE).status());
        }
    }
    @Test void manualCancellationRefundsThenReleasesThenRevokesAndIsIdempotent() {
        try (GarageApplication app = open()) {
            String id = app.parking().park("CANCEL01", 1, APPROVE).id();
            Workflow cancelled = app.admin().cancel(id);
            assertEquals(List.of("A committed", "B committed", "C committed", "C reversed", "B released", "A revoked"), cancelled.events());
            assertEquals("REFUNDED", app.admin().inspect(id).payment());
            assertEquals(cancelled.events(), app.admin().cancel(id).events());
            assertEquals(COMPLETED, app.parking().park("CANCEL01", 1, APPROVE).status()); // plate and space reusable
        }
    }
    @Test void recoverAllFindsPendingAAndBAndC() {
        CsvStore disk = new CsvStore(directory);
        new WorkflowRepository(disk).save(new Workflow("pending-a", "NEW0001", 1, APPROVE, PENDING_A, "", List.of()));
        try (GarageApplication app = open()) {
            app.parking().begin("NEW0002", 1, APPROVE, AFTER_A);
            app.parking().begin("NEW0003", 1, APPROVE, AFTER_B);
            assertEquals(3, app.admin().recoverPending().size());
            assertTrue(app.admin().inspectAll().stream().allMatch(s -> s.workflow() == COMPLETED));
        }
    }
    @Test void compensatingStateResumesAfterAnInterruptedUndo() {
        String id;
        try (GarageApplication app = open()) { id = app.parking().begin("UNDO0001", 1, DECLINE, AFTER_B).id(); }
        CsvStore disk = new CsvStore(directory);
        new WorkflowRepository(disk).save(new Workflow(id, "UNDO0001", 1, DECLINE, COMPENSATING, "Interrupted rollback", List.of("A committed", "B committed")));
        // B was reversed, but the process died before writing the undo event.
        new ReservationManager(new SpaceRepository(disk), new SpaceSensorGrid()).release(id);
        try (GarageApplication reopened = open()) {
            Workflow result = reopened.parking().resume(id);
            assertEquals(COMPENSATED, result.status());
            assertEquals(List.of("A committed", "B committed", "B released", "A revoked"), result.events());
        }
    }
    @Test void overdueAlertsUseSavedDeadlineAndRefundedPaymentsAreIgnored() {
        String id;
        try (GarageApplication app = open()) {
            id = app.parking().park("ALERT001", 1, APPROVE).id(); assertTrue(app.admin().overdueIds().isEmpty());
        }
        try (GarageApplication late = new GarageApplication(directory, Clock.offset(ParkingScenario.CLOCK, Duration.ofHours(2)))) {
            assertEquals(List.of(id), late.admin().overdueIds());
            late.admin().cancel(id); assertTrue(late.admin().overdueIds().isEmpty());
        }
    }
    @Test void secondProcessCannotWriteTheSameStoreWhileTheFirstIsOpen() {
        try (GarageApplication app = open()) { assertThrows(StorageException.class, this::open); }
        try (GarageApplication reopened = open()) { assertTrue(reopened.admin().inspectAll().isEmpty()); }
    }
    @Test void storageFailureDuringRollbackKeepsDurableRetryState() {
        CsvStore disk = new CsvStore(directory);
        class FailingStore implements RowStore {
            boolean failRelease;
            public List<String[]> read(String table) { return disk.read(table); }
            public void write(String table, List<String[]> rows) {
                if (failRelease && table.equals("space") && rows.stream().anyMatch(r -> r[2].equals("RELEASED")))
                    throw new StorageException("Space disk temporarily unavailable", new java.io.IOException());
                disk.write(table, rows);
            }
        }
        FailingStore store = new FailingStore();
        GateController gate = new GateController(new GateRepository(store), new LicensePlateReader());
        ReservationManager spaces = new ReservationManager(new SpaceRepository(store), new SpaceSensorGrid());
        PaymentTerminal billing = new PaymentTerminal(new BillingRepository(store), new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK);
        WorkflowRepository workflows = new WorkflowRepository(store);
        ParkingFacade facade = new ParkingFacade(gate, spaces, billing, workflows);
        String id = facade.begin("DISK0001", 1, DECLINE, AFTER_B).id();
        store.failRelease = true;
        assertThrows(StorageException.class, () -> facade.resume(id));
        assertEquals(COMPENSATING, workflows.get(id).status());
        assertEquals(ParkingReservation.Status.RESERVED, new SpaceRepository(disk).find(id).orElseThrow().status());
        try (GarageApplication reopened = open()) {
            assertEquals(COMPENSATED, reopened.admin().recoverPending().get(0).status());
            assertEquals("REVOKED", reopened.admin().inspect(id).gate());
        }
    }
}
