package parking.billing;

import java.time.Instant;
import java.util.*;
import parking.store.RowStore;

public final class BillingRepository {
    private final RowStore store;
    public BillingRepository(RowStore store) { this.store = store; }
    public List<Payment> all() {
        return store.read("billing").stream().map(r -> new Payment(r[0], Integer.parseInt(r[1]), Instant.parse(r[2]), Payment.Status.valueOf(r[3]))).toList();
    }
    public Optional<Payment> find(String id) { return all().stream().filter(p -> p.id().equals(id)).findFirst(); }
    public void save(Payment payment) {
        List<Payment> payments = new ArrayList<>(all());
        payments.removeIf(p -> p.id().equals(payment.id())); payments.add(payment);
        store.write("billing", payments.stream().map(p -> new String[]{p.id(), String.valueOf(p.amount()), p.dueAt().toString(), p.status().name()}).toList());
    }
    public Optional<PriceQuote> findQuote(String id) {
        return store.read("price_quotes").stream().filter(r -> r[0].equals(id))
            .map(r -> new PriceQuote(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]))).findFirst();
    }
    public void saveQuote(PriceQuote quote) {
        List<String[]> rows = new ArrayList<>(store.read("price_quotes"));
        rows.removeIf(r -> r[0].equals(quote.id()));
        rows.add(new String[]{quote.id(), String.valueOf(quote.hours()), String.valueOf(quote.amount())});
        store.write("price_quotes", rows);
    }
}
