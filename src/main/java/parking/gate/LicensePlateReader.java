package parking.gate;

import java.util.Locale;
import parking.store.ParkingException;
import static parking.store.ParkingException.Reason.INVALID_INPUT;

/** A deliberately simple demo format, not an official national plate validator. */
public final class LicensePlateReader {
    public String read(String input) {
        if (input == null) throw new ParkingException(INVALID_INPUT, "License plate is required");
        String plate = input.trim().toUpperCase(Locale.ROOT);
        if (!plate.matches("[A-Z0-9-]{3,12}"))
            throw new ParkingException(INVALID_INPUT, "Plate must have 3-12 letters, digits or hyphens");
        return plate;
    }
}
