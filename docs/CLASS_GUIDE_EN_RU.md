# Smart Parking Garage full class guide

## The idea in simple words

**EN:** A driver asks to park. First the program authorizes an admission permit. Next it reserves a space. Finally it confirms a prepaid payment and saves the time when an overstay alert becomes relevant. One facade coordinates these steps. If the garage is full, the permit is revoked. If payment declines, the reservation is released first, then the permit is revoked.

**RU:** Водитель хочет припарковаться. Сначала программа оформляет разрешение на въезд. Затем бронирует место. В конце подтверждает предоплату и сохраняет время окончания парковки. Фасад управляет этими шагами. Если мест нет, разрешение отменяется. Если оплата отклонена, сначала освобождается место, затем отменяется разрешение.

The application is a simulation with real file persistence. It does not control a physical gate or charge a real card. / Это симуляция с настоящим сохранением в файлы. Реальный шлагбаум и банковская карта не подключены.

## What the assignment asks

The supplied assignment requires an individual original domain, three independent subsystems with 3-5 classes each, entities with at least three legal statuses, pure business logic and a separate persistence gateway in every subsystem. It also requires two facades, A -> B -> C sequencing, saved domain state and saved chain progress, A-only compensation after B fails, B-then-A compensation after C fails, an additional realistic failure, a runnable demo, a UML image, Least-Knowledge before/after code, a short Facade-versus-Adapter reflection, isolated self-validating tests with a domain helper, a restart test, >=80% measured line coverage, tests committed alongside source, and a Git repository link submitted through Moodle.

По заданию нужны три независимые подсистемы по 3-5 классов; собственные состояния, бизнес-правила и репозиторий каждой; два фасада; последовательность A -> B -> C; реальные файлы; восстановление после перезапуска; два уровня компенсации; дополнительные ошибки; консольная демонстрация; UML; объяснение Least Knowledge и Facade vs Adapter; независимые тесты, DSL, проверка перезапуска, покрытие минимум 80% и история Git. Ссылку на свой опубликованный репозиторий нужно самостоятельно отправить в Moodle.

## Call flow

`Main -> ParkingFacade.begin -> WorkflowRepository.save -> GateController.authorize -> GateRepository.save -> WorkflowRepository.save -> ReservationManager.reserve -> SpaceRepository.save -> WorkflowRepository.save -> PaymentTerminal.confirm -> BillingRepository.save -> WorkflowRepository.save`.

The last workflow save records COMPLETED. Each subsystem talks to its own repository. Every repository talks directly to the `RowStore` abstraction, whose real implementation is `CsvStore`. The facade coordinates operations; it does not serialize another subsystem's entity itself.

Последняя запись устанавливает COMPLETED. Каждая подсистема обращается к своему репозиторию, а репозиторий — к RowStore. Реализация CsvStore работает с диском. Фасад задаёт порядок действий, но не записывает сущности других подсистем напрямую.

## Subsystem A admission

### GatePermit

**Role / Роль:** the entity containing an admission authorization. Это сущность разрешения на въезд, а не сам физический шлагбаум.

Fields: `id` connects this permit to the workflow; `plate` is the normalized plate; `status` is a nested `Status` enum. `id` и `plate` неизменяемы, `status` изменяется только через разрешённые методы.

`GatePermit(id, plate, status)` constructs a new entity or restores a saved entity. `authorize()` allows NEW -> AUTHORIZED. `revoke()` allows AUTHORIZED -> REVOKED. The private `transition(from, to)` checks the current state and throws `ParkingException(ILLEGAL_TRANSITION)` otherwise. `id()`, `plate()` and `status()` return values.

Why here? A caller cannot call revoke on a NEW permit or authorize it twice, even if it bypasses the facade. / Проверка находится в сущности, поэтому даже при прямом обращении к подсистеме запрещённые переходы не работают.

```java
GatePermit permit = new GatePermit("demo", "123ABC01", GatePermit.Status.NEW);
permit.authorize(); // NEW -> AUTHORIZED
permit.revoke();    // AUTHORIZED -> REVOKED
// permit.authorize(); would throw: REVOKED cannot become AUTHORIZED.
```

### LicensePlateReader

