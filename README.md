# Car Rental Service

A small Spring Boot REST API for renting cars by type (sedan / SUV / van) over a date window.

---

## Tech stack

- **Java 17**, **Spring Boot 3.5**
- **Spring Web** (REST), **Spring Data JPA** (persistence), **Spring Validation** (Jakarta Bean Validation)
- **H2** in-memory database (rebuilt on every restart)
- **Lombok** for boilerplate reduction
- **JUnit 5**, **Mockito**, **AssertJ**, **MockMvc** for testing

---

## How to run

```bash
./mvnw spring-boot:run
```

The service listens on `http://localhost:8080`.

H2 console is available at `http://localhost:8080/h2-console`. JDBC URL: `jdbc:h2:mem:carrental`, user `sa`, blank password. The database is wiped on every restart (`spring.jpa.hibernate.ddl-auto: create-drop`).

### Run the tests

```bash
./mvnw test
```

Currently 30 tests across four suites: service (Mockito, no Spring context), repository (`@DataJpaTest` against H2), end-to-end (`@SpringBootTest` + MockMvc), and the smoke `contextLoads`.

---

## Seeding data

There's no seed loader, so before exercising the endpoints you need at least one row in `cars`. The simplest path: open the H2 console and run

```sql
INSERT INTO cars (car_type, registration_number, active) VALUES ('SEDAN', 'ABC-1234', TRUE);
INSERT INTO cars (car_type, registration_number, active) VALUES ('SEDAN', 'ABC-5678', TRUE);
INSERT INTO cars (car_type, registration_number, active) VALUES ('SUV',   'XYZ-0001', TRUE);
INSERT INTO cars (car_type, registration_number, active) VALUES ('VAN',   'V-9999',   TRUE);
```

(See **Known gaps** - automated seeding is a missing piece.)

---

## Endpoints

### `POST /api/reservations` - create a reservation

```bash
curl -i -X POST http://localhost:8080/api/reservations \
  -H 'Content-Type: application/json' \
  -d '{
    "carType": "SEDAN",
    "startDateTime": "2026-06-01T10:00:00",
    "numberOfDays": 3,
    "customerName": "Alice"
  }'
```

`201 Created` with a `Location` header pointing at the new reservation, and a body shaped like:

```json
{
  "id": 1,
  "carId": 1,
  "carType": "SEDAN",
  "registrationNumber": "ABC-1234",
  "customerName": "Alice",
  "startDateTime": "2026-06-01T10:00:00",
  "endDateTime": "2026-06-04T10:00:00",
  "status": "ACTIVE"
}
```

### `GET /api/reservations/availability` - list cars free in a window

```bash
curl "http://localhost:8080/api/reservations/availability?carType=SEDAN&startDateTime=2026-06-01T10:00:00&numberOfDays=3"
```

`200 OK` with an array of available cars.

### `DELETE /api/reservations/{id}` - cancel

```bash
curl -i -X DELETE http://localhost:8080/api/reservations/1
```

`204 No Content` on success. Cancelling an already-cancelled reservation returns `409 Conflict` (idempotent semantics deliberately not chosen - see **Known gaps**).

### Error responses

All errors return JSON shaped like:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "numberOfDays: must be greater than or equal to 1",
  "timestamp": "2026-06-01T10:00:00"
}
```

| Status | Triggered by |
|--------|--------------|
| `400 Bad Request` | DTO validation failure, unknown enum value (e.g. `carType=FERRARI`), past start date |
| `404 Not Found` | Cancelling a non-existent reservation |
| `409 Conflict`  | All cars of the requested type are booked, or double-cancel |

---

## Architecture

```
                 ┌──────────────────────────────┐
HTTP request ───►│  ReservationController       │     /api/reservations
                 │  - @Validated, @Valid bodies │
                 └──────────────┬───────────────┘
                                │ DTOs (records)
                                ▼
                 ┌──────────────────────────────┐
                 │  ReservationService          │
                 │  - @Transactional            │
                 │  - orchestrates availability │
                 │    check + persist           │
                 └────────┬───────────────┬─────┘
                          │               │
                          ▼               ▼
            ┌──────────────────┐   ┌──────────────────────┐
            │  CarRepository   │   │ ReservationRepository│
            │  (Spring Data)   │   │ (Spring Data + JPQL) │
            └────────┬─────────┘   └──────────┬───────────┘
                     │                        │
                     ▼                        ▼
            ┌────────────────────────────────────────┐
            │  H2 (in-memory) - cars / reservations  │
            │  composite index on                    │
            │  (car_id, start_date_time, end_date_time) │
            └────────────────────────────────────────┘

   ┌──────────────────────────────┐
   │  GlobalExceptionHandler      │ ← maps domain exceptions
   │  (@RestControllerAdvice)     │   to HTTP statuses + ErrorResponse
   └──────────────────────────────┘
