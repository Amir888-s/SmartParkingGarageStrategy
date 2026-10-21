# Verified Assignment 5 results

Date: 20 October 2026. Java: OpenJDK 17. Maven: 3.9.9. JUnit Jupiter: 5.10.2.

## Build

`mvn clean package` completed successfully. Final suite: **105 invocations, 0 failures, 0 errors, 0 skipped**.

| Test class | Invocations |
|---|---:|
| PricingContractTest | 16 |
| StrategyTest | 57 |
| SubsystemTest | 9 |
| WorkflowTest | 16 |
| CompensationRecoveryTest | 5 |
| StoreTest | 2 |
| Total | 105 |

The unchanged Assignment 4 tests account for 32 invocations. The before commit ran these same 32 plus all 16 PricingContractTest invocations: **48 passed**. The after commit includes the same tests plus 57 new strategy invocations.

PricingContractTest SHA256 on both commits: `07a829acc3eccc7c386964c93e7be59597fc3b741600929a1d571a41e30819d8`.

Before: `4a8187a83db4de021efacf2b3fe8854716e9aeb4`. After: `80c1ac664c854073a4a03225aed63f08180d8ce6`. The test source is byte identical; all original test files are unchanged from Assignment 4.

## Measured line coverage

| Package | Covered lines | Line coverage |
|---|---:|---:|
| parking.pricing | 39 / 39 | 100% |
| parking.allocation | 36 / 36 | 100% |
| parking.gate | 45 / 45 | 100% |
| parking.space | 43 / 43 | 100% |
| parking.billing | 69 / 69 | 100% |
| parking.facade | 89 / 89 | 100% |
| parking.store | 37 / 37 | 100% |
| parking.app | 37 / 133 | 27.8% |

JaCoCo enforces at least 80% line coverage separately for both strategy packages plus the four original subsystem/facade packages. Console/composition code remains outside that gate. Line coverage is not a claim of exhaustive behavior or 100% branch coverage.

Current HTML/XML/CSV reports are in `docs/coverage/`. JUnit XML is in `docs/assignment5/junit/`. Before and after build logs are retained beside this file. Environment specific paths in raw JUnit XML describe the verification machine, not project dependencies.

## Process level checks

The rebuilt executable JAR was used to run StrategyDemo. Its transcript is STRATEGY_DEMO.txt. Console regression and strategy configuration results are recorded in CLI_VERIFICATION.md. Both compensation orders remain covered by original tests and new integration tests for every strategy combination.

## Preserved history

The Git bundle retains original Assignment 4 history and real local before/after commits. Commit patch links are in README. A remote push or course submission was not performed.
