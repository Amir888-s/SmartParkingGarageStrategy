package parking;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import parking.gate.*;
import parking.space.*;
import parking.billing.*;
import parking.store.*;
import static parking.billing.PaymentTerminal.Card.*;
import static org.junit.jupiter.api.Assertions.*;

class SubsystemTest {
    @TempDir Path directory;
    private CsvStore disk() { return new CsvStore(directory); }

    @Test void gateWorksDirectlyWithoutOtherSubsystemsOrFacades() {
        GateController gate = new GateController(new GateRepository(disk()), new LicensePlateReader());
        GatePermit permit = gate.authorize("gate-only", " abc123 ");
        assertEquals("ABC123", permit.plate());
        assertEquals(GatePermit.Status.AUTHORIZED, gate.authorize("gate-only", "ABC123").status());
        assertEquals(1, gate.all().size());
        assertThrows(ParkingException.class, () -> gate.authorize("gate-only", "DIFFERENT"));
        assertThrows(ParkingException.class, () -> gate.authorize("other-id", "ABC123"));
        gate.revoke("gate-only"); gate.revoke("gate-only"); gate.revoke("absent");
        assertEquals(GatePermit.Status.REVOKED, gate.find("gate-only").orElseThrow().status());
        assertThrows(ParkingException.class, () -> gate.authorize("gate-only", "ABC123"));
        gate.setOpen(false); assertThrows(ParkingException.class, () -> gate.authorize("closed", "XYZ001"));
        gate.setOpen(true); assertEquals(GatePermit.Status.AUTHORIZED, gate.authorize("open", "XYZ001").status());
        assertEquals(2, new GateRepository(disk()).all().size());
    }
    @Test void plateValidationRejectsUnsupportedValues() {
        LicensePlateReader reader = new LicensePlateReader();
        assertEquals("123-ABC", reader.read("123-abc"));
        for (String invalid : new String[]{"", "ab", "1234567890123", "A,B", "ABC DEF", "!@#"})
            assertThrows(ParkingException.class, () -> reader.read(invalid));
        assertThrows(ParkingException.class, () -> reader.read(null));
    }
    @Test void gateEntityItselfRejectsIllegalTransitions() {
        GatePermit permit = new GatePermit("g", "ABC", GatePermit.Status.NEW);
        assertThrows(ParkingException.class, permit::revoke);
        permit.authorize(); assertThrows(ParkingException.class, permit::authorize);
        permit.revoke(); assertThrows(ParkingException.class, permit::revoke);
    }
    @Test void spacesWorkDirectlyAndUseActualPersistedOccupiedSpaces() {
        ReservationManager spaces = new ReservationManager(new SpaceRepository(disk()), new SpaceSensorGrid());
        spaces.setBlockedSpaces(Set.of(1));
        assertEquals(2, spaces.reserve("first").space());
        assertEquals(2, spaces.reserve("first").space());
        assertEquals(3, spaces.reserve("second").space());
        assertThrows(ParkingException.class, () -> spaces.reserve("full"));
        spaces.release("first"); spaces.release("first"); spaces.release("absent");
        assertThrows(ParkingException.class, () -> spaces.reserve("first"));
        assertEquals(2, spaces.reserve("reuse").space());
        spaces.setSensorsOnline(false); assertThrows(ParkingException.class, () -> spaces.reserve("offline"));
        spaces.setSensorsOnline(true); spaces.setBlockedSpaces(Set.of());
        assertEquals(1, spaces.reserve("third").space());
        assertEquals(4, spaces.all().size());
        assertTrue(new SpaceRepository(disk()).blockedSpaces().isEmpty());
    }
    @Test void reservationEntityEnforcesTransitionsItself() {
        ParkingReservation reservation = new ParkingReservation("s", 1, ParkingReservation.Status.NEW);
        assertThrows(ParkingException.class, reservation::release);
        reservation.reserve(); assertThrows(ParkingException.class, reservation::reserve);
        reservation.release(); assertThrows(ParkingException.class, reservation::release);
    }
    @Test void billingWorksDirectlyWithPricingAlertsAndNoOtherSubsystems() {
        BillingRepository repository = new BillingRepository(disk());
        PaymentTerminal terminal = new PaymentTerminal(repository, new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK);
        Payment paid = terminal.confirm("paid", 2, APPROVE);
        assertEquals(1000, paid.amount()); assertEquals(Payment.Status.PAID, paid.status());
        assertEquals(Payment.Status.PAID, terminal.confirm("paid", 2, APPROVE).status());
        assertThrows(ParkingException.class, () -> terminal.confirm("paid", 3, APPROVE));
        assertTrue(terminal.overdueIds().isEmpty());
        terminal.reverse("paid"); terminal.reverse("paid"); terminal.reverse("missing");
        assertEquals(Payment.Status.REFUNDED, terminal.find("paid").orElseThrow().status());
        assertThrows(ParkingException.class, () -> terminal.confirm("paid", 2, APPROVE));
        assertThrows(ParkingException.class, () -> terminal.confirm("declined", 1, DECLINE));
        assertThrows(ParkingException.class, () -> terminal.confirm("declined", 1, APPROVE));
        terminal.reverse("declined");
        assertEquals(Payment.Status.DECLINED, terminal.find("declined").orElseThrow().status());
        repository.save(new Payment("new", 500, Instant.parse("2026-10-11T11:00:00Z"), Payment.Status.NEW));
        terminal.reverse("new"); assertEquals(Payment.Status.CANCELLED, terminal.find("new").orElseThrow().status());
        assertThrows(ParkingException.class, () -> terminal.confirm("null-card", 1, null));
        assertEquals(3, terminal.all().size());
    }
    @Test void aPersistedNewPaymentCanFinishAfterRestart() {
        new BillingRepository(disk()).save(new Payment("new", 500, Instant.parse("2026-10-11T11:00:00Z"), Payment.Status.NEW));
        PaymentTerminal terminal = new PaymentTerminal(new BillingRepository(disk()), new PricingPolicy(), new OverstayAlert(), ParkingScenario.CLOCK);
        assertEquals(Payment.Status.PAID, terminal.confirm("new", 1, APPROVE).status());
    }
    @Test void pricingBoundariesAndOverstayBoundaryAreExplicit() {
        PricingPolicy pricing = new PricingPolicy(); assertEquals(500, pricing.amountFor(1)); assertEquals(12000, pricing.amountFor(24));
        assertThrows(ParkingException.class, () -> pricing.amountFor(0)); assertThrows(ParkingException.class, () -> pricing.amountFor(25));
        OverstayAlert alert = new OverstayAlert(); Instant now = ParkingScenario.CLOCK.instant();
        Instant due = alert.dueAt(now, 1); Payment paid = new Payment("p", 500, due, Payment.Status.PAID);
        assertFalse(alert.isOverdue(paid, due)); assertTrue(alert.isOverdue(paid, due.plusSeconds(1)));
        paid.refund(); assertFalse(alert.isOverdue(paid, due.plusSeconds(1)));
    }
    @Test void paymentEntityEnforcesEveryTransitionItself() {
        Payment payment = new Payment("p", 500, ParkingScenario.CLOCK.instant(), Payment.Status.NEW);
        assertThrows(ParkingException.class, payment::refund);
        payment.pay(); assertThrows(ParkingException.class, payment::pay); assertThrows(ParkingException.class, payment::decline);
        payment.refund(); assertThrows(ParkingException.class, payment::refund);
        Payment declined = new Payment("d", 500, ParkingScenario.CLOCK.instant(), Payment.Status.NEW);
        declined.decline(); assertThrows(ParkingException.class, declined::pay);
        Payment cancelled = new Payment("c", 500, ParkingScenario.CLOCK.instant(), Payment.Status.NEW);
        cancelled.cancel(); assertThrows(ParkingException.class, cancelled::cancel);
    }
}
