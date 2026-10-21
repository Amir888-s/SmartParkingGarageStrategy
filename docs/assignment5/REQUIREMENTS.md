# Assignment 5 requirement mapping

| Requirement | Implementation | Evidence |
|---|---|---|
| Same domain, existing functionality | Smart Parking Garage with the original facade chain | Original 32 JUnit invocations still pass |
| Two strategy interfaces with 1–2 methods | PricingStrategy, AllocationStrategy; one method each | Source interfaces |
| At least three distinct algorithms per family | Linear/cap/grace plus sensor; ID/distance/history | StrategyTest; demo option 13 |
| One context per family | PricingPolicy, SpaceSensorGrid | Constructors plus setStrategy |
| No type dispatch in contexts | Direct interface delegation | Context source; custom strategy tests |
| Separate automatic selection | PricingSelector, AllocationSelector | Priority and boundary tests |
| Bad implementation committed before refactor | Real before commit with PricingPolicy switch | before-refactor.patch; Git bundle |
| Same tests before and after | Unmodified PricingContractTest and original tests | XML evidence; contract SHA256 in verification |
| External data behind own interface | OccupancySource injected into DemandPricing | Fixed FakeOccupancySource tests |
| JUnit 5 test per strategy | Direct expected-result tests for all six algorithms | StrategyTest |
| Test every selector rule | Priority, fallback, manual override and boundary checks | Parameterized selector tests |
| Parameterized invariant test | Both pricing and allocation families | @MethodSource tests |
| Same input demo | Eight hours; shared bay snapshot | STRATEGY_DEMO.txt |
| Manual versus automatic demo | Named modes and automatic selector rules | Option 13; option 14 then option 1 |
| Runtime replacement | setStrategy on the same context objects | Option 13; context tests |
| README class responsibilities | Every source type, nested Snapshot, test helper | At most 25 words; forbidden words checked |
| Half-page comparison | Strategy versus State versus Template Method | README comparison section |
| Refactoring commit links | Local commit patch views with full hashes | README links; complete Git bundle |
| Preserve Facade rollback depths | B failure undoes A; C failure undoes B then A | Original tests plus three strategy combinations |
| Defense preparation | New strategy, selector rule, data source exercises | DEFENSE_GUIDE_RU.md |

No lecture sorting, route navigation, generic calculator or payment-method strategies are used. The existing simulated card approval flag remains part of Assignment 4; it is not a Strategy family.

Hosted GitHub commit URLs require pushing the included history to the student's repository. The archive does not perform a remote push or a course submission. Originality relative to unseen student projects cannot be verified.