**Role / Роль:** pure input-validation logic. Чистая бизнес-логика проверки и нормализации номера.

`read(input)` rejects null, trims leading/trailing spaces, converts letters to uppercase using `Locale.ROOT`, and accepts 3-12 characters from `[A-Z0-9-]`. It returns the normalized plate or throws INVALID_INPUT. It stores nothing and knows no other subsystem.

Example: `" abc123 " -> "ABC123"`; `"A,B"` fails. Это упрощённый формат для демонстрации, не официальный стандарт номерных знаков Казахстана.

### GateRepository

**Role / Роль:** A's persistence gateway. Собственный шлюз подсистемы A для чтения и записи.

The constructor receives `RowStore`. `all()` reads `gate.csv` and reconstructs GatePermit objects, including the enum with `valueOf`. `find(id)` searches those freshly read records and returns Optional. `save(permit)` reads the existing rows, replaces the row with the same ID, then writes the table. It is an upsert: insert new or replace existing.

`isOpen()` reads `gate_settings.csv`, defaulting to true if there is no setting. `setOpen(open)` persists the setting. Neither the facade nor an in-memory list owns the source of truth.

Список используется только как временный результат чтения файла. Он не заменяет базу данных и не хранится в static-поле.

### GateController

**Role / Роль:** A's public subsystem API. Он соединяет проверку номера, правила въезда и собственный репозиторий.

Constructor dependencies: GateRepository and LicensePlateReader. `authorize(id, input)` validates the plate, then checks persisted records. An existing matching AUTHORIZED permit with the same ID is returned unchanged. This makes retries idempotent. A different plate or a revoked record under the same ID is a conflict. For a new ID it checks whether the gate is open and whether this plate already has an active permit. Then it creates NEW, calls authorize, and saves AUTHORIZED.

`revoke(id)` loads a permit, revokes and saves it only if AUTHORIZED. Missing or already-revoked permits produce no additional effect. `find`, `all` and `setOpen` expose direct subsystem operations.

Why duplicate-plate validation? Two different workflows must not admit the same car at once. / Разные workflow не должны одновременно выдавать два активных разрешения одному номеру. После компенсации можно создать новый workflow для того же номера.

## Subsystem B parking spaces

### ParkingReservation

**Role / Роль:** entity for a reserved space. Сущность бронирования места.

Fields: immutable `id`, immutable integer `space`, mutable nested `Status`. Constructor supports NEW creation and persisted-state hydration. `reserve()` allows NEW -> RESERVED; `release()` allows RESERVED -> RELEASED. Private `transition` rejects illegal transitions. `id()`, `space()` and `status()` are accessors.

RELEASED records remain for history but do not count as occupied. / Запись остаётся после отмены, однако освобождённое место больше не считается занятым.

### SpaceSensorGrid

**Role / Роль:** pure space-selection decision. Чистая логика выбора свободного места.

`CAPACITY = 3`. `firstFree(occupied, blocked, online)` first rejects offline sensors with SENSOR_OFFLINE, then loops over spaces 1 through CAPACITY. The first space absent from both sets is returned. If none exists, it throws NO_SPACE.

`occupied` comes from active persisted reservations. `blocked` comes from saved sensor readings used to simulate spaces occupied by an external car or unavailable for use. Sensor occupancy is independent of reservations, which makes B's failure realistic even after A authorized admission.

`occupied` — места с активным бронированием; `blocked` — места, которые датчики считают недоступными. Место свободно, только если отсутствует в обоих наборах.

Example: occupied = {1}, blocked = {2}, online = true -> space 3. This class neither opens files nor calls GateController or PaymentTerminal.

### SpaceRepository

**Role / Роль:** B's gateway. Репозиторий бронирований и сохранённых настроек датчиков.

`all()` reads `space.csv` and constructs entities; `find(id)` searches them; `save(reservation)` upserts by ID. `blockedSpaces()` reads integers from `space_blocks.csv`; `setBlockedSpaces(set)` saves them in sorted order. `sensorsOnline()` reads `sensor_settings.csv`, default true; `setSensorsOnline(value)` writes it.

Sorting makes sensor settings predictable on disk. A Set prevents duplicate blocked-space values. / Set хранит уникальные значения, а сортировка делает запись в файл стабильной.

