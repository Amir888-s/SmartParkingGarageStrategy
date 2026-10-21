package parking.pricing;
import parking.store.ParkingException;
public final class DurationRules {
    private DurationRules() {}
    public static int requireValid(int hours) {
        if (hours < 1 || hours > 24)
            throw new ParkingException(ParkingException.Reason.INVALID_INPUT, "Parking duration must be 1-24 whole hours");
        return hours;
    }
}
