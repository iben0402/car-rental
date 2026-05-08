# Design: Vehicle Hierarchy & Domain Extension

**Date:** 2026-05-08
**Status:** Approved

---

## Overview

Extend the car rental service with a richer domain model: a `VehicleModel` catalog, a `@MappedSuperclass Vehicle` hierarchy with `Car` and `Motorcycle`, a full `Customer` entity, per-model pricing locked at booking time, and resolution of all known gaps from the README.

---

## Implementation Approach

Hybrid vertical/horizontal slices — vertical where features are independent, horizontal within the vehicle slice where domain coupling forces it:

1. **Customer slice** — entity → repo → service → API (fully independent)
2. **Vehicle hierarchy slice** — domain-first (`VehicleModel` + `Vehicle` + `Car` + `Motorcycle`), then repo → service → API together
3. **Pricing slice** — lock price at booking, compute `totalCostEur`, surface in responses
4. **Known gaps** — audit fields, seeding, soft-delete, reschedule endpoint, `COMPLETED` status, timezone, OpenAPI, pagination

---

## Section 1: Domain Model

### `VehicleModel` (new entity, table `vehicle_models`)

Single table covering both car and motorcycle models. Acts as the rentable catalog — "what you rent."

| Field | Type | Constraints |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `brand` | String(50) | not null |
| `model` | String(50) | not null |
| `vehicleType` | enum `VehicleType` | not null — CAR / MOTORCYCLE |
| `carType` | enum `CarType` (nullable) | set only when `vehicleType = CAR` |
| `motorcycleType` | enum `MotorcycleType` (nullable) | set only when `vehicleType = MOTORCYCLE` |
| `dailyPriceEur` | BigDecimal | not null, scale 2 |

### `@MappedSuperclass Vehicle` (new, no own table)

Shared physical fleet fields inherited by `Car` and `Motorcycle`.

| Field | Type | Constraints |
|---|---|---|
| `id` | Long | PK, auto-generated |
| `registrationNumber` | String(20) | not null, unique |
| `year` | int | not null |
| `colour` | String(30) | not null |
| `active` | boolean | not null, default true |
| `vehicleModel` | ManyToOne → `VehicleModel` | not null, LAZY fetch |

### `Car extends Vehicle` (table `cars`)

Adds one car-specific field.

| Field | Type |
|---|---|
| `numberOfDoors` | int |

### `Motorcycle extends Vehicle` (table `motorcycles`)

Adds one motorcycle-specific field.

| Field | Type |
|---|---|
| `engineCC` | int |

### `Customer` (new entity, table `customers`)

Full customer entity replacing the current free-text `customerName` on `Reservation`.

| Field | Type | Constraints |
|---|---|---|
| `id` | Long | PK |
| `firstName` | String(50) | not null |
| `lastName` | String(50) | not null |
| `email` | String(100) | not null, unique |
| `phone` | String(20) | not null |
| `driverLicenseNumber` | String(30) | not null, unique |
| `licenseExpiryDate` | LocalDate | not null |
| `address` | String(200) | not null |
| `dateOfBirth` | LocalDate | not null |
| `createdAt` | LocalDateTime | audit, set on insert |
| `updatedAt` | LocalDateTime | audit, set on update |

### `Reservation` (updated)

| Change | Detail |
|---|---|
| Remove `customerName: String` | replaced by `customer: ManyToOne Customer` |
| Add `motorcycle: ManyToOne Motorcycle` (nullable) | exactly one of `car` or `motorcycle` is non-null; invariant enforced in `ReservationService` |
| Add `pricePerDayEur: BigDecimal` | copied from `VehicleModel.dailyPriceEur` at booking time |
| Add `totalCostEur: BigDecimal` | computed: `pricePerDayEur × numberOfDays` |
| Add `createdAt`, `updatedAt`, `cancelledAt` | audit fields |

### Enums

- **Add** `VehicleType { CAR, MOTORCYCLE }`
- **Add** `MotorcycleType { SCOOTER, SPORT, CRUISER }`
- **Remove** `dailyPriceEur` from `CarType` — price moves to `VehicleModel`

---

## Section 2: Repository & Service Layer

### New repositories

| Repository | Key methods |
|---|---|
| `VehicleModelRepository` | `findByVehicleType`, `findByCarType`, `findByMotorcycleType` |
| `MotorcycleRepository` | availability overlap query (mirrors `CarRepository`) |
| `CustomerRepository` | `findByEmail`, `findByDriverLicenseNumber` |

### Updated repositories

- `CarRepository` — availability query filters via `vehicleModel.carType` instead of `car.carType`
- `ReservationRepository` — add `findByCustomer(Customer)`, expose `findById`

### New services

**`CustomerService`**
- `createCustomer(request)` — validates email + license uniqueness, saves, returns `CustomerResponse`
- `getCustomer(id)` — throws `CustomerNotFoundException` if absent
- `getCustomerReservations(customerId)` — returns all reservations for a customer

**`VehicleModelService`**
- `listModels(vehicleType)` — filterable catalog
- `getModel(id)` — throws `VehicleModelNotFoundException` if absent

### Updated `ReservationService`

