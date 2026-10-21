# Smart Parking Garage

Assignment 4: Facade over a three-stage chain with persistence and a live demo.
Author: Amir Kabdilmalik, Astana IT University.

The program coordinates an admission permit, a parking-space reservation, and a prepaid parking payment with an overstay deadline. It saves every subsystem and the workflow to disk. If a later stage fails, it reads persisted records and reverses the effects in reverse order. This is a local teaching application: gate sensors and card decisions are simulated, not connected to hardware or a bank.

## Run

You need Java 17 or newer. Extract the ZIP before running.

Windows: double-click `run.cmd`, or in the extracted project folder run:

```powershell
java -jar dist/smart-parking-garage-1.0.0.jar
```

Linux/macOS: `sh run.sh`.

To **build from source and run in one command**, install a JDK 17+ and Maven, open a terminal in the project folder, and run:

```text
mvn package exec:java
```

To use IntelliJ: open the folder containing `pom.xml`, choose JDK 17, let Maven import dependencies, and run `parking.app.Main.main`. If `java` or `mvn` is not recognized, install/configure it or use the IDE's JDK and bundled Maven. The supplied JAR does not need Maven. After changing source, rebuild it with `build.cmd` or `sh build.sh` before running the JAR again; IntelliJ can run the changed source directly.

## Three independent subsystems

| Subsystem | Classes | Entity statuses | Own business rules | Own gateway |
|---|---|---|---|---|
| A admission | GatePermit, LicensePlateReader, GateRepository, GateController | NEW, AUTHORIZED, REVOKED | Plate format, gate availability, duplicate plate | GateRepository |
| B parking space | ParkingReservation, SpaceSensorGrid, SpaceRepository, ReservationManager | NEW, RESERVED, RELEASED | Select first unoccupied and unblocked space, reject offline sensors | SpaceRepository |
| C finalization | Payment, PricingPolicy, OverstayAlert, BillingRepository, PaymentTerminal | NEW, PAID, DECLINED, REFUNDED, CANCELLED | 500 KZT/hour, 1-24 hours, card decision, overstay deadline | BillingRepository |

There are **13 subsystem classes: 4 + 4 + 5**. Each entity enforces its own transitions. No subsystem imports either of the other subsystems. The shared store package is infrastructure, not another subsystem. `GarageApplication` only constructs and injects dependencies; all cross-subsystem business coordination is in the facades.

The permit is a reversible **admission authorization**. The program does not claim that revoking a permit moves an already-entered physical car backwards. A successful operation models prepaid parking. A paid record's saved deadline supports overstay inspection; refunds disable alerts because only PAID records can be overdue. Completed reservations remain active until administrative cancellation in this assignment's limited lifecycle. A normal car exit with usage-based billing would be a separate feature.

## Facades and persisted workflow

`ParkingFacade.park(plate, hours, card)` provides the common A -> B -> C operation. `begin` can intentionally stop after A or B for the live restart demonstration. `resume(id)` continues an incomplete workflow or retries interrupted compensation.

`AdminFacade` provides inspection, manual cancellation, recovery of pending work, persisted gate/sensor controls and overstay inspection. It is a narrower operational API rather than a second copy of the customer workflow.

Workflow states are PENDING_A, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED and FAILED. A UUID is generated and the PENDING_A intent is saved **before** A commits. The same ID connects all records and makes stage calls idempotent.

| Situation | A final state | B final state | C final state | Workflow |
|---|---|---|---|---|
| Success | AUTHORIZED | RESERVED | PAID | COMPLETED |
| Full garage at B | REVOKED | absent | absent | COMPENSATED |
| Declined card at C | REVOKED | RELEASED | DECLINED | COMPENSATED |
| Invalid plate or closed gate at A | absent | absent | absent | FAILED |
| Cancel completed operation | REVOKED | RELEASED | REFUNDED | COMPENSATED |

