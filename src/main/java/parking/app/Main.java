package parking.app;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;
import parking.billing.PaymentTerminal.Card;
import parking.facade.*;
import parking.store.*;

public final class Main {
    public static void main(String[] args) {
        Path directory = Path.of(args.length == 0 ? "data" : args[0]);
        try (GarageApplication app = new GarageApplication(directory, Clock.systemUTC()); Scanner input = new Scanner(System.in)) {
            System.out.println("Smart Parking Garage | persisted store: " + directory.toAbsolutePath());
            System.out.println("Pending work is retained. Inspect with 2, recover explicitly with 9.");
            while (true) {
                System.out.println("\n1 Park  2 Inspect all  3 Full garage (B failure)  4 Declined payment (C failure)");
                System.out.println("5 Pause after A  6 Pause after B  7 Resume ID  8 Cancel ID  9 Recover pending");
                System.out.println("10 Close/open gate  11 Sensors online/offline  12 Overstay alerts  0 Exit");
                System.out.println("13 Strategy demonstration  14 Configure strategies");
                System.out.print("Choice: ");
                if (!input.hasNextLine()) break;
                String choice = input.nextLine().trim();
                if (choice.equals("0")) break;
                try {
                    switch (choice) {
                        case "1", "3", "4", "5", "6" -> {
                            System.out.print("License plate (example 123ABC01): "); String plate = input.nextLine();
                            System.out.print("Whole hours (1-24): "); int hours = Integer.parseInt(input.nextLine());
                            app.prepareStrategies(hours);
                            Card card = choice.equals("4") ? Card.DECLINE : Card.APPROVE;
                            ParkingFacade.Checkpoint stop = choice.equals("5") ? ParkingFacade.Checkpoint.AFTER_A :
                                choice.equals("6") ? ParkingFacade.Checkpoint.AFTER_B : ParkingFacade.Checkpoint.NONE;
                            if (choice.equals("3")) app.admin().simulateFullGarage(true);
                            try {
                                Workflow workflow = app.parking().begin(plate, hours, card, stop);
                                show(app.admin().inspect(workflow.id()));
                            } finally { if (choice.equals("3")) app.admin().simulateFullGarage(false); }
                        }
                        case "2" -> app.admin().inspectAll().forEach(Main::show);
                        case "7", "8" -> {
                            System.out.print("Workflow ID: "); String id = input.nextLine().trim();
                            Workflow workflow = choice.equals("7") ? app.parking().resume(id) : app.admin().cancel(id);
                            show(app.admin().inspect(workflow.id()));
                        }
                        case "9" -> app.admin().recoverPending().forEach(w -> show(app.admin().inspect(w.id())));
                        case "10" -> { System.out.print("Gate open? true/false: "); app.admin().setGateOpen(booleanInput(input)); }
                        case "11" -> { System.out.print("Sensors online? true/false: "); app.admin().setSensorsOnline(booleanInput(input)); }
                        case "12" -> System.out.println("Overdue workflow IDs: " + app.admin().overdueIds());
                        case "13" -> StrategyDemo.run();
                        case "14" -> {
                            System.out.print("Pricing: hourly / capped / demand / auto: "); String price = input.nextLine().trim();
                            System.out.print("Allocation: first / lift / balanced / auto: "); String allocation = input.nextLine().trim();
                            System.out.print("Simulated occupancy fraction (0-1): "); double occupancy = Double.parseDouble(input.nextLine());
                            System.out.print("Prefer a bay near the lift? true/false: "); boolean lift = booleanInput(input);
                            app.configureStrategies(price, allocation, occupancy, lift);
                            System.out.println("Configured " + app.strategyModes() + ". Applied to each new park request.");
                        }
                        default -> System.out.println("Unknown menu option");
                    }
                } catch (NumberFormatException e) { System.out.println("Input error: enter a whole number"); }
                catch (IllegalArgumentException e) { System.out.println("Input error: " + e.getMessage()); }
                catch (ParkingException e) { System.out.println("Domain error [" + e.reason() + "]: " + e.getMessage()); }
                catch (StorageException e) { System.out.println("Storage error: " + e.getMessage() + ". Repair access, restart and recover pending work."); }
            }
        } catch (StorageException e) { System.out.println("Cannot start: " + e.getMessage()); }
    }
    private static boolean booleanInput(Scanner input) {
        String value = input.nextLine().trim();
        if (!value.equals("true") && !value.equals("false"))
            throw new ParkingException(ParkingException.Reason.INVALID_INPUT, "Enter true or false");
        return Boolean.parseBoolean(value);
    }
    private static void show(AdminFacade.Snapshot snapshot) {
        System.out.println("ID: " + snapshot.id() + " | workflow=" + snapshot.workflow() + " | plate=" + snapshot.plate());
        System.out.println("A gate=" + snapshot.gate() + " | B reservation=" + snapshot.reservation() + " space=" + snapshot.space() +
            " | C payment=" + snapshot.payment() + " amount=" + snapshot.amount() + " KZT");
        if (!snapshot.dueAt().isEmpty()) System.out.println("Overstay due at (UTC): " + snapshot.dueAt());
        if (!snapshot.failure().isEmpty()) System.out.println("Reason: " + snapshot.failure());
        System.out.println("Persisted events: " + String.join(" -> ", snapshot.events()));
    }
}
