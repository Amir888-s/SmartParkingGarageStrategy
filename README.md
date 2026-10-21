# Smart Parking Garage

Assignments 4 and 5: preserved Facade workflow with interchangeable pricing and bay allocation algorithms.
Original project author: Amir Kabdilmalik, Astana IT University.

## Start

Java 17 or newer is required. Extract the archive, then run `run.cmd` on Windows or `sh run.sh` on Linux/macOS. The included JAR runs without Maven.

```text
java -jar dist/smart-parking-garage-1.0.0.jar
mvn clean package
mvn test
```

After source edits, use `build.cmd` or `sh build.sh` to refresh the JAR in `dist`; `mvn package` writes the new JAR to `target`.

In IntelliJ, open `pom.xml`, choose JDK 17, import Maven, then run `parking.app.Main`.
The original package layout, launch scripts, CSV persistence, restart recovery, overstay alerts, cancellation, subsystem independence and both compensation depths remain. Defaults remain 500 KZT per hour and the first free bay. Full Assignment 4 documentation is retained in [Assignment4_README.md](docs/Assignment4_README.md).

## Two strategy families

| Family | Interface | Context | Concrete algorithms |
|---|---|---|---|
| Price calculation | `PricingStrategy` | Existing `PricingPolicy` | `HourlyPricing`, `CappedPricing`, `DemandPricing` |
| Bay allocation | `AllocationStrategy` | Existing `SpaceSensorGrid` | `FirstFreeAllocation`, `NearLiftAllocation`, `LeastUsedAllocation` |

Each interface has one method. Both contexts accept a strategy in their constructor and expose `setStrategy()`. Neither context dispatches on concrete strategy types.

| Algorithm | Rule | Identical sample input | Result |
|---|---|---|---|
| Hourly | 500 × hours | 8 hours | 4000 KZT |
| Capped | min(500 × hours, 3000) | 8 hours | 3000 KZT |
| Demand | 500 × hours + ceil(occupancy × 250) × max(0, hours − 2) | 8 hours; occupancy 0.8 | 5200 KZT |
| First free | Smallest free bay number | Bays 1,2,3; usage counts 5,2,0 | Bay 1 |
| Near lift | Minimum distance to lift at bay 2; smaller ID breaks ties | Same bays and history | Bay 2 |
| Least used | Minimum historical use count; smaller ID breaks ties | Same bays and history | Bay 3 |

A cap, a grace period with sensor based surcharges, distance ordering and usage balancing provide different logic. These are not percentage substitutions. All durations remain whole hours from 1 through 24. A missing bay usage count means zero. Only free, unblocked bays enter the allocation input. Offline sensors or no eligible bays retain the original domain failures.

## Automatic selection rules

`PricingSelector` checks these rules in order in auto mode:

1. Duration at least 6 hours → capped.
2. Local time before 06:00 or from 22:00 → capped.
3. Occupancy at least 0.8 → demand.
4. Otherwise → hourly.

`AllocationSelector` checks these rules in order in auto mode:

1. Near lift preference → near lift.
2. Local time from 08:00 inclusive to 18:00 exclusive → least used.
3. Otherwise → first free.

Manual mode bypasses automatic rules. Selection conditionals live in the selectors. Ordinary business validation and the existing facade lifecycle still have their necessary conditions. Local time uses `Asia/Almaty`; tests inject a fixed clock. The lift option is a location preference, not a guarantee of an accessible bay.

## External data isolation

`DemandPricing` receives an `OccupancySource` interface. `SimulatedOccupancySource` supplies the live demo reading. Unit tests inject a fixed fake source. No real API, network access or hardware is required. Occupancy must be finite and between 0 and 1.

Historical bay usage comes from the existing reservation repository, including released reservations. The calculation strategies do not access CSV files. `SpaceAvailability` copies collections to keep the shared input immutable.

## Live demonstration

- Option **13** compares all three algorithms in each family on the same input. It shows manual selection, automatic rules, a live sensor update and repeated `setStrategy()` calls on the same contexts. It does not modify garage records.
- Option **14** sets pricing (`hourly`, `capped`, `demand`, `auto`), allocation (`first`, `lift`, `balanced`, `auto`), simulated occupancy and lift preference.
- Option **1** then parks through the original Facade using those algorithms. The menu calls `GarageApplication.prepareStrategies(hours)` before each new request, using the actual duration. Programmatic callers configure, prepare, then call the facade.
- For a practical comparison: choose capped/lift/0.8/true; park for 8 hours; inspect 3000 KZT and bay 2. Cancel the operation with option 8. Switch to hourly/first/0/false; park again for 8 hours; inspect 4000 KZT and bay 1.
- Original options **3** and **4** still demonstrate B failure → undo A, and C failure → undo B then A.
- Original options **5**, **6**, **7**, **9** still demonstrate persisted pauses and recovery.