```

### Layer responsibilities

- **Controller**: HTTP shape, validation entry point, status codes, `Location` headers. No business logic.
- **DTOs (records)**: request/response bodies, decoupled from entities. Static `from(entity)` factories on responses.
- **Service**: transaction boundaries, orchestrates repository calls, applies domain rules (window must be in the future, ≥ 1 day).
- **Repository**: Spring Data interfaces. The reservation overlap query uses half-open interval logic: `existing.start < new.end AND existing.end > new.start`.
- **Entities**: rich domain methods (`Reservation.cancel()`, `Reservation.reschedule()`) keep state-transition rules with the data.
- **Exception handler**: single place that converts domain exceptions to JSON error responses.

---

## Known gaps

This list is deliberate - the exercise is partly about **knowing what's missing**, not building everything.

### Concurrency
- `ReservationService.reserve()` has a TOCTOU race between the booked-cars query and the `save()`. Two concurrent requests for the last car of a type can both succeed. The fix is `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the candidate car (or a Postgres exclusion constraint), neither implemented yet. See `ReservationFlowIntegrationTest.concurrent_bookings_onlyOneSucceeds_whenSingleCarAvailable` - written and `@Disabled` to demonstrate the race exists and where the fix should be verified.
- Concurrency is tested in a single JVM. A multi-instance deployment would need a distributed lock (Redis / Postgres advisory) or move the invariant into a DB constraint.

### Domain
- **No customer entity** - `customerName` is a free-text field. A real system would link to a `Customer` with id, contact info, license details, and history.
- **No pricing** - `CarType` has a `dailyPriceEur` field but it's never read by the service. Total cost is not computed or returned.
- **No "return early" / "extend"** flows. `Reservation.reschedule()` exists on the entity but isn't exposed via HTTP.
- **No COMPLETED status transition** - the enum has it, but nothing automatically moves an `ACTIVE` reservation to `COMPLETED` once the end date passes.
- **Cancellation is non-idempotent** by design (returns 409 on double-cancel). REST purists would argue DELETE should be idempotent → 204 in both cases. Decision deferred until product clarifies.

### Data
- **In-memory H2 only.** Real deployment needs Postgres/MySQL with a managed connection pool, migrations (Flyway/Liquibase), and backups.
- **No data seeding.** Cars must be inserted manually via the H2 console.
- **No audit fields** (`createdAt`, `updatedAt`, `cancelledAt`). For a booking system these are essential - drop in `@CreatedDate`/`@LastModifiedDate` with `@EntityListeners(AuditingEntityListener.class)` and `@EnableJpaAuditing`.
- **No soft-delete** for cars. Removing a car retired from the fleet would FK-cascade-fail against historical reservations.

### API surface
- **No pagination** on any list endpoint. `GET /availability` returns the full result set; with thousands of cars this won't scale. Switch to `Pageable` + `Page<CarSummaryResponse>`.
- **No filtering / sorting** on availability beyond carType.
- **No GET /reservations/{id}** - once created, you can't read a reservation back through the API (only via H2 console).
- **No list of a customer's reservations** - would need the customer entity first.
- **OpenAPI / Swagger documentation missing.** A `springdoc-openapi-starter-webmvc-ui` dependency would generate `/swagger-ui.html` from existing annotations.

### Security
- **No authentication.** Anyone reachable on the network can create or cancel any reservation.
- **No authorization.** No notion of "this customer can only cancel their own reservation."
- **No rate limiting** at the application or gateway layer.
- **No HTTPS** in the dev profile (would be terminated by the deployment platform in prod).

### Operability
- **No observability.** No metrics (Micrometer + Prometheus), no structured logging, no distributed tracing. `actuator` not on the classpath.
- **No health checks** beyond what Spring Boot provides by default - and `actuator` isn't enabled, so even those aren't exposed.
- **No CI configuration** committed.

### Time zones
- All timestamps are stored as `LocalDateTime` (no zone). Fine for a single-region rental shop; broken the moment a customer in Berlin books in Tokyo. The interview-friendly fix: store `Instant` (UTC) in the DB, accept/return `OffsetDateTime` at the API edge, convert at the boundary.

### Testing
- Integration tests share a single H2 instance and rely on `@BeforeEach deleteAll()` for isolation. `@DirtiesContext` would be cleaner but slower; trade-off accepted.
- The concurrency test is `@Disabled` until the lock is added.
- No load tests (Gatling / JMeter) - overlap-query performance under realistic data volume is unverified.

### Code quality
- Service-layer validation (`numberOfDays < 1`, `startDateTime.isBefore(now)`) duplicates DTO `@Min` / `@Future`. Defensible (defense in depth) but worth a discussion: where is the canonical validation boundary?
- `Reservation.reschedule()` calls `LocalDateTime.now()` directly, coupling the entity to the system clock. Inject a `Clock` or move the check up to the service.
- `findOverlappingForCar` query is defined but currently unused - it's the missing piece for the locking fix described above.