### ReservationManager

**Role / Роль:** B's independent public API. Управляет бронированием, не знает про въезд и оплату.

The constructor receives SpaceRepository and SpaceSensorGrid. `reserve(id)` reloads an existing reservation. If it is RESERVED, it returns it without allocating a second space. If RELEASED, the operation ID cannot be reused and CONFLICT is thrown. For a new ID, the manager reads all RESERVED spaces, reads sensor blocks and availability, asks firstFree for a decision, creates NEW, transitions to RESERVED, and saves it.

`release(id)` reloads and reverses only an active reservation; retrying release is harmless. `find`, `all`, `setBlockedSpaces`, and `setSensorsOnline` are direct APIs.

После отмены тот же ID нельзя снова активировать. Для новой парковки нужен новый workflow ID; освободившееся место может получить другой workflow.

## Subsystem C payment and overstay

### Payment

**Role / Роль:** payment entity with its durable deadline. Сущность платежа с сохранённым сроком парковки.

Fields: `id`, integer `amount` in KZT, `dueAt` as an Instant, and nested Status. Whole KZT avoids floating-point rounding in this small application. All fields except status are final.

`pay()` permits NEW -> PAID; `decline()` NEW -> DECLINED; `refund()` PAID -> REFUNDED; `cancel()` NEW -> CANCELLED. `transition` enforces the source state. Accessors: `id()`, `amount()`, `dueAt()`, `status()`.

A refund is only meaningful after money was charged. DECLINED means no money was charged, so a decline is not refunded. CANCELLED cancels an unfinished NEW record. / REFUNDED — возврат ранее оплаченного платежа; DECLINED — отказ без списания; CANCELLED — отмена ещё не завершённого платежа.

### PricingPolicy

**Role / Роль:** pure price calculation and duration rule. Чистый расчёт цены.

`HOURLY_RATE = 500`. `amountFor(hours)` rejects durations outside 1-24 whole hours, otherwise returns `hours * HOURLY_RATE`. Example: 2 hours -> 1000 KZT. This validation still applies when C is used directly outside the facade.

The facade validates duration early to avoid reserving resources for an invalid request; PricingPolicy validates its own API independently. / Ранний фильтр фасада не заменяет правила самой подсистемы.

### OverstayAlert

**Role / Роль:** pure deadline and overdue logic. Вычисляет срок и определяет превышение времени.

`dueAt(start, hours)` returns start + Duration.ofHours(hours). `isOverdue(payment, now)` is true only when status is PAID and now is strictly after dueAt. At the exact deadline it is false; one second after is true. A refund disables the alert automatically because REFUNDED is not PAID.

There is no separate alert table: the Payment record already persists everything needed to reconstruct the alert. The application reports alerts through an inspection command; it does not send SMS or run a background notification scheduler.

Отдельный статус сигнала не нужен: данные для его восстановления уже сохранены в Payment. Проверка выполняется командой меню.

### BillingRepository

**Role / Роль:** C's persistence gateway. Репозиторий платежей.

`all()` reads `billing.csv`, parses the amount, reconstructs Instant and Payment.Status. `find(id)` returns Optional. `save(payment)` replaces/inserts the payment with that ID and writes the full table. It stores status, amount and deadline together in one file update.

Это важно: нельзя получить PAID без сохранённого dueAt в той же записи.

### PaymentTerminal

**Role / Роль:** C's public API. Подтверждает или отклоняет оплату и предоставляет данные сигналов.

Constructor dependencies: BillingRepository, PricingPolicy, OverstayAlert and Clock. Nested `Card` enum has APPROVE and DECLINE, deterministic simulated terminal decisions; it does not hold real card numbers.

`confirm(id, hours, card)` calculates the price, reloads the payment or creates NEW with the calculated deadline. A saved payment with a different amount is a conflict. A PAID payment is returned without charging again. DECLINED throws PAYMENT_DECLINED again; REFUNDED/CANCELLED cannot be reused. A null decision is invalid. DECLINE changes NEW to DECLINED, saves it, then throws. APPROVE changes NEW to PAID, saves it and returns it.

`reverse(id)` refunds PAID or cancels NEW; it does nothing for absent or already-terminal records. `find`, `all` provide direct access. `overdueIds()` reads saved payments, checks each using the injected clock, and returns matching IDs.