B failure: read A from disk, revoke A only. C failure: persist COMPENSATING, release B, then revoke A. A declined payment never charged money, so it is retained as DECLINED rather than refunded. Manual cancellation of a completed workflow additionally refunds C first.

Recovery does not trust a journal alone. There is a crash window between a subsystem save and the workflow save. Every stage reloads its record by the workflow ID and reuses an already committed result. Compensation also checks actual persisted A/B/C entities, including the case where payment succeeded but the final workflow save failed. If rollback fails, the persisted workflow stays COMPENSATING; after restoring storage access, `resume` or `recoverPending` retries it. No rollback failure is silently labelled COMPENSATED.

## Storage and reset

Default folder: `data/`, relative to the current working directory. `run.cmd` and `run.sh` select the project folder as that working directory. Specify a different folder with:

```text
java -jar dist/smart-parking-garage-1.0.0.jar demo-data
```

Files: `gate.csv`, `space.csv`, `billing.csv`, `workflow.csv`, `gate_settings.csv`, `space_blocks.csv`, and `sensor_settings.csv`. Each repository reads its own table through `RowStore`; it never routes a save through a facade. Text fields that can contain punctuation, including error messages and raw workflow input, use Base64 in workflow.csv so they do not break CSV parsing. Base64 is encoding, not encryption.

`CsvStore` writes a temporary file, forces its contents to disk, then atomically replaces the table. There is no transaction across different CSV files; compensation is the deliberate consistency mechanism. A file lock prevents two `GarageApplication` processes writing the same folder at once. Standalone subsystems are for isolated tests, not concurrent writers into an active application's directory.

This design targets an ordinary application stop/restart on a local filesystem that supports atomic rename. It does not claim protection against arbitrary power loss, externally edited/corrupted CSV files, network filesystems, or distributed payment-side effects. Real payment integration needs provider idempotency keys and a transaction/recovery design appropriate to that provider.

To reset **only the demo data**, first exit the application, then delete the `data` folder:

```powershell
Remove-Item -Recurse -Force .\data
```

```sh
rm -rf ./data
```

Use the path you actually supplied if running a custom store. These commands delete the saved demo operations, not source. For a new demonstration without deleting anything, use a new store folder.

## Live demo

Start with a clean store. Use different plates for each active successful operation.

1. Option 1, plate `123ABC01`, 2 hours: show COMPLETED, AUTHORIZED, RESERVED, PAID and 1000 KZT.
2. Option 3, plate `BFAIL01`, 1 hour: saved sensor blocks make the garage full. Show A committed -> A revoked. B and C have no records. The option clears only its temporary full-sensor simulation afterwards; existing reservations remain untouched.
3. Option 4, plate `CFAIL01`, 1 hour: show A committed -> B committed -> B released -> A revoked. C is DECLINED.
4. Option 5, plate `RESTART1`, 1 hour: show PENDING_B. Copy its UUID; select 0 to exit. Restart against the same folder, use option 2 to prove records remain, then option 7 with that UUID or option 9 to continue. There is no automatic recovery on startup, so the examiner can inspect the pending record first.
5. Option 6 demonstrates PENDING_C after A and B. Restart and resume; B is not reserved twice.
6. Option 10, enter `false`, then option 1 with a new plate: A fails before committing. Restore `true`.
7. Option 11, enter `false`, then park: offline sensors make B fail and A is compensated. Restore `true`.
8. Option 8 cancels a completed operation: C refund -> B release -> A revoke.
9. Option 12 reads overdue paid operations using saved deadlines. The tests use a fixed/advanced clock so no one waits hours to verify this rule.

The original source includes `SubsystemTest`: gate, space and billing tests operate each subsystem directly without either facade, proving independence. Run just that class with `mvn -Dtest=SubsystemTest test`; use `mvn test` for the full coverage check.

## Principle of Least Knowledge

Before, the client needs knowledge of three APIs, their order, IDs and failures:

