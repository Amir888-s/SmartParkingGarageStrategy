# Executable JAR verification

All checks ran against the rebuilt dist/smart-parking-garage-1.0.0.jar through real standard input.

- Default option 1: 2 hours, 1000 KZT, COMPLETED.
- Option 3: B fails; events A committed → A revoked.
- Option 4: C fails; events A committed → B committed → B released → A revoked.
- Option 13: identical input comparisons, manual choices, automatic rules, runtime swaps.
- Option 14 then option 1: capped/lift settings produce 3000 KZT and bay 2 for eight hours.
- Exit then reopen: the completed amount and bay remain persisted.
- Option 5 then real process restart and option 7: paused operation resumes to COMPLETED.
- Invalid strategy mode: console reports an input error and remains operational.

Transcripts: CLI_DEFAULT.txt, CLI_STRATEGIES.txt, CLI_REOPEN.txt, CLI_RESTART.txt.
