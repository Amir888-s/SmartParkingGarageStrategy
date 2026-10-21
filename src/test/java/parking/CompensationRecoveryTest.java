package parking;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import parking.app.GarageApplication;
import parking.billing.*;
import parking.facade.*;
import parking.gate.*;
import parking.space.*;
import parking.store.*;
import static parking.billing.PaymentTerminal.Card.*;
import static parking.facade.ParkingFacade.Checkpoint.*;
import static parking.facade.Workflow.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class CompensationRecoveryTest {
    @TempDir Path directory;
    @Test void administratorCanCancelAChainPausedAfterAWithoutTouchingBOrC() {
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) {
            String id = app.parking().begin("PARTIAL1", 1, APPROVE, AFTER_A).id();
            assertEquals(COMPENSATED, app.admin().cancel(id).status());
            assertEquals(List.of("A committed", "A revoked"), app.admin().inspect(id).events());
            assertEquals("NONE", app.admin().inspect(id).payment());
        }
    }
    @Test void recoveryRecognizesAPaymentThatWasAlreadyRefundedBeforeTheUndoJournalUpdated() {
        String id;
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) { id = app.parking().park("REFUND01", 1, APPROVE).id(); }
        CsvStore disk = new CsvStore(directory);
        new WorkflowRepository(disk).save(new Workflow(id, "REFUND01", 1, APPROVE, COMPENSATING, "Interrupted cancellation", List.of("A committed", "B committed", "C committed")));
        new PaymentTerminal(new BillingRepository(disk), new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK).reverse(id);
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) {
            assertEquals(COMPENSATED, app.parking().resume(id).status());
            assertEquals(List.of("A committed", "B committed", "C committed", "C reversed", "B released", "A revoked"), app.admin().inspect(id).events());
        }
    }
    @Test void recoveryCancelsAnUnpaidNewPaymentAlongWithBAndA() {
        String id;
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) { id = app.parking().begin("NEWPAY01", 1, APPROVE, AFTER_B).id(); }
        CsvStore disk = new CsvStore(directory);
        new BillingRepository(disk).save(new Payment(id, 500, ParkingScenario.CLOCK.instant(), Payment.Status.NEW));
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) {
            assertEquals(COMPENSATED, app.admin().cancel(id).status());
            assertEquals("CANCELLED", app.admin().inspect(id).payment());
        }
    }
    @Test void failureWritingTheFinalJournalStillFindsAndRefundsTheRealPersistedPayment() {
        CsvStore disk = new CsvStore(directory);
        RowStore failingJournal = new RowStore() {
            boolean failed;
            public List<String[]> read(String table) { return disk.read(table); }
            public void write(String table, List<String[]> rows) {
                if (!failed && table.equals("workflow") && rows.stream().anyMatch(r -> r[4].equals("COMPLETED"))) {
                    failed = true; throw new StorageException("Final journal write failed", new java.io.IOException());
                }
                disk.write(table, rows);
            }
        };
        GateController gate = new GateController(new GateRepository(failingJournal), new LicensePlateReader());
        ReservationManager spaces = new ReservationManager(new SpaceRepository(failingJournal), new SpaceSensorGrid());
        PaymentTerminal billing = new PaymentTerminal(new BillingRepository(failingJournal), new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK);
        ParkingFacade facade = new ParkingFacade(gate, spaces, billing, new WorkflowRepository(failingJournal));
        Workflow result = facade.park("JOURNAL1", 1, APPROVE);
        assertEquals(COMPENSATED, result.status());
        assertEquals(Payment.Status.REFUNDED, new BillingRepository(disk).find(result.id()).orElseThrow().status());
        assertEquals(List.of("A committed", "B committed", "C reversed", "B released", "A revoked"), result.events());
    }
    @Test void originalPlateCannotBeRegisteredTwiceUntilItsFirstChainIsCancelled() {
        try (GarageApplication app = new GarageApplication(directory, ParkingScenario.CLOCK)) {
            app.parking().park("DUP001", 1, APPROVE);
            Workflow second = app.parking().park("DUP001", 1, APPROVE);
            assertEquals(FAILED, second.status()); assertTrue(second.failure().contains("active permit"));
            assertEquals(2, app.admin().inspectAll().size());
            assertEquals("NONE", app.admin().inspect(second.id()).gate());
        }
    }
}
