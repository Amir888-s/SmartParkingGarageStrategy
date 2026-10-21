package parking.billing;
/** Persisted tariff result pins retries to the first price decision. */
public record PriceQuote(String id, int hours, int amount) {}