- `reserve(vehicleModelId, customerId, startDateTime, numberOfDays)` — replaces `(carType, customerName, ...)`. Looks up model and customer, finds an available vehicle, copies `pricePerDayEur`, computes `totalCostEur = pricePerDayEur × numberOfDays` (BigDecimal, HALF_UP, scale 2)
- `cancel(id)` — sets `cancelledAt` audit field
- `reschedule(id, newStart, days)` — now exposed via HTTP; recomputes `totalCostEur`
- `completeExpiredReservations()` — called by nightly `@Scheduled` job; transitions `ACTIVE` reservations past `endDateTime` to `COMPLETED`

### Concurrency fix

Add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to the availability query in both `CarRepository` and `MotorcycleRepository`. Re-enable the `@Disabled` concurrency test in `ReservationFlowIntegrationTest`.

### Pricing rule

`totalCostEur = pricePerDayEur.multiply(BigDecimal.valueOf(numberOfDays)).setScale(2, RoundingMode.HALF_UP)`

---

## Section 3: API Layer

### New endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/customers` | Create a customer |
| `GET` | `/api/customers/{id}` | Get customer by id |
| `GET` | `/api/customers/{id}/reservations` | List reservations for a customer |
| `GET` | `/api/vehicle-models` | List models, optional `?vehicleType=CAR` |
| `GET` | `/api/vehicle-models/{id}` | Get a single model |
| `GET` | `/api/reservations/{id}` | Get reservation by id |
| `PATCH` | `/api/reservations/{id}/reschedule` | Reschedule a reservation |
| `DELETE` | `/api/cars/{id}` | Soft-delete (set active=false) |
| `DELETE` | `/api/motorcycles/{id}` | Soft-delete (set active=false) |

### Updated endpoints

**`POST /api/reservations`** — new request shape:
```json
{
  "vehicleModelId": 1,
  "customerId": 1,
  "startDateTime": "2026-06-01T10:00:00Z",
  "numberOfDays": 3
}
```

**`GET /api/reservations/availability`** — query param changes from `carType` to `vehicleModelId`

All reservation responses gain `pricePerDayEur` and `totalCostEur` fields.

### New/updated DTOs

| DTO | Purpose |
|---|---|
| `CreateCustomerRequest` | `@NotBlank`, `@Email`, `@Future` on `licenseExpiryDate` |
| `CustomerResponse` | all fields; used for both `POST /customers` response and `GET /customers/{id}` (no customer list endpoint) |
| `CreateVehicleModelRequest` / `VehicleModelResponse` | model catalog DTOs |
| `RescheduleReservationRequest` | `newStartDateTime`, `numberOfDays` |
| Updated `CreateReservationRequest` | `vehicleModelId`, `customerId` replacing `carType`, `customerName` |
| Updated `ReservationResponse` | adds `pricePerDayEur`, `totalCostEur` |

### New exceptions → HTTP mappings

| Exception | Status |
|---|---|
| `CustomerNotFoundException` | 404 |
| `VehicleModelNotFoundException` | 404 |
| `NoCarAvailableException` renamed → `NoVehicleAvailableException` | 409 |

---

## Section 4: Cross-cutting Gaps

### Audit fields
Use `@CreatedDate` / `@LastModifiedDate` with `@EntityListeners(AuditingEntityListener.class)` on `Reservation` and `Customer`. Enable with `@EnableJpaAuditing` on `CarRentalApplication`.

### Data seeding
`DataSeeder` component annotated `@Component` with `@EventListener(ApplicationReadyEvent.class)` inserts representative `VehicleModel`, `Car`, and `Motorcycle` rows on startup. Replaces the current manual H2 console step.

### Soft-delete for vehicles
`Car` and `Motorcycle` already have `active` flag. Retiring a vehicle sets `active = false` — no row deletion, FK integrity on historical reservations preserved. Exposed via `DELETE /api/cars/{id}` and `DELETE /api/motorcycles/{id}`.

### `COMPLETED` status transition
`@Scheduled(cron = "0 0 1 * * *")` nightly job calls `completeExpiredReservations()` which bulk-updates `ACTIVE` reservations where `endDateTime < now()` to `COMPLETED`. Enable with `@EnableScheduling` on main class.

### Timezone fix
Replace `LocalDateTime` with `Instant` (stored as UTC) on `Reservation.startDateTime` / `endDateTime`. Accept and return `OffsetDateTime` at the API boundary; convert to `Instant` in the service layer.

### OpenAPI / Swagger
Add `springdoc-openapi-starter-webmvc-ui` to `pom.xml`. Swagger UI auto-generated at `/swagger-ui.html`.

### Pagination
`GET /api/reservations/availability` and `GET /api/vehicle-models` accept `Pageable` params (`page`, `size`, `sort`). Responses wrapped in `Page<T>`.

---

## Entity Relationship Summary

```
VehicleModel ──< Car          (one model → many physical cars)
VehicleModel ──< Motorcycle   (one model → many physical motorcycles)
Customer     ──< Reservation  (one customer → many reservations)
Reservation  >── Car          (nullable: set when vehicle is a car)
Reservation  >── Motorcycle   (nullable: set when vehicle is a motorcycle)
```

---

## Out of Scope

- Authentication / authorization
- Rate limiting
- Multi-instance distributed locking (single-JVM pessimistic lock is sufficient for this stage)
- Load testing
- Postgres migration (H2 stays for now)