Clock is injected so tests use a known time without sleeping. / Clock передаётся в конструктор: тесты используют фиксированное время и не зависят от реальных часов.

## Coordination and the two facades

### Workflow

**Role / Роль:** durable progress and request record. Хранит запрос и прогресс общей операции.

Fields: id, raw plate, hours, Card, mutable Status, failure message and a mutable internal list of events. The first four do not change. Constructor copies the input list. Accessors expose the fields; `events()` returns an immutable copy so a caller cannot change the saved journal by editing a list.

`status(value)`, `failure(value)` and `record(event)` have package access, so only coordination code in the facade package updates them. `record` avoids duplicate events during recovery. Unlike GatePermit or Payment, Workflow is the coordinator's record, not a domain subsystem entity.

States: PENDING_A means A has not yet been journalled; PENDING_B means A was journalled and B is next; PENDING_C means B was journalled and C is next; COMPLETED means success; COMPENSATING means rollback started and may need retry; COMPENSATED means all applicable undo steps finished; FAILED means no subsystem effect existed to reverse.

Важно: pending-статус не доказывает, что следующий шаг отсутствует на диске. Приложение могло остановиться между сохранением шага и обновлением workflow.

### WorkflowRepository

**Role / Роль:** gateway for durable chain progress. Сохраняет общую операцию отдельно от A, B и C.

`all()` reads `workflow.csv` and restores Workflow objects. `get(id)` returns the matching record or throws UNKNOWN_WORKFLOW. `save(workflow)` upserts by ID. Private `encode` and `decode` use Base64 for raw plate, error text and the event list. Events are joined with a semicolon before encoding; current event names contain no semicolons.

Base64 prevents commas/newlines in invalid user input or error text from creating extra CSV columns. It does not encrypt personal data.

### ParkingFacade

**Role / Роль:** main Facade. Главный фасад обычного клиента.

Its constructor receives GateController, ReservationManager, PaymentTerminal and WorkflowRepository. It holds references to them: composition, not inheritance from three subsystems.

`park(plate, hours, card)` calls begin with Checkpoint.NONE. `begin(..., checkpoint)` validates duration/card/checkpoint, generates a UUID, saves PENDING_A before any side effect, and calls private advance. Checkpoint is NONE, AFTER_A or AFTER_B.

`advance(workflow, checkpoint)` authorizes A, records A committed and PENDING_B, reserves B, records B committed and PENDING_C, confirms C, records C committed and COMPLETED. Each save finishes before the next step begins. A checkpoint intentionally returns between committed stages for inspection/restart. Domain/storage failures call compensate.

`resume(id)` reloads the workflow. For COMPENSATING it retries compensation. For COMPLETED/COMPENSATED/FAILED it returns the terminal record. Otherwise it advances using the same ID. Re-running a stage is safe because that subsystem reloads its state and recognizes the existing record.

`compensate(id, reason)` is package-visible for AdminFacade. It reads Workflow again, saves COMPENSATING, then reads actual C/B/A records. A PAID C is refunded or a NEW C is cancelled; DECLINED has no charge to reverse. B is released before A is revoked. After each undo, its event is persisted. Already-reversed records are recognized and their missing events can be reconstructed. At the end it saves COMPENSATED, or FAILED if no subsystem record existed. An undo/storage exception escapes, leaving the on-disk progress available for later retry.

Для B-ошибки запись B отсутствует, поэтому компенсируется только A. Для C-ошибки B существует и освобождается раньше A. Это решение принято по фактически сохранённым записям, а не по boolean-переменным.

### AdminFacade

**Role / Роль:** second, narrower facade. Второй фасад для администратора.

Dependencies: the same independent APIs, WorkflowRepository and ParkingFacade for central compensation. Nested `Snapshot` is a Java record: an immutable inspection result with id, workflow status, raw plate, A/B/C status strings, space, amount, deadline, failure and events. It avoids exposing internal repository references.

`inspect(id)` reloads workflow and all three subsystem records and combines them. Missing records use NONE, 0 or empty text. `inspectAll()` reads all workflow IDs then inspects each. `cancel(id)` requests the common compensation algorithm. `recoverPending()` filters PENDING_A/PENDING_B/PENDING_C/COMPENSATING records and resumes them. Terminal workflows are not reprocessed.

