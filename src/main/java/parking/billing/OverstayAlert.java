package parking.billing;

import java.time.*;

/** Scheduling information is part of the durable payment; no fake SMS is sent. */
public final class OverstayAlert {
    public Instant dueAt(Instant start, int hours) { return start.plus(Duration.ofHours(hours)); }
    public boolean isOverdue(Payment payment, Instant now) {
        return payment.status() == Payment.Status.PAID && now.isAfter(payment.dueAt());
    }
}