The application can also run the comparison directly:

```text
java -cp dist/smart-parking-garage-1.0.0.jar parking.app.StrategyDemo
```

Pricing configuration is session scoped, as are the simulated readings. Existing reservations and payment amounts remain persisted. A workflow that has not reached price calculation uses the active tariff when it reaches billing. `price_quotes.csv` pins the duration and first calculated amount before a payment is saved. Subsequent retries reuse that quote even after a tariff swap or restart. Old Assignment 4 payment rows keep their original CSV format and are recognized as hourly legacy records. No schema migration is needed. A quote alone does not represent a charge.

## Why Strategy rather than State or Template Method

The design varies two algorithms while keeping the surrounding workflow stable. PricingPolicy stores a PricingStrategy reference, receives it through its constructor, replaces it through setStrategy(), then delegates amountFor(). SpaceSensorGrid follows the same composition model for AllocationStrategy. Each family has three concrete implementations with different decision logic. A caller can apply every implementation to the same immutable input without changing the context class.

The client deliberately selects an algorithm to meet an external requirement: a price cap, live occupancy, proximity to the lift, or balanced bay usage. PricingSelector and AllocationSelector isolate that selection policy. Contexts contain no conditional dispatch based on concrete algorithm types. A new implementation can be passed directly to either context without editing its existing code; StrategyTest demonstrates this using previously unregistered implementations. Adding a new menu label or automatic rule remains an explicit change to the selector or composition setup.

State addresses changes in an object's lifecycle, where behavior follows transitions such as NEW to PAID or RESERVED to RELEASED. Those existing entity statuses remain in this project, but the tariff implementations never transition the parking workflow or decide which algorithm follows them. A completed payment can retain its original quote even after the client changes the active tariff. The selected algorithm therefore represents a calculation policy, rather than a lifecycle phase.

Template Method normally fixes an algorithm skeleton in a superclass and allows subclasses to override individual steps. Here no algorithm inherits a context skeleton. Separate objects implement a common interface, and the context delegates the complete operation to the selected object. This composition permits a runtime swap on the same context instance. Facade serves a separate purpose: ParkingFacade still coordinates gate admission, bay reservation, billing and recovery behind a simplified client API.

## Class responsibilities

Each responsibility is one sentence of at most 25 words; the descriptions omit the prohibited conjunctions.

| Class or interface | Responsibility |
|---|---|
| `parking.allocation.AllocationSelector` | Chooses the allocation algorithm for the current situation. |
| `parking.allocation.AllocationSituation` | Carries the current allocation selection criteria. |
| `parking.allocation.AllocationStrategy` | Defines the bay allocation algorithm contract. |
| `parking.allocation.FirstFreeAllocation` | Selects the available bay with the smallest identifier. |
| `parking.allocation.LeastUsedAllocation` | Selects the available bay with the lowest historical usage. |
| `parking.allocation.NearLiftAllocation` | Selects the available bay closest to the lift. |
| `parking.allocation.SpaceAvailability` | Captures an immutable snapshot of eligible bays. |
| `parking.app.GarageApplication` | Constructs the garage dependency graph with selectable algorithms. |
| `parking.app.Main` | Handles the interactive console commands. |
| `parking.app.StrategyDemo` | Demonstrates comparable results from interchangeable algorithms. |
| `parking.billing.BillingRepository` | Persists payment records with durable price quotes. |
| `parking.billing.OverstayAlert` | Determines the prepaid parking deadline. |
| `parking.billing.Payment` | Enforces payment lifecycle transitions. |
| `parking.billing.PaymentTerminal` | Finalizes prepaid parking charges against durable quotes. |
| `parking.billing.PriceQuote` | Stores the original duration with its calculated charge. |
| `parking.billing.PricingPolicy` | Delegates price calculation to its current pricing strategy. |
| `parking.facade.AdminFacade` | Exposes administrative garage operations. |
| `parking.facade.ParkingFacade` | Coordinates the persisted admission workflow with reverse compensation. |
| `parking.facade.Workflow` | Tracks durable progress through the parking operation. |
| `parking.facade.WorkflowRepository` | Persists workflow progress records. |
| `parking.gate.GateController` | Issues reversible admission authorizations. |
| `parking.gate.GatePermit` | Enforces admission permit lifecycle transitions. |
| `parking.gate.GateRepository` | Persists admission permits with gate availability. |
| `parking.gate.LicensePlateReader` | Normalizes supported license plate input. |
| `parking.pricing.CappedPricing` | Limits the calculated charge to a daily ceiling. |
| `parking.pricing.DemandPricing` | Applies sensor based surcharges after a two hour grace period. |
| `parking.pricing.DurationRules` | Validates the supported parking duration range. |
| `parking.pricing.HourlyPricing` | Calculates a linear hourly parking charge. |
| `parking.pricing.OccupancySource` | Defines the external occupancy reading boundary. |
| `parking.pricing.PricingSelector` | Chooses the pricing algorithm for the current situation. |
| `parking.pricing.PricingSituation` | Carries the current pricing selection criteria. |
| `parking.pricing.PricingStrategy` | Defines the parking price calculation contract. |
| `parking.pricing.SimulatedOccupancySource` | Provides a mutable simulated occupancy reading. |
| `parking.space.ParkingReservation` | Enforces reservation lifecycle transitions. |
| `parking.space.ReservationManager` | Reserves available bays using persisted booking history. |
| `parking.space.SpaceRepository` | Persists reservations with sensor settings. |
| `parking.space.SpaceSensorGrid` | Delegates bay selection to its current allocation strategy. |
| `parking.store.CsvStore` | Atomically replaces individual CSV tables. |
| `parking.store.ParkingException` | Reports domain failures using typed reasons. |
| `parking.store.RowStore` | Defines the table persistence boundary. |
| `parking.store.StorageException` | Reports persistence failures. |
| `CompensationRecoveryTest` | Checks recovery after interrupted compensation. |
| `ParkingScenario` | Provides reusable garage test setup with domain assertions. |
| `StoreTest` | Checks CSV persistence behavior. |
| `SubsystemTest` | Checks independent subsystem behavior. |
| `WorkflowTest` | Checks facade workflow behavior across durable restarts. |
| `parking.strategy.PricingContractTest` | Checks unchanged observable tariff behavior across refactoring. |
| `parking.strategy.StrategyTest` | Checks interchangeable algorithms with selection rules. |
| `StrategyTest.FakeOccupancySource` | Returns a fixed occupancy reading for deterministic tests. |
| `AdminFacade.Snapshot` | Carries a read only administrative workflow view. |