`simulateFullGarage(full)` persists blocked spaces {1,2,3}, or clears sensor blocks; active reservations remain unchanged. `setSensorsOnline`, `setGateOpen` change persisted settings through the relevant subsystem APIs. `overdueIds()` asks C for its calculated inspection result.

Второй фасад тоже выполняет реальную логику: inspect собирает состояние трёх подсистем, recoverPending решает, какие workflow нужно восстановить. Некоторые простые методы являются делегированием, что допустимо.

## Infrastructure and application

### RowStore

**Role / Роль:** interface for file rows. Абстракция хранилища.

Two methods: `read(table)` returns `List<String[]>`; `write(table, rows)` persists them. It has no domain-specific methods, statuses or references to a facade. Repositories depend on this interface rather than a hard-coded concrete file writer. Tests can wrap it to inject a realistic disk failure.

### CsvStore

**Role / Роль:** real RowStore implementation. Реальное CSV-хранилище.

The constructor creates the directory. `read(table)` resolves `<table>.csv`, reads UTF-8 lines and splits fields, retaining empty trailing fields. A missing table returns an empty list, which is a legitimate first-run state. No table is kept in a cached map.

`write(table, rows)` rejects comma/newline-containing fields, assembles lines, writes `<table>.tmp` through FileChannel, writes until the buffer is exhausted, calls `force(true)`, closes the file, then atomically moves it over the previous table. If atomic replacement is unavailable, it reports an error instead of pretending the save succeeded. IO failures become StorageException with the original cause.

Temporary files never act as authoritative records. `read` reads the .csv file only. A new save replaces an abandoned .tmp file. The guarantees are for ordinary process restart, not every possible power or filesystem failure.

### ParkingException

**Role / Роль:** custom unchecked domain exception. Собственное исключение бизнес-правил.

It `extends RuntimeException`; constructor takes Reason and message; `reason()` returns the reason. The nested enum distinguishes INVALID_INPUT, GATE_CLOSED, DUPLICATE_PLATE, NO_SPACE, SENSOR_OFFLINE, PAYMENT_DECLINED, ILLEGAL_TRANSITION, UNKNOWN_WORKFLOW and CONFLICT.

The message explains the problem; the enum lets code/tests classify it without parsing text. / Сообщение понятно человеку, а Reason удобно проверять в коде.

### StorageException

**Role / Роль:** custom unchecked persistence exception. Ошибка сохранения/чтения.

It extends RuntimeException and preserves a message and cause. It is separate from domain rejection because a storage failure may need restored access and a recovery retry rather than corrected user input.

### GarageApplication

**Role / Роль:** composition root and lifecycle. Создаёт объектный граф и удерживает блокировку файлов.

Constructor takes Path and Clock, creates CsvStore, opens `garage.lock`, and acquires an exclusive lock. A second application using the same folder fails visibly. It constructs each subsystem with its own repository, constructs WorkflowRepository, then the two facades.

`parking()` and `admin()` return the two ready facades. `close()` releases the lock and channel. It implements AutoCloseable, so try-with-resources closes it automatically. There is no cached domain state to flush when closing: every commit has already saved its CSV. Closing and creating a new application reconstructs objects from disk.

Это не третий фасад: класс соединяет зависимости при запуске, но не принимает решений о парковке и не выполняет A -> B -> C.

### Main

**Role / Роль:** real console client. Консоль, которую нужно запускать на защите.

`main(args)` selects the folder (data by default), opens GarageApplication and Scanner, then reads menu options. Customer commands call ParkingFacade; administrative commands call AdminFacade. Case 3 temporarily persists full sensor readings and restores those sensor readings in finally; case 4 supplies DECLINE. Cases 5/6 intentionally stop between stages. Cases 7/9 continue saved work. Case 8 cancels. Cases 10/11 set persisted availability. Case 12 inspects overstay IDs. Choice 0 exits without deleting data.

Private `booleanInput` accepts exactly true/false. `show(snapshot)` prints workflow ID, statuses, space, price, dueAt, failure and the saved event order. main catches number-format, domain and storage errors with readable output. It does not print a fake success when compensation fails.