```java
String id = UUID.randomUUID().toString();
gate.authorize(id, "123ABC01");
spaces.reserve(id);
billing.confirm(id, 2, Card.APPROVE);
// The client must also record progress and handle both compensation depths.
```

After, the client asks one immediate collaborator for its domain operation:

```java
Workflow result = parkingFacade.park("123ABC01", 2, Card.APPROVE);
```

Inspection is also direct: `adminFacade.inspect(result.id())`. The client does not walk through `getRepository().getStore()` or obtain subsystem internals through the facade. The facades know the necessary immediate collaborators; the client needs one facade for parking and a second for administrative work.

## Facade versus Adapter reflection

Smart Parking Garage needs one coherent parking operation over three independently usable APIs. GateController authorizes a plate, ReservationManager selects a space, and PaymentTerminal confirms a payment and deadline. These APIs already work; the problem is making the client coordinate their sequence, durable progress and partial failures. ParkingFacade hides that coordination behind park and resume, while AdminFacade exposes the operational subset needed for inspection and cancellation.

An Adapter would address an incompatible interface. For example, a real sensor vendor might return its readings in a different format, and an adapter could translate them into the inputs expected by SpaceSensorGrid. That would not decide whether to revoke an admission permit after reservation fails, or release a space before revoking the permit after payment declines. Our central intent is simplified orchestration and recovery, so Facade is appropriate. Its internal compensation resembles a small saga; that does not change the public interface's Facade role.

## Tests and measured coverage

Standard command: `mvn test`. The test phase generates JaCoCo HTML/XML/CSV and enforces >=80% line coverage **separately** for `parking.gate`, `parking.space`, `parking.billing`, and `parking.facade` (both facades and the workflow classes). `mvn package` runs these checks too.

Open `target/site/jacoco/index.html` after a run. A verified snapshot is included at `docs/coverage/index.html`, with the test output and coverage summary in `docs/VERIFICATION.md`. The console UI/composition root is reported by JaCoCo but is outside the assignment's subsystem/facade coverage gate; the real CLI is also verified through process-level demo runs.

F.I.R.S.T.: tests use small local files (Fast); each JUnit `@TempDir` gives an isolated store (Independent); a fixed `Clock` and deterministic outcomes make runs Repeatable; JUnit assertions make them Self-validating. Source and tests are committed together in the supplied repository history (Timely). This documents actual commits, not a claim that every test was written before implementation.

Example domain testing language:

```java
try (ParkingScenario garage = ParkingScenario.aGarage(directory)) {
    garage.thatIsFull().parks("BFAIL01", APPROVE).thenOnlyTheGateIsRevoked();
}
```

Tests explicitly inspect reloaded repository state, verify undo order, close/reopen the application mid-chain, and exercise stale progress and interrupted rollback. `docs/CLASS_GUIDE_EN_RU.md` explains every production class and its methods. `docs/DEFENSE_GUIDE_EN_RU.md` gives oral answers, demo instructions and worked live-coding exercises.

## UML and submission

`docs/uml.svg` is the full class diagram and `docs/uml.png` is its raster copy. Both match the source; nested enums and the Snapshot record are listed in their owner boxes. Store/exception classes and the composition root are distinguished from the 13 domain classes.

The source and local commits are ready to publish. The archive does not create a remote GitHub repository or submit to Moodle. Create your GitHub repository, then preserve the included history by cloning `docs/repository-history.bundle` into a new folder, adding your actual remote and pushing:

```text
git clone docs/repository-history.bundle SmartParkingGarageGit
cd SmartParkingGarageGit
git remote remove origin
git remote add origin YOUR_ACTUAL_GITHUB_REPOSITORY_URL
git push -u origin main
```

The bundle's branch is `main`. Replace the URL placeholder with your repository's real URL. Submit that link through Moodle before the deadline announced in class. The assignment file does not specify a calendar deadline; do not assume one.
