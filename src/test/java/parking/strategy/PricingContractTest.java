package parking.strategy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import parking.billing.PricingPolicy;
import parking.store.ParkingException;
import static org.junit.jupiter.api.Assertions.*;

/** Identical characterization tests are run before refactoring and afterwards. */
class PricingContractTest {
    @ParameterizedTest
    @CsvSource({"hourly,1,500", "hourly,8,4000", "hourly,24,12000",
        "capped,1,500", "capped,6,3000", "capped,8,3000", "capped,24,3000",
        "demand,1,500", "demand,2,1000", "demand,8,5200", "demand,24,16400"})
    void tariffsKeepTheirObservableResults(String mode, int hours, int expected) {
        assertEquals(expected, new PricingPolicy(mode, () -> 0.8).amountFor(hours));
    }
    @ParameterizedTest @ValueSource(strings={"hourly", "capped", "demand"})
    void everyTariffIsNonnegativeMonotonicAndRejectsInvalidHours(String mode) {
        PricingPolicy pricing = new PricingPolicy(mode, () -> 0.8);
        int previous = 0;
        for (int hours=1; hours<=24; hours++) {
            int amount = pricing.amountFor(hours);
            assertTrue(amount >= 0); assertTrue(amount >= previous); previous = amount;
        }
        assertThrows(ParkingException.class, () -> pricing.amountFor(0));
        assertThrows(ParkingException.class, () -> pricing.amountFor(25));
    }
    @Test void defaultTariffRemainsHourly() { assertEquals(1000, new PricingPolicy().amountFor(2)); }
    @Test void externalSensorChangesDemandPrice() {
        assertEquals(4000, new PricingPolicy("demand", () -> 0.0).amountFor(8));
        assertEquals(5500, new PricingPolicy("demand", () -> 1.0).amountFor(8));
    }
}