## Test classes and build files

`ParkingScenario`: custom domain testing language. aGarage opens an isolated app; thatIsFull changes persisted sensor readings; parks calls the customer operation; thenOnlyTheGateIsRevoked and thenTheSpaceIsReleasedBeforeTheGateIsRevoked assert all final fields and undo order. It implements AutoCloseable. The shared CLOCK is fixed for repeatability.

`WorkflowTest`: success, both compensation depths, restart after A/B, stale B/C progress, invalid input, availability, administrative cancellation, recovery, saved overstay deadline, single-writer lock and failed rollback retry. Every test has its own @TempDir.

`SubsystemTest`: each subsystem is run directly without other domain subsystems or facades; it also checks entity transitions, pricing and plate boundaries. These tests are your independence demonstration.

`CompensationRecoveryTest`: additional crash windows and partially finished reverse operations, final-journal failure after C actually paid, duplicate plate and partial manual cancellation.

`StoreTest`: real file round-trip, CSV delimiter protection, and actual filesystem read/write/commit failures. No in-memory repository stands in for the restart requirement.

`pom.xml`: Java 17, JUnit 5, Maven compiler/test/JAR/exec plugins and JaCoCo. Tests generate HTML coverage and fail the build when any required package has less than 80% line coverage. `run.cmd`/`run.sh` launch the supplied JAR. `build.cmd`/`build.sh` rebuild source, run tests and replace the JAR in dist.

## Java concepts used

**Composition / Композиция:** a facade HAS references to subsystems; services HAVE a repository and business rule objects. Constructor injection makes dependencies visible. The UML associations show these references; the diagram does not claim exclusive ownership of the subsystems shared by both facades.

**Inheritance / Наследование:** the two custom exceptions extend RuntimeException. **Interface implementation / Реализация интерфейса:** CsvStore implements RowStore; GarageApplication and ParkingScenario implement AutoCloseable. These do not make the pattern Adapter or Bridge: the problem and intent determine the pattern.

**Enum / Перечисление:** named finite choices such as RESERVED or DECLINE. Better than arbitrary status strings because the compiler restricts valid choices.

**Optional / Возможное значение:** find(id) may return no record. isPresent, filter, map and ifPresent handle that absence deliberately. A missing B record is normal after B failed before committing.

**List and Set / Список и множество:** events are an ordered List; occupied/blocked spaces are Sets where membership matters. Streams filter, map and collect records. A lambda such as `r -> r.status() == RESERVED` describes the rule applied to each record.

**UUID:** unique operation identifier linking independent records. It is not a database foreign key; the facade coordinates records with the same ID.

**Idempotency / Идемпотентность:** repeating the same operation ID does not produce another permit, reservation, charge or refund. The entity transition methods remain strict; subsystem APIs check stored state before calling them.

**Compensation / Компенсация:** a new durable action reversing an earlier committed effect. Unlike one database transaction's rollback, the original records already exist on disk and remain in their reversed status.

**Least Knowledge / Минимум знаний:** Main uses the facade operations without traversing repositories or knowing the undo algorithm. The before/after example is in README.

## SOLID explained with this project

SRP: entities enforce transitions; policies calculate/validate; repositories serialize; the facade coordinates; Main handles input/output. / Каждый класс имеет связанную и понятную ответственность.

OCP: another RowStore implementation can be introduced through its interface without changing repository business callers. Pricing is in one policy class; this small version does not introduce speculative pricing hierarchies. / Новое хранилище можно подключить через интерфейс, а тариф не разбросан по фасадам.

LSP: CsvStore must honor RowStore's read/write contract, including durable successful writes. A fake implementation that claims to persist while keeping data only in memory would not satisfy the application's expectations.

ISP: RowStore has only the two operations repositories need. There is no huge interface forcing every subsystem to implement unrelated methods.

DIP: persistence gateways receive RowStore, an abstraction, and code receives dependencies in constructors. The facades deliberately receive the small concrete subsystem APIs; the project does not claim every dependency is abstracted. / DIP реализован прежде всего на границе хранилища; дополнительные интерфейсы для каждого сервиса здесь не нужны только ради количества классов.