## Unit tests and verification

JUnit 5 tests cover every concrete strategy, every automatic selection rule, manual overrides, rule boundaries, runtime swaps, custom strategies, immutable inputs, fixed sensor fakes, persisted quotes and preserved facade compensation. Both families have a `@ParameterizedTest` that executes the same invariant across all implementations.

`PricingContractTest.java` is byte identical before and after the refactor. Both commits run this same 16 invocation contract plus the original 32 tests. Additional Strategy tests belong to the after commit. See [verification](docs/assignment5/VERIFICATION.md), [before test evidence](docs/assignment5/before-pricing-tests.xml), [after test evidence](docs/assignment5/after-pricing-tests.xml) and [current coverage](docs/coverage/index.html). JaCoCo enforces at least 80% line coverage separately for gate, space, billing, facade, pricing and allocation packages.

## Refactoring commits

- [Before refactoring commit `4a8187a83db4de021efacf2b3fe8854716e9aeb4`](docs/assignment5/before-refactor.patch): all tariff variants inside one switch.
- [After refactoring commit `80c1ac664c854073a4a03225aed63f08180d8ce6`](docs/assignment5/after-refactor.patch): extracted algorithms, contexts, selectors, external data boundary and tests.

The linked patches are portable views of the real local Git commits. The full parent history is preserved in `docs/repository-history.bundle`, including the original Assignment 4 commits. No remote repository was supplied, so no GitHub commit URLs are fabricated. To obtain hosted commit links, push this exact history to your own repository:

```text
git clone docs/repository-history.bundle SmartParkingGarageGit
cd SmartParkingGarageGit
git remote remove origin
git remote add origin YOUR_REPOSITORY_URL
git push -u origin main --tags
```

After pushing, the commit hashes above identify the corresponding `/commit/<hash>` pages on your remote. Do not create a new initial commit from extracted files if your submission must preserve refactoring history.

To rerun the identical test set on each commit, use the actual hashes listed above in separate worktrees, then execute `mvn test` on before and `mvn -Dtest=PricingContractTest,SubsystemTest,WorkflowTest,CompensationRecoveryTest,StoreTest test` on after.

## Report and defense materials

- [Assignment 5 report](docs/assignment5/Assignment_5_Report.docx)
- [Russian class and method guide](docs/assignment5/CLASS_GUIDE_RU.md)
- [Russian defense guide](docs/assignment5/DEFENSE_GUIDE_RU.md)
- [Pricing UML](docs/assignment5/pricing-uml.png)
- [Allocation UML](docs/assignment5/allocation-uml.png)
- [Requirement mapping](docs/assignment5/REQUIREMENTS.md)
- [Demonstration transcript](docs/assignment5/STRATEGY_DEMO.txt)

Original `docs/Assignment_Report.docx`, `docs/uml.*`, guides and original demo transcript describe Assignment 4. The new materials supplement them; current combined behavior is documented here.
