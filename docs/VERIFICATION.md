# Assignment 4 verification record

For current combined Assignment 4 and 5 results, see [assignment5/VERIFICATION.md](assignment5/VERIFICATION.md).

# Verified build and tests

Verified on 11 October 2026 using OpenJDK 17 and Maven 3.9.9.

Command: `mvn package` (includes `mvn test` and the JaCoCo coverage gate). Build: PASS.

Tests: 32; failures: 0; errors: 0; skipped: 0.

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| parking.CompensationRecoveryTest | 5 | 0 | 0 | 0 |
| parking.StoreTest | 2 | 0 | 0 | 0 |
| parking.SubsystemTest | 9 | 0 | 0 | 0 |
| parking.WorkflowTest | 16 | 0 | 0 | 0 |

| Package | Covered lines | Missed lines | Line coverage |
|---|---:|---:|---:|
| parking/app | 20 | 50 | 28.57% |
| parking/billing | 49 | 0 | 100.00% |
| parking/facade | 89 | 0 | 100.00% |
| parking/gate | 45 | 0 | 100.00% |
| parking/space | 41 | 0 | 100.00% |
| parking/store | 36 | 1 | 97.30% |

Required packages gate, space, billing and facade each pass the >=80% line-coverage gate. The report also includes the console app and shared infrastructure. The CLI/composition-root package is outside the required package gate and has lower unit coverage; its live operation is separately exercised below.

Process-level verification: PASS. A built JAR was started in a clean temporary store. One process executed success, full-garage failure, declined-payment failure and paused chains after A and B. That process exited. A second real JVM against the same directory inspected and recovered the pending workflows. Assertions checked stored workflow states and that no space/payment was duplicated.

See `LIVE_DEMO_TRANSCRIPT.txt` and `coverage/index.html`. Coverage measures executed lines, not proof of production reliability or a guaranteed grade.

Source launch with `mvn exec:java`: PASS; the menu accepted option 0 and exited normally.
