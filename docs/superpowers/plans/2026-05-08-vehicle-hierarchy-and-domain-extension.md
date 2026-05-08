# Vehicle Hierarchy & Domain Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the car rental service with a `VehicleModel` catalog, `Car`/`Motorcycle` hierarchy, full `Customer` entity, per-model pricing locked at booking, and resolution of all README known gaps.

**Architecture:** Four slices — Customer (independent vertical), Vehicle Hierarchy (domain-first then repo/service/API), Reservation Update (touches existing entity/service/controller together), Known Gaps (audit, seeding, scheduling, soft-delete, OpenAPI, pagination). Each slice leaves the app compilable and tests green.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA, H2, Lombok `@SuperBuilder` for the entity hierarchy, `springdoc-openapi-starter-webmvc-ui` for Swagger.

---

## File Map

**New files**
- `src/main/java/com/iwonabendig/car_rental/entity/Vehicle.java` — `@MappedSuperclass` with shared fleet fields
- `src/main/java/com/iwonabendig/car_rental/entity/Motorcycle.java` — concrete fleet entity
- `src/main/java/com/iwonabendig/car_rental/entity/VehicleModel.java` — rentable catalog entry
- `src/main/java/com/iwonabendig/car_rental/entity/Customer.java` — full customer entity
- `src/main/java/com/iwonabendig/car_rental/enums/VehicleType.java`
- `src/main/java/com/iwonabendig/car_rental/enums/MotorcycleType.java`
- `src/main/java/com/iwonabendig/car_rental/repository/VehicleModelRepository.java`
- `src/main/java/com/iwonabendig/car_rental/repository/MotorcycleRepository.java`
- `src/main/java/com/iwonabendig/car_rental/repository/CustomerRepository.java`
- `src/main/java/com/iwonabendig/car_rental/service/CustomerService.java`
- `src/main/java/com/iwonabendig/car_rental/service/VehicleModelService.java`
- `src/main/java/com/iwonabendig/car_rental/controller/CustomerController.java`
- `src/main/java/com/iwonabendig/car_rental/controller/VehicleModelController.java`
- `src/main/java/com/iwonabendig/car_rental/controller/VehicleController.java`
- `src/main/java/com/iwonabendig/car_rental/dto/CreateCustomerRequest.java`
- `src/main/java/com/iwonabendig/car_rental/dto/CustomerResponse.java`
- `src/main/java/com/iwonabendig/car_rental/dto/CreateVehicleModelRequest.java`
- `src/main/java/com/iwonabendig/car_rental/dto/VehicleModelResponse.java`
- `src/main/java/com/iwonabendig/car_rental/dto/RescheduleReservationRequest.java`
- `src/main/java/com/iwonabendig/car_rental/exceptions/CustomerNotFoundException.java`
- `src/main/java/com/iwonabendig/car_rental/exceptions/VehicleModelNotFoundException.java`
- `src/main/java/com/iwonabendig/car_rental/exceptions/NoVehicleAvailableException.java`
- `src/main/java/com/iwonabendig/car_rental/DataSeeder.java`
- `src/test/java/com/iwonabendig/car_rental/repository/CustomerRepositoryTest.java`
- `src/test/java/com/iwonabendig/car_rental/service/CustomerServiceTest.java`
- `src/test/java/com/iwonabendig/car_rental/service/VehicleModelServiceTest.java`

**Modified files**
- `src/main/java/com/iwonabendig/car_rental/CarRentalApplication.java` — add `@EnableJpaAuditing`, `@EnableScheduling`
- `src/main/java/com/iwonabendig/car_rental/entity/Car.java` — extend `Vehicle`, swap `@Builder` → `@SuperBuilder`, remove fields moved to `Vehicle`
- `src/main/java/com/iwonabendig/car_rental/entity/Reservation.java` — replace `customerName` with `Customer` FK, add `motorcycle` FK, pricing fields, audit fields
- `src/main/java/com/iwonabendig/car_rental/enums/CarType.java` — remove `dailyPriceEur`
- `src/main/java/com/iwonabendig/car_rental/repository/CarRepository.java` — update queries to navigate via `vehicleModel`
- `src/main/java/com/iwonabendig/car_rental/repository/ReservationRepository.java` — update JPQL, add new queries
- `src/main/java/com/iwonabendig/car_rental/service/ReservationService.java` — new signature, pricing, reschedule, scheduling
- `src/main/java/com/iwonabendig/car_rental/controller/ReservationController.java` — new endpoints, updated params
- `src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java` — new exception handlers
- `src/main/java/com/iwonabendig/car_rental/dto/CreateReservationRequest.java` — `vehicleModelId` + `customerId`
- `src/main/java/com/iwonabendig/car_rental/dto/ReservationResponse.java` — pricing fields, customer info
- `src/main/java/com/iwonabendig/car_rental/dto/CarSummaryResponse.java` — add brand/model from `VehicleModel`
- `pom.xml` — add `springdoc-openapi-starter-webmvc-ui`
- `src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java` — rewrite for new signatures
- `src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java` — rewrite for new API shape

---

## Slice 1 — Customer

### Task 1: Customer entity + `@EnableJpaAuditing` + CustomerRepository

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/entity/Customer.java`
- Create: `src/main/java/com/iwonabendig/car_rental/repository/CustomerRepository.java`
- Create: `src/test/java/com/iwonabendig/car_rental/repository/CustomerRepositoryTest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/CarRentalApplication.java`

- [ ] **Write the failing repository test**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomerRepositoryTest {

    @Autowired CustomerRepository customerRepository;

    private Customer buildCustomer(String email, String license) {
        return Customer.builder()
                .firstName("Alice").lastName("Smith")
                .email(email).phone("+48123456789")
                .driverLicenseNumber(license)
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .address("123 Main St").dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();
    }

    @Test
    void saveAndFindByEmail() {
        customerRepository.save(buildCustomer("alice@test.com", "DL-001"));
        assertThat(customerRepository.findByEmail("alice@test.com")).isPresent();
        assertThat(customerRepository.findByEmail("other@test.com")).isEmpty();
    }

    @Test
    void saveAndFindByDriverLicense() {
        customerRepository.save(buildCustomer("bob@test.com", "DL-002"));
        assertThat(customerRepository.findByDriverLicenseNumber("DL-002")).isPresent();
    }

    @Test
    void existsByEmail_returnsTrue_whenEmailExists() {
        customerRepository.save(buildCustomer("carol@test.com", "DL-003"));
        assertThat(customerRepository.existsByEmail("carol@test.com")).isTrue();
        assertThat(customerRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    void existsByDriverLicenseNumber_returnsTrue_whenLicenseExists() {
        customerRepository.save(buildCustomer("dan@test.com", "DL-004"));
        assertThat(customerRepository.existsByDriverLicenseNumber("DL-004")).isTrue();
    }
}
```

- [ ] **Run test — expect compile failure** (Customer class doesn't exist yet)

```bash
./mvnw test -pl . -Dtest=CustomerRepositoryTest
```

- [ ] **Create `Customer.java`**

```java
package com.iwonabendig.car_rental.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String firstName;

    @NotBlank @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String lastName;

    @NotBlank @Email @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String phone;

    @NotBlank @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String driverLicenseNumber;

    @NotNull
    @Column(nullable = false)
    private LocalDate licenseExpiryDate;

    @NotBlank @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String address;

    @NotNull
    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Create `CustomerRepository.java`**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByDriverLicenseNumber(String driverLicenseNumber);
    boolean existsByEmail(String email);
    boolean existsByDriverLicenseNumber(String driverLicenseNumber);
}
```

- [ ] **Add `@EnableJpaAuditing` to `CarRentalApplication.java`**

```java
package com.iwonabendig.car_rental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CarRentalApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarRentalApplication.class, args);
    }
}
```

- [ ] **Run tests — expect green**

```bash
./mvnw test -Dtest=CustomerRepositoryTest
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/entity/Customer.java \
        src/main/java/com/iwonabendig/car_rental/repository/CustomerRepository.java \
        src/main/java/com/iwonabendig/car_rental/CarRentalApplication.java \
        src/test/java/com/iwonabendig/car_rental/repository/CustomerRepositoryTest.java
git commit -m "feat: add Customer entity, repository, and enable JPA auditing"
```

---

### Task 2: CustomerService + CustomerNotFoundException

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/exceptions/CustomerNotFoundException.java`
- Create: `src/main/java/com/iwonabendig/car_rental/service/CustomerService.java`
- Create: `src/test/java/com/iwonabendig/car_rental/service/CustomerServiceTest.java`

- [ ] **Write the failing service test**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CreateCustomerRequest;
import com.iwonabendig.car_rental.dto.CustomerResponse;
import com.iwonabendig.car_rental.entity.Customer;
import com.iwonabendig.car_rental.exceptions.CustomerNotFoundException;
import com.iwonabendig.car_rental.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @InjectMocks CustomerService customerService;

    private CreateCustomerRequest buildRequest(String email, String license) {
        return new CreateCustomerRequest(
                "Alice", "Smith", email, "+48123456789", license,
                LocalDate.of(2028, 1, 1), "123 Main St", LocalDate.of(1990, 5, 15));
    }

    @Test
    void createCustomer_savesAndReturnsResponse() {
        when(customerRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(customerRepository.existsByDriverLicenseNumber("DL-001")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            return Customer.builder()
                    .firstName(c.getFirstName()).lastName(c.getLastName())
                    .email(c.getEmail()).phone(c.getPhone())
                    .driverLicenseNumber(c.getDriverLicenseNumber())
                    .licenseExpiryDate(c.getLicenseExpiryDate())
                    .address(c.getAddress()).dateOfBirth(c.getDateOfBirth())
                    .build();
        });

        CustomerResponse response = customerService.createCustomer(buildRequest("alice@test.com", "DL-001"));

        assertThat(response.email()).isEqualTo("alice@test.com");
        assertThat(response.firstName()).isEqualTo("Alice");
    }

    @Test
    void createCustomer_throwsWhenEmailAlreadyExists() {
        when(customerRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(buildRequest("alice@test.com", "DL-001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void createCustomer_throwsWhenLicenseAlreadyExists() {
        when(customerRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(customerRepository.existsByDriverLicenseNumber("DL-001")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(buildRequest("alice@test.com", "DL-001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Driver license already registered");
    }

    @Test
    void getCustomer_throwsWhenNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomer(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
```

- [ ] **Run test — expect compile failure**

```bash
./mvnw test -Dtest=CustomerServiceTest
```

- [ ] **Create `CustomerNotFoundException.java`**

```java
package com.iwonabendig.car_rental.exceptions;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Customer not found with id: " + id);
    }
}
```

- [ ] **Create DTOs needed by the service — `CreateCustomerRequest.java`**

```java
package com.iwonabendig.car_rental.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 30) String driverLicenseNumber,
        @NotNull @Future LocalDate licenseExpiryDate,
        @NotBlank @Size(max = 200) String address,
        @NotNull @Past LocalDate dateOfBirth
) {}
```

- [ ] **Create `CustomerResponse.java`**

```java
package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String driverLicenseNumber,
        LocalDate licenseExpiryDate,
        String address,
        LocalDate dateOfBirth,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), c.getDriverLicenseNumber(), c.getLicenseExpiryDate(),
                c.getAddress(), c.getDateOfBirth(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
```

- [ ] **Create `CustomerService.java`**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CreateCustomerRequest;
import com.iwonabendig.car_rental.dto.CustomerResponse;
import com.iwonabendig.car_rental.entity.Customer;
import com.iwonabendig.car_rental.exceptions.CustomerNotFoundException;
import com.iwonabendig.car_rental.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        if (customerRepository.existsByEmail(request.email()))
            throw new IllegalArgumentException("Email already in use: " + request.email());
        if (customerRepository.existsByDriverLicenseNumber(request.driverLicenseNumber()))
            throw new IllegalArgumentException("Driver license already registered: " + request.driverLicenseNumber());
        Customer customer = Customer.builder()
                .firstName(request.firstName()).lastName(request.lastName())
                .email(request.email()).phone(request.phone())
                .driverLicenseNumber(request.driverLicenseNumber())
                .licenseExpiryDate(request.licenseExpiryDate())
                .address(request.address()).dateOfBirth(request.dateOfBirth())
                .build();
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id) {
        return CustomerResponse.from(customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id)));
    }
}
```

- [ ] **Run tests — expect green**

```bash
./mvnw test -Dtest=CustomerServiceTest
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/exceptions/CustomerNotFoundException.java \
        src/main/java/com/iwonabendig/car_rental/service/CustomerService.java \
        src/main/java/com/iwonabendig/car_rental/dto/CreateCustomerRequest.java \
        src/main/java/com/iwonabendig/car_rental/dto/CustomerResponse.java \
        src/test/java/com/iwonabendig/car_rental/service/CustomerServiceTest.java
git commit -m "feat: add CustomerService and DTOs"
```

---

### Task 3: CustomerController + GlobalExceptionHandler update

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/controller/CustomerController.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java`

- [ ] **Create `CustomerController.java`**

```java
package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.dto.CreateCustomerRequest;
import com.iwonabendig.car_rental.dto.CustomerResponse;
import com.iwonabendig.car_rental.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/customers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.getCustomer(id);
    }
}
```

- [ ] **Add `CustomerNotFoundException` handler to `GlobalExceptionHandler.java`**

Add this method inside the `GlobalExceptionHandler` class (after the existing `handleNotFound` method):

```java
@ExceptionHandler(CustomerNotFoundException.class)
public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex) {
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
}
```

Also add the import at the top:
```java
import com.iwonabendig.car_rental.exceptions.CustomerNotFoundException;
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/controller/CustomerController.java \
        src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java
git commit -m "feat: add CustomerController and exception handler"
```

---

## Slice 2 — Vehicle Hierarchy

### Task 4: New enums + update CarType

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/enums/VehicleType.java`
- Create: `src/main/java/com/iwonabendig/car_rental/enums/MotorcycleType.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/enums/CarType.java`

- [ ] **Create `VehicleType.java`**

```java
package com.iwonabendig.car_rental.enums;

public enum VehicleType {
    CAR,
    MOTORCYCLE
}
```

- [ ] **Create `MotorcycleType.java`**

```java
package com.iwonabendig.car_rental.enums;

public enum MotorcycleType {
    SCOOTER,
    SPORT,
    CRUISER
}
```

- [ ] **Remove `dailyPriceEur` from `CarType.java`** — price moves to `VehicleModel`

Replace the entire file:

```java
package com.iwonabendig.car_rental.enums;

public enum CarType {
    SEDAN,
    SUV,
    VAN
}
```

- [ ] **Run all tests — expect green** (the field was unused by the service)

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/enums/VehicleType.java \
        src/main/java/com/iwonabendig/car_rental/enums/MotorcycleType.java \
        src/main/java/com/iwonabendig/car_rental/enums/CarType.java
git commit -m "feat: add VehicleType and MotorcycleType enums, remove dailyPriceEur from CarType"
```

---

### Task 5: VehicleModel entity + VehicleModelRepository

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/entity/VehicleModel.java`
- Create: `src/main/java/com/iwonabendig/car_rental/repository/VehicleModelRepository.java`

- [ ] **Create `VehicleModel.java`**

```java
package com.iwonabendig.car_rental.entity;

import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import com.iwonabendig.car_rental.enums.VehicleType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vehicle_models")
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String brand;

    @NotBlank @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String model;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column
    private CarType carType;

    @Enumerated(EnumType.STRING)
    @Column
    private MotorcycleType motorcycleType;

    @NotNull @Positive
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyPriceEur;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleModel other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Create `VehicleModelRepository.java`**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.VehicleModel;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import com.iwonabendig.car_rental.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {
    List<VehicleModel> findByVehicleType(VehicleType vehicleType);
    List<VehicleModel> findByCarType(CarType carType);
    List<VehicleModel> findByMotorcycleType(MotorcycleType motorcycleType);
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/entity/VehicleModel.java \
        src/main/java/com/iwonabendig/car_rental/repository/VehicleModelRepository.java
git commit -m "feat: add VehicleModel entity and repository"
```

---

### Task 6: `Vehicle` `@MappedSuperclass` + refactor `Car` + new `Motorcycle`

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/entity/Vehicle.java`
- Create: `src/main/java/com/iwonabendig/car_rental/entity/Motorcycle.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/entity/Car.java`

> **Note:** `Car` currently uses `@Builder`. The hierarchy requires switching to `@SuperBuilder` on both `Vehicle` and `Car`. `@Builder` and `@SuperBuilder` cannot be mixed on the same inheritance chain — remove `@Builder` from `Car` entirely and replace with `@SuperBuilder`.

- [ ] **Create `Vehicle.java`**

```java
package com.iwonabendig.car_rental.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_model_id", nullable = false)
    private VehicleModel vehicleModel;

    @NotBlank @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String registrationNumber;

    @Column(nullable = false)
    private int year;

    @NotBlank @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String colour;

    @Column(nullable = false)
    private boolean active;

    public void retire() {
        this.active = false;
    }
}
```

- [ ] **Replace `Car.java` entirely**

```java
package com.iwonabendig.car_rental.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Entity
@Table(name = "cars")
public class Car extends Vehicle {

    @Column
    private Integer numberOfDoors;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Car other)) return false;
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Create `Motorcycle.java`**

```java
package com.iwonabendig.car_rental.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@Entity
@Table(name = "motorcycles")
public class Motorcycle extends Vehicle {

    @Column
    private Integer engineCC;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Motorcycle other)) return false;
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

- [ ] **Fix the existing `ReservationServiceTest` — `Car.builder()` calls break because `Car` no longer has `carType`, `registrationNumber`, `active` directly**

Replace the `activeCar` helper and all builder calls in `ReservationServiceTest.java`. The test helpers can no longer pass `carType` to `Car.builder()` — instead they should set `vehicleModel`. But since the Mockito service test mocks the repository, we can build a minimal `VehicleModel` inline.

Replace the file `src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java` with a placeholder that just passes. The full rewrite happens in Task 11 after the Reservation entity is updated. For now, stub out the class so the build compiles:

```java
package com.iwonabendig.car_rental.service;

import org.junit.jupiter.api.Test;

class ReservationServiceTest {
    @Test void placeholder() {}
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/entity/Vehicle.java \
        src/main/java/com/iwonabendig/car_rental/entity/Car.java \
        src/main/java/com/iwonabendig/car_rental/entity/Motorcycle.java \
        src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java
git commit -m "feat: add Vehicle superclass, refactor Car to extend it, add Motorcycle entity"
```

---

### Task 7: Update `CarRepository` + new `MotorcycleRepository`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/repository/CarRepository.java`
- Create: `src/main/java/com/iwonabendig/car_rental/repository/MotorcycleRepository.java`

> `Car` no longer has a `carType` field directly — it navigates via `vehicleModel.carType`. Derived query names and JPQL must reflect this.

- [ ] **Replace `CarRepository.java` entirely**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.enums.CarType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {

    List<Car> findByVehicleModel_CarTypeAndActiveTrue(CarType carType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Car c WHERE c.vehicleModel.carType = :carType AND c.active = true")
    List<Car> findByCarTypeActiveLocked(@Param("carType") CarType carType);
}
```

- [ ] **Create `MotorcycleRepository.java`**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Motorcycle;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotorcycleRepository extends JpaRepository<Motorcycle, Long> {

    List<Motorcycle> findByVehicleModel_MotorcycleTypeAndActiveTrue(MotorcycleType motorcycleType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Motorcycle m WHERE m.vehicleModel.motorcycleType = :motorcycleType AND m.active = true")
    List<Motorcycle> findByMotorcycleTypeActiveLocked(@Param("motorcycleType") MotorcycleType motorcycleType);
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/repository/CarRepository.java \
        src/main/java/com/iwonabendig/car_rental/repository/MotorcycleRepository.java
git commit -m "feat: update CarRepository queries for Vehicle hierarchy, add MotorcycleRepository"
```

---

### Task 8: VehicleModelService + VehicleModelController + DTOs

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/exceptions/VehicleModelNotFoundException.java`
- Create: `src/main/java/com/iwonabendig/car_rental/dto/CreateVehicleModelRequest.java`
- Create: `src/main/java/com/iwonabendig/car_rental/dto/VehicleModelResponse.java`
- Create: `src/main/java/com/iwonabendig/car_rental/service/VehicleModelService.java`
- Create: `src/main/java/com/iwonabendig/car_rental/controller/VehicleModelController.java`
- Create: `src/test/java/com/iwonabendig/car_rental/service/VehicleModelServiceTest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java`

- [ ] **Write the failing service test**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CreateVehicleModelRequest;
import com.iwonabendig.car_rental.dto.VehicleModelResponse;
import com.iwonabendig.car_rental.entity.VehicleModel;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.exceptions.VehicleModelNotFoundException;
import com.iwonabendig.car_rental.repository.VehicleModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    @Mock VehicleModelRepository vehicleModelRepository;
    @InjectMocks VehicleModelService vehicleModelService;

    private VehicleModel camry() {
        return VehicleModel.builder()
                .brand("Toyota").model("Camry")
                .vehicleType(VehicleType.CAR).carType(CarType.SEDAN)
                .dailyPriceEur(new BigDecimal("50.00")).build();
    }

    @Test
    void createModel_savesAndReturns() {
        when(vehicleModelRepository.save(any())).thenReturn(camry());
        CreateVehicleModelRequest req = new CreateVehicleModelRequest(
                "Toyota", "Camry", VehicleType.CAR, CarType.SEDAN, null, new BigDecimal("50.00"));
        VehicleModelResponse res = vehicleModelService.createModel(req);
        assertThat(res.brand()).isEqualTo("Toyota");
        assertThat(res.carType()).isEqualTo(CarType.SEDAN);
    }

    @Test
    void getModel_throwsWhenNotFound() {
        when(vehicleModelRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> vehicleModelService.getModel(99L))
                .isInstanceOf(VehicleModelNotFoundException.class);
    }

    @Test
    void listModels_filtersWhenTypeProvided() {
        when(vehicleModelRepository.findByVehicleType(VehicleType.CAR)).thenReturn(List.of(camry()));
        List<VehicleModelResponse> results = vehicleModelService.listModels(VehicleType.CAR);
        assertThat(results).hasSize(1);
    }

    @Test
    void listModels_returnsAll_whenTypeIsNull() {
        when(vehicleModelRepository.findAll()).thenReturn(List.of(camry()));
        List<VehicleModelResponse> results = vehicleModelService.listModels(null);
        assertThat(results).hasSize(1);
    }
}
```

- [ ] **Run test — expect compile failure**

```bash
./mvnw test -Dtest=VehicleModelServiceTest
```

- [ ] **Create `VehicleModelNotFoundException.java`**

```java
package com.iwonabendig.car_rental.exceptions;

public class VehicleModelNotFoundException extends RuntimeException {
    public VehicleModelNotFoundException(Long id) {
        super("Vehicle model not found with id: " + id);
    }
}
```

- [ ] **Create `CreateVehicleModelRequest.java`**

```java
package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import com.iwonabendig.car_rental.enums.VehicleType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateVehicleModelRequest(
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotNull VehicleType vehicleType,
        CarType carType,
        MotorcycleType motorcycleType,
        @NotNull @Positive BigDecimal dailyPriceEur
) {}
```

- [ ] **Create `VehicleModelResponse.java`**

```java
package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.VehicleModel;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import com.iwonabendig.car_rental.enums.VehicleType;
import java.math.BigDecimal;

public record VehicleModelResponse(
        Long id, String brand, String model,
        VehicleType vehicleType, CarType carType, MotorcycleType motorcycleType,
        BigDecimal dailyPriceEur
) {
    public static VehicleModelResponse from(VehicleModel m) {
        return new VehicleModelResponse(m.getId(), m.getBrand(), m.getModel(),
                m.getVehicleType(), m.getCarType(), m.getMotorcycleType(), m.getDailyPriceEur());
    }
}
```

- [ ] **Create `VehicleModelService.java`**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CreateVehicleModelRequest;
import com.iwonabendig.car_rental.dto.VehicleModelResponse;
import com.iwonabendig.car_rental.entity.VehicleModel;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.exceptions.VehicleModelNotFoundException;
import com.iwonabendig.car_rental.repository.VehicleModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleModelService {

    private final VehicleModelRepository vehicleModelRepository;

    public VehicleModelResponse createModel(CreateVehicleModelRequest request) {
        VehicleModel model = VehicleModel.builder()
                .brand(request.brand()).model(request.model())
                .vehicleType(request.vehicleType()).carType(request.carType())
                .motorcycleType(request.motorcycleType()).dailyPriceEur(request.dailyPriceEur())
                .build();
        return VehicleModelResponse.from(vehicleModelRepository.save(model));
    }

    @Transactional(readOnly = true)
    public VehicleModelResponse getModel(Long id) {
        return VehicleModelResponse.from(vehicleModelRepository.findById(id)
                .orElseThrow(() -> new VehicleModelNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<VehicleModelResponse> listModels(VehicleType vehicleType) {
        List<VehicleModel> models = vehicleType != null
                ? vehicleModelRepository.findByVehicleType(vehicleType)
                : vehicleModelRepository.findAll();
        return models.stream().map(VehicleModelResponse::from).toList();
    }
}
```

- [ ] **Create `VehicleModelController.java`**

```java
package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.dto.CreateVehicleModelRequest;
import com.iwonabendig.car_rental.dto.VehicleModelResponse;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.service.VehicleModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/vehicle-models")
@RequiredArgsConstructor
public class VehicleModelController {

    private final VehicleModelService vehicleModelService;

    @PostMapping
    public ResponseEntity<VehicleModelResponse> create(@Valid @RequestBody CreateVehicleModelRequest request) {
        VehicleModelResponse response = vehicleModelService.createModel(request);
        return ResponseEntity.created(URI.create("/api/vehicle-models/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public VehicleModelResponse get(@PathVariable Long id) {
        return vehicleModelService.getModel(id);
    }

    @GetMapping
    public List<VehicleModelResponse> list(@RequestParam(required = false) VehicleType vehicleType) {
        return vehicleModelService.listModels(vehicleType);
    }
}
```

- [ ] **Add `VehicleModelNotFoundException` handler to `GlobalExceptionHandler.java`**

Add inside the class:
```java
@ExceptionHandler(VehicleModelNotFoundException.class)
public ResponseEntity<ErrorResponse> handleVehicleModelNotFound(VehicleModelNotFoundException ex) {
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
}
```

Also add the import:
```java
import com.iwonabendig.car_rental.exceptions.VehicleModelNotFoundException;
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/exceptions/VehicleModelNotFoundException.java \
        src/main/java/com/iwonabendig/car_rental/dto/CreateVehicleModelRequest.java \
        src/main/java/com/iwonabendig/car_rental/dto/VehicleModelResponse.java \
        src/main/java/com/iwonabendig/car_rental/service/VehicleModelService.java \
        src/main/java/com/iwonabendig/car_rental/controller/VehicleModelController.java \
        src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java \
        src/test/java/com/iwonabendig/car_rental/service/VehicleModelServiceTest.java
git commit -m "feat: add VehicleModelService, controller, DTOs, and exception handler"
```

---

## Slice 3 — Reservation Update

### Task 9: Update `Reservation` entity

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/entity/Reservation.java`

- [ ] **Replace `Reservation.java` entirely**

```java
package com.iwonabendig.car_rental.entity;

import com.iwonabendig.car_rental.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_res_car_dates", columnList = "car_id, start_date_time, end_date_time")
})
@EntityListeners(AuditingEntityListener.class)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id")
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorcycle_id")
    private Motorcycle motorcycle;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDayEur;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCostEur;

    @NotNull
    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @NotNull
    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }

    public void cancel() {
        if (status == ReservationStatus.CANCELLED)
            throw new IllegalStateException("Reservation is already cancelled");
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void complete() {
        if (status != ReservationStatus.ACTIVE)
            throw new IllegalStateException("Only ACTIVE reservations can be completed");
        this.status = ReservationStatus.COMPLETED;
    }

    public void reschedule(LocalDateTime newStart, int newDurationDays) {
        if (status == ReservationStatus.CANCELLED)
            throw new IllegalStateException("Cannot reschedule a cancelled reservation");
        if (newStart.isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("New start must be in the future");
        if (newDurationDays < 1)
            throw new IllegalArgumentException("Duration must be at least 1 day");
        this.startDateTime = newStart;
        this.endDateTime = newStart.plusDays(newDurationDays);
        this.totalCostEur = pricePerDayEur.multiply(BigDecimal.valueOf(newDurationDays))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Run all tests — expect compile failures in tests that used `customerName`; that is expected — they will be fixed in Task 11**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/entity/Reservation.java
git commit -m "feat: update Reservation entity — Customer FK, motorcycle FK, pricing, audit fields"
```

---

### Task 10: Update `ReservationRepository`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/repository/ReservationRepository.java`

- [ ] **Replace `ReservationRepository.java` entirely**

```java
package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Customer;
import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.MotorcycleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCustomer(Customer customer);

    @Query("""
        SELECT r.car.id FROM Reservation r
        WHERE r.car IS NOT NULL
          AND r.car.vehicleModel.carType = :carType
          AND r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
          AND r.startDateTime < :end
          AND r.endDateTime > :start
    """)
    List<Long> findBookedCarIdsByCarTypeAndWindow(
            @Param("carType") CarType carType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT r.motorcycle.id FROM Reservation r
        WHERE r.motorcycle IS NOT NULL
          AND r.motorcycle.vehicleModel.motorcycleType = :motorcycleType
          AND r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
          AND r.startDateTime < :end
          AND r.endDateTime > :start
    """)
    List<Long> findBookedMotorcycleIdsByTypeAndWindow(
            @Param("motorcycleType") MotorcycleType motorcycleType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.car.id = :carId
          AND r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
          AND r.startDateTime < :end
          AND r.endDateTime > :start
    """)
    List<Reservation> findOverlappingForCar(
            @Param("carId") Long carId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
          AND r.endDateTime < :now
    """)
    List<Reservation> findExpiredActiveReservations(@Param("now") LocalDateTime now);
}
```

- [ ] **Run all tests — compile errors in service/integration tests are still expected**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/repository/ReservationRepository.java
git commit -m "feat: update ReservationRepository queries for new schema"
```

---

### Task 11: Update `ReservationService` + new exception + rewrite service test

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/exceptions/NoVehicleAvailableException.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/service/ReservationService.java`
- Modify: `src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java`

- [ ] **Create `NoVehicleAvailableException.java`**

```java
package com.iwonabendig.car_rental.exceptions;

public class NoVehicleAvailableException extends RuntimeException {
    public NoVehicleAvailableException(String message) {
        super(message);
    }
}
```

- [ ] **Replace `ReservationService.java` entirely**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CarSummaryResponse;
import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.entity.*;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.exceptions.*;
import com.iwonabendig.car_rental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private final CarRepository carRepository;
    private final MotorcycleRepository motorcycleRepository;
    private final ReservationRepository reservationRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final CustomerRepository customerRepository;

    public ReservationResponse reserve(Long vehicleModelId, Long customerId,
                                       LocalDateTime startDateTime, int numberOfDays) {
        if (numberOfDays < 1)
            throw new InvalidReservationException("Invalid reservation: reservation too short.");
        if (startDateTime.isBefore(LocalDateTime.now()))
            throw new InvalidReservationException("Invalid reservation: reservation in the past.");

        VehicleModel model = vehicleModelRepository.findById(vehicleModelId)
                .orElseThrow(() -> new VehicleModelNotFoundException(vehicleModelId));
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        LocalDateTime endDateTime = startDateTime.plusDays(numberOfDays);
        BigDecimal pricePerDay = model.getDailyPriceEur();
        BigDecimal totalCost = pricePerDay.multiply(BigDecimal.valueOf(numberOfDays))
                .setScale(2, RoundingMode.HALF_UP);

        if (model.getVehicleType() == VehicleType.CAR) {
            return reserveCar(model, customer, startDateTime, endDateTime, pricePerDay, totalCost);
        } else {
            return reserveMotorcycle(model, customer, startDateTime, endDateTime, pricePerDay, totalCost);
        }
    }

    private ReservationResponse reserveCar(VehicleModel model, Customer customer,
            LocalDateTime start, LocalDateTime end, BigDecimal pricePerDay, BigDecimal totalCost) {
        List<Car> candidates = carRepository.findByCarTypeActiveLocked(model.getCarType());
        if (candidates.isEmpty())
            throw new NoVehicleAvailableException(
                    "No active cars for model " + model.getBrand() + " " + model.getModel());
        Set<Long> bookedIds = new HashSet<>(
                reservationRepository.findBookedCarIdsByCarTypeAndWindow(model.getCarType(), start, end));
        Car selected = candidates.stream().filter(c -> !bookedIds.contains(c.getId())).findFirst()
                .orElseThrow(() -> new NoVehicleAvailableException(
                        "All cars for model " + model.getBrand() + " " + model.getModel() + " are booked."));
        Reservation reservation = Reservation.builder()
                .customer(customer).car(selected)
                .pricePerDayEur(pricePerDay).totalCostEur(totalCost)
                .startDateTime(start).endDateTime(end).build();
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    private ReservationResponse reserveMotorcycle(VehicleModel model, Customer customer,
            LocalDateTime start, LocalDateTime end, BigDecimal pricePerDay, BigDecimal totalCost) {
        List<Motorcycle> candidates = motorcycleRepository.findByMotorcycleTypeActiveLocked(
                model.getMotorcycleType());
        if (candidates.isEmpty())
            throw new NoVehicleAvailableException(
                    "No active motorcycles for model " + model.getBrand() + " " + model.getModel());
        Set<Long> bookedIds = new HashSet<>(
                reservationRepository.findBookedMotorcycleIdsByTypeAndWindow(
                        model.getMotorcycleType(), start, end));
        Motorcycle selected = candidates.stream().filter(m -> !bookedIds.contains(m.getId())).findFirst()
                .orElseThrow(() -> new NoVehicleAvailableException(
                        "All motorcycles for model " + model.getBrand() + " " + model.getModel() + " are booked."));
        Reservation reservation = Reservation.builder()
                .customer(customer).motorcycle(selected)
                .pricePerDayEur(pricePerDay).totalCostEur(totalCost)
                .startDateTime(start).endDateTime(end).build();
        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    public void cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        reservation.cancel();
    }

    public ReservationResponse reschedule(Long reservationId, LocalDateTime newStart, int newDays) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        reservation.reschedule(newStart, newDays);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long id) {
        return ReservationResponse.from(reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<CarSummaryResponse> findAvailableCars(Long vehicleModelId,
                                                      LocalDateTime startDateTime, int days) {
        VehicleModel model = vehicleModelRepository.findById(vehicleModelId)
                .orElseThrow(() -> new VehicleModelNotFoundException(vehicleModelId));
        LocalDateTime end = startDateTime.plusDays(days);
        List<Car> all = carRepository.findByVehicleModel_CarTypeAndActiveTrue(model.getCarType());
        Set<Long> bookedIds = new HashSet<>(
                reservationRepository.findBookedCarIdsByCarTypeAndWindow(model.getCarType(), startDateTime, end));
        return all.stream().filter(c -> !bookedIds.contains(c.getId()))
                .map(CarSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getCustomerReservations(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        return reservationRepository.findByCustomer(customer).stream()
                .map(ReservationResponse::from).toList();
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void completeExpiredReservations() {
        reservationRepository.findExpiredActiveReservations(LocalDateTime.now())
                .forEach(Reservation::complete);
    }
}
```

- [ ] **Rewrite `ReservationServiceTest.java`**

```java
package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.entity.*;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.ReservationStatus;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.exceptions.*;
import com.iwonabendig.car_rental.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock CarRepository carRepository;
    @Mock MotorcycleRepository motorcycleRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock VehicleModelRepository vehicleModelRepository;
    @Mock CustomerRepository customerRepository;
    @InjectMocks ReservationService service;

    private LocalDateTime futureStart;
    private VehicleModel sedanModel;
    private Customer alice;

    @BeforeEach
    void setUp() {
        futureStart = LocalDateTime.now().plusDays(1);
        sedanModel = VehicleModel.builder()
                .brand("Toyota").model("Camry")
                .vehicleType(VehicleType.CAR).carType(CarType.SEDAN)
                .dailyPriceEur(new BigDecimal("50.00")).build();
        alice = Customer.builder()
                .firstName("Alice").lastName("Smith").email("alice@test.com")
                .phone("+48123456789").driverLicenseNumber("DL-001")
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .address("123 Main St").dateOfBirth(LocalDate.of(1990, 5, 15))
                .build();
    }

    private Car activeCar(long id) {
        return Car.builder()
                .vehicleModel(sedanModel)
                .registrationNumber("REG-" + id)
                .year(2022).colour("Silver").active(true).numberOfDoors(4)
                .build();
    }

    private void stubSaveAssigningId(long assignedId, Car car) {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            return Reservation.builder()
                    .customer(r.getCustomer()).car(r.getCar())
                    .pricePerDayEur(r.getPricePerDayEur()).totalCostEur(r.getTotalCostEur())
                    .startDateTime(r.getStartDateTime()).endDateTime(r.getEndDateTime())
                    .status(r.getStatus()).build();
        });
    }

    @Test
    void reserve_returnsResponse_whenCarIsAvailable() {
        Car car = activeCar(1L);
        when(vehicleModelRepository.findById(1L)).thenReturn(Optional.of(sedanModel));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(carRepository.findByCarTypeActiveLocked(CarType.SEDAN)).thenReturn(List.of(car));
        when(reservationRepository.findBookedCarIdsByCarTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of());
        stubSaveAssigningId(42L, car);

        ReservationResponse response = service.reserve(1L, 1L, futureStart, 3);

        assertThat(response.totalCostEur()).isEqualByComparingTo("150.00");
        assertThat(response.pricePerDayEur()).isEqualByComparingTo("50.00");
        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void reserve_throwsNoVehicleAvailable_whenAllCarsBooked() {
        Car car = activeCar(1L);
        when(vehicleModelRepository.findById(1L)).thenReturn(Optional.of(sedanModel));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(carRepository.findByCarTypeActiveLocked(CarType.SEDAN)).thenReturn(List.of(car));
        when(reservationRepository.findBookedCarIdsByCarTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of(car.getId()));

        assertThatThrownBy(() -> service.reserve(1L, 1L, futureStart, 1))
                .isInstanceOf(NoVehicleAvailableException.class)
                .hasMessageContaining("booked");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_throwsInvalidReservation_whenDaysIsZero() {
        assertThatThrownBy(() -> service.reserve(1L, 1L, futureStart, 0))
                .isInstanceOf(InvalidReservationException.class);
        verifyNoInteractions(vehicleModelRepository, carRepository, reservationRepository);
    }

    @Test
    void reserve_throwsInvalidReservation_whenStartIsInThePast() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        assertThatThrownBy(() -> service.reserve(1L, 1L, past, 1))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("past");
        verifyNoInteractions(vehicleModelRepository, carRepository, reservationRepository);
    }

    @Test
    void reserve_throwsVehicleModelNotFound_whenModelMissing() {
        when(vehicleModelRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reserve(99L, 1L, futureStart, 1))
                .isInstanceOf(VehicleModelNotFoundException.class);
    }

    @Test
    void cancel_marksReservationAsCancelled() {
        Car car = activeCar(1L);
        Reservation reservation = Reservation.builder()
                .customer(alice).car(car)
                .pricePerDayEur(new BigDecimal("50.00"))
                .totalCostEur(new BigDecimal("50.00"))
                .status(ReservationStatus.ACTIVE)
                .startDateTime(futureStart).endDateTime(futureStart.plusDays(1))
                .build();
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        service.cancel(5L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
    }

    @Test
    void cancel_throwsReservationNotFound_whenIdMissing() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(999L))
                .isInstanceOf(ReservationNotFoundException.class);
    }
}
```

- [ ] **Run tests — expect green**

```bash
./mvnw test -Dtest=ReservationServiceTest
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/exceptions/NoVehicleAvailableException.java \
        src/main/java/com/iwonabendig/car_rental/service/ReservationService.java \
        src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java
git commit -m "feat: update ReservationService for new domain, add scheduling, rewrite service tests"
```

---

### Task 12: Update DTOs + `ReservationController` + `GlobalExceptionHandler` + fix integration tests

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/CreateReservationRequest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/ReservationResponse.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/CarSummaryResponse.java`
- Create: `src/main/java/com/iwonabendig/car_rental/dto/RescheduleReservationRequest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/ReservationController.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java`

- [ ] **Replace `CreateReservationRequest.java`**

```java
package com.iwonabendig.car_rental.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotNull Long vehicleModelId,
        @NotNull Long customerId,
        @NotNull @Future LocalDateTime startDateTime,
        @NotNull @Min(1) @Max(30) Integer numberOfDays
) {}
```

- [ ] **Replace `ReservationResponse.java`**

```java
package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.entity.Vehicle;
import com.iwonabendig.car_rental.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long vehicleId,
        String vehicleBrand,
        String vehicleModel,
        String registrationNumber,
        Long customerId,
        String customerFullName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        BigDecimal pricePerDayEur,
        BigDecimal totalCostEur,
        ReservationStatus status
) {
    public static ReservationResponse from(Reservation reservation) {
        Vehicle vehicle = reservation.getCar() != null
                ? reservation.getCar()
                : reservation.getMotorcycle();
        return new ReservationResponse(
                reservation.getId(),
                vehicle.getId(),
                vehicle.getVehicleModel().getBrand(),
                vehicle.getVehicleModel().getModel(),
                vehicle.getRegistrationNumber(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
                reservation.getStartDateTime(),
                reservation.getEndDateTime(),
                reservation.getPricePerDayEur(),
                reservation.getTotalCostEur(),
                reservation.getStatus());
    }
}
```

- [ ] **Replace `CarSummaryResponse.java`**

```java
package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.Car;

public record CarSummaryResponse(
        Long id,
        String brand,
        String model,
        String registrationNumber,
        Integer numberOfDoors
) {
    public static CarSummaryResponse from(Car car) {
        return new CarSummaryResponse(
                car.getId(),
                car.getVehicleModel().getBrand(),
                car.getVehicleModel().getModel(),
                car.getRegistrationNumber(),
                car.getNumberOfDoors());
    }
}
```

- [ ] **Create `RescheduleReservationRequest.java`**

```java
package com.iwonabendig.car_rental.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record RescheduleReservationRequest(
        @NotNull @Future LocalDateTime newStartDateTime,
        @NotNull @Min(1) @Max(30) Integer numberOfDays
) {}
```

- [ ] **Replace `ReservationController.java`**

```java
package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.dto.*;
import com.iwonabendig.car_rental.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Validated
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.reserve(
                request.vehicleModelId(), request.customerId(),
                request.startDateTime(), request.numberOfDays());
        return ResponseEntity.created(URI.create("/api/reservations/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.getReservation(id);
    }

    @GetMapping("/availability")
    public List<CarSummaryResponse> availability(
            @RequestParam Long vehicleModelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDateTime,
            @RequestParam @Min(1) @Max(30) int numberOfDays) {
        return reservationService.findAvailableCars(vehicleModelId, startDateTime, numberOfDays);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }

    @PatchMapping("/{id}/reschedule")
    public ReservationResponse reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleReservationRequest request) {
        return reservationService.reschedule(id, request.newStartDateTime(), request.numberOfDays());
    }
}
```

- [ ] **Add `NoVehicleAvailableException` handler to `GlobalExceptionHandler.java` and remove the old `NoCarAvailableException` handler**

Remove:
```java
@ExceptionHandler(NoCarAvailableException.class)
public ResponseEntity<ErrorResponse> handleNoCar(NoCarAvailableException ex) { ... }
```

Add:
```java
@ExceptionHandler(NoVehicleAvailableException.class)
public ResponseEntity<ErrorResponse> handleNoVehicle(NoVehicleAvailableException ex) {
    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage()));
}
```

Also remove the import of `NoCarAvailableException` and add:
```java
import com.iwonabendig.car_rental.exceptions.NoVehicleAvailableException;
```

- [ ] **Rewrite `ReservationFlowIntegrationTest.java`**

The integration test must now seed a `VehicleModel`, `Car`, and `Customer` before each test — direct entity saves via repositories. The `BeforeEach` block needs to delete all data in FK-safe order and re-insert the minimum required fixtures.

```java
package com.iwonabendig.car_rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iwonabendig.car_rental.dto.CreateReservationRequest;
import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.entity.Customer;
import com.iwonabendig.car_rental.entity.VehicleModel;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.VehicleType;
import com.iwonabendig.car_rental.repository.CarRepository;
import com.iwonabendig.car_rental.repository.CustomerRepository;
import com.iwonabendig.car_rental.repository.ReservationRepository;
import com.iwonabendig.car_rental.repository.VehicleModelRepository;
import com.iwonabendig.car_rental.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CarRepository carRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired VehicleModelRepository vehicleModelRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationService reservationService;

    VehicleModel sedanModel;
    Customer alice;

    @BeforeEach
    void cleanAndSeed() {
        reservationRepository.deleteAll();
        carRepository.deleteAll();
        vehicleModelRepository.deleteAll();
        customerRepository.deleteAll();

        sedanModel = vehicleModelRepository.save(VehicleModel.builder()
                .brand("Toyota").model("Camry").vehicleType(VehicleType.CAR)
                .carType(CarType.SEDAN).dailyPriceEur(new BigDecimal("50.00")).build());

        alice = customerRepository.save(Customer.builder()
                .firstName("Alice").lastName("Smith").email("alice@test.com")
                .phone("+48123456789").driverLicenseNumber("DL-INT-001")
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .address("123 Main St").dateOfBirth(LocalDate.of(1990, 5, 15)).build());
    }

    @Test
    void postReservation_then_availabilityShowsDecrementedCount() throws Exception {
        carRepository.save(Car.builder().vehicleModel(sedanModel).registrationNumber("INT-1")
                .year(2022).colour("Silver").active(true).numberOfDoors(4).build());
        carRepository.save(Car.builder().vehicleModel(sedanModel).registrationNumber("INT-2")
                .year(2022).colour("White").active(true).numberOfDoors(4).build());

        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);

        mockMvc.perform(get("/api/reservations/availability")
                        .param("vehicleModelId", sedanModel.getId().toString())
                        .param("startDateTime", start.toString())
                        .param("numberOfDays", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        CreateReservationRequest request = new CreateReservationRequest(
                sedanModel.getId(), alice.getId(), start, 3);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalCostEur").value(150.00))
                .andExpect(jsonPath("$.customerFullName").value("Alice Smith"));

        mockMvc.perform(get("/api/reservations/availability")
                        .param("vehicleModelId", sedanModel.getId().toString())
                        .param("startDateTime", start.toString())
                        .param("numberOfDays", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void postReservation_returns400_whenPayloadFailsValidation() throws Exception {
        String invalidJson = """
                {"vehicleModelId":1,"customerId":1,"startDateTime":"%s","numberOfDays":0}
                """.formatted(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled("Demonstrates known race in reserve(); enable once locking is verified end-to-end")
    void concurrent_bookings_onlyOneSucceeds_whenSingleCarAvailable() throws Exception {
        carRepository.save(Car.builder().vehicleModel(sedanModel).registrationNumber("V-001")
                .year(2022).colour("Black").active(true).numberOfDoors(4).build());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        LocalDateTime startDt = LocalDateTime.now().plusDays(5).withNano(0);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        reservationService.reserve(sedanModel.getId(), alice.getId(), startDt, 2);
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
    }
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/dto/ \
        src/main/java/com/iwonabendig/car_rental/controller/ReservationController.java \
        src/main/java/com/iwonabendig/car_rental/controller/GlobalExceptionHandler.java \
        src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java
git commit -m "feat: update ReservationController, DTOs, exception handler, fix integration tests"
```

---

## Slice 4 — Known Gaps

### Task 13: Data seeding

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/DataSeeder.java`

- [ ] **Create `DataSeeder.java`**

```java
package com.iwonabendig.car_rental;

import com.iwonabendig.car_rental.entity.*;
import com.iwonabendig.car_rental.enums.*;
import com.iwonabendig.car_rental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataSeeder {

    private final VehicleModelRepository vehicleModelRepository;
    private final CarRepository carRepository;
    private final MotorcycleRepository motorcycleRepository;
    private final CustomerRepository customerRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (vehicleModelRepository.count() > 0) return;

        VehicleModel camry = vehicleModelRepository.save(VehicleModel.builder()
                .brand("Toyota").model("Camry").vehicleType(VehicleType.CAR)
                .carType(CarType.SEDAN).dailyPriceEur(new BigDecimal("50.00")).build());
        VehicleModel rav4 = vehicleModelRepository.save(VehicleModel.builder()
                .brand("Toyota").model("RAV4").vehicleType(VehicleType.CAR)
                .carType(CarType.SUV).dailyPriceEur(new BigDecimal("75.00")).build());
        VehicleModel sprinter = vehicleModelRepository.save(VehicleModel.builder()
                .brand("Mercedes").model("Sprinter").vehicleType(VehicleType.CAR)
                .carType(CarType.VAN).dailyPriceEur(new BigDecimal("100.00")).build());
        VehicleModel cb500 = vehicleModelRepository.save(VehicleModel.builder()
                .brand("Honda").model("CB500F").vehicleType(VehicleType.MOTORCYCLE)
                .motorcycleType(MotorcycleType.SPORT).dailyPriceEur(new BigDecimal("40.00")).build());

        carRepository.save(Car.builder().vehicleModel(camry).registrationNumber("ABC-1234")
                .year(2022).colour("Silver").active(true).numberOfDoors(4).build());
        carRepository.save(Car.builder().vehicleModel(camry).registrationNumber("ABC-5678")
                .year(2021).colour("White").active(true).numberOfDoors(4).build());
        carRepository.save(Car.builder().vehicleModel(rav4).registrationNumber("XYZ-0001")
                .year(2023).colour("Black").active(true).numberOfDoors(5).build());
        carRepository.save(Car.builder().vehicleModel(sprinter).registrationNumber("V-9999")
                .year(2020).colour("White").active(true).numberOfDoors(3).build());
        motorcycleRepository.save(Motorcycle.builder().vehicleModel(cb500)
                .registrationNumber("MOTO-001").year(2023).colour("Red")
                .active(true).engineCC(500).build());

        customerRepository.save(Customer.builder()
                .firstName("Alice").lastName("Smith").email("alice@example.com")
                .phone("+48123456789").driverLicenseNumber("DL-SEED-001")
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .address("123 Main St, Warsaw").dateOfBirth(LocalDate.of(1990, 5, 15)).build());
    }
}
```

- [ ] **Run all tests — expect green** (seeder guard `if count > 0` prevents duplicate data in tests)

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/DataSeeder.java
git commit -m "feat: add DataSeeder so app starts with usable data"
```

---

### Task 14: Soft-delete endpoints + `VehicleController`

**Files:**
- Create: `src/main/java/com/iwonabendig/car_rental/controller/VehicleController.java`

- [ ] **Create `VehicleController.java`**

```java
package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.entity.Motorcycle;
import com.iwonabendig.car_rental.exceptions.ReservationNotFoundException;
import com.iwonabendig.car_rental.repository.CarRepository;
import com.iwonabendig.car_rental.repository.MotorcycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final CarRepository carRepository;
    private final MotorcycleRepository motorcycleRepository;

    @DeleteMapping("/cars/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retireCar(@PathVariable Long id) {
        Car car = carRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        car.retire();
        carRepository.save(car);
    }

    @DeleteMapping("/motorcycles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retireMotorcycle(@PathVariable Long id) {
        Motorcycle motorcycle = motorcycleRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        motorcycle.retire();
        motorcycleRepository.save(motorcycle);
    }
}
```

> Note: reuses `ReservationNotFoundException` for 404 for now. If you want a dedicated `VehicleNotFoundException`, create it and wire it in `GlobalExceptionHandler` the same way as the others.

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/controller/VehicleController.java
git commit -m "feat: add soft-delete endpoints for cars and motorcycles"
```

---

### Task 15: `COMPLETED` status scheduling + `@EnableScheduling`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/CarRentalApplication.java`

The `completeExpiredReservations()` method is already on `ReservationService` with `@Scheduled(cron = "0 0 1 * * *")`. The only missing piece is enabling scheduling.

- [ ] **Add `@EnableScheduling` to `CarRentalApplication.java`**

```java
package com.iwonabendig.car_rental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class CarRentalApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarRentalApplication.class, args);
    }
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/CarRentalApplication.java
git commit -m "feat: enable scheduling for nightly COMPLETED status transition"
```

---

### Task 16: Add customer reservations endpoint to `CustomerController`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/CustomerController.java`

- [ ] **Add `getReservations` endpoint to `CustomerController.java`**

Add `ReservationService` dependency and a new method:

```java
package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.dto.CreateCustomerRequest;
import com.iwonabendig.car_rental.dto.CustomerResponse;
import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.service.CustomerService;
import com.iwonabendig.car_rental.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/customers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.getCustomer(id);
    }

    @GetMapping("/{id}/reservations")
    public List<ReservationResponse> getReservations(@PathVariable Long id) {
        return reservationService.getCustomerReservations(id);
    }
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/controller/CustomerController.java
git commit -m "feat: add GET /api/customers/{id}/reservations endpoint"
```

---

### Task 17: OpenAPI / Swagger

**Files:**
- Modify: `pom.xml`

- [ ] **Add `springdoc-openapi-starter-webmvc-ui` to `pom.xml`**

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Verify Swagger UI loads**

```bash
./mvnw spring-boot:run &
sleep 5
curl -s http://localhost:8080/swagger-ui/index.html | grep -q "swagger" && echo "OK"
kill %1
```

- [ ] **Commit**

```bash
git add pom.xml
git commit -m "feat: add springdoc-openapi for Swagger UI at /swagger-ui/index.html"
```

---

### Task 18: Pagination on `GET /vehicle-models` and `GET /availability`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/service/VehicleModelService.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/controller/VehicleModelController.java`

`GET /availability` already returns a filtered list; making it pageable requires returning `Page<CarSummaryResponse>`. `GET /vehicle-models` is the simpler first target.

- [ ] **Update `VehicleModelService.listModels` to accept `Pageable`**

Replace the `listModels` method:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Transactional(readOnly = true)
public Page<VehicleModelResponse> listModels(VehicleType vehicleType, Pageable pageable) {
    Page<VehicleModel> page = vehicleType != null
            ? vehicleModelRepository.findByVehicleType(vehicleType, pageable)
            : vehicleModelRepository.findAll(pageable);
    return page.map(VehicleModelResponse::from);
}
```

- [ ] **Update `VehicleModelRepository` to accept `Pageable` on `findByVehicleType`**

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

Page<VehicleModel> findByVehicleType(VehicleType vehicleType, Pageable pageable);
```

- [ ] **Update `VehicleModelController.list` to pass `Pageable`**

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@GetMapping
public Page<VehicleModelResponse> list(
        @RequestParam(required = false) VehicleType vehicleType,
        @PageableDefault(size = 20) Pageable pageable) {
    return vehicleModelService.listModels(vehicleType, pageable);
}
```

- [ ] **Update `VehicleModelServiceTest` — fix the two `listModels` tests to pass a `Pageable`**

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Test
void listModels_filtersWhenTypeProvided() {
    Pageable pageable = PageRequest.of(0, 20);
    when(vehicleModelRepository.findByVehicleType(VehicleType.CAR, pageable))
            .thenReturn(new PageImpl<>(List.of(camry())));
    Page<VehicleModelResponse> results = vehicleModelService.listModels(VehicleType.CAR, pageable);
    assertThat(results.getContent()).hasSize(1);
}

@Test
void listModels_returnsAll_whenTypeIsNull() {
    Pageable pageable = PageRequest.of(0, 20);
    when(vehicleModelRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(camry())));
    Page<VehicleModelResponse> results = vehicleModelService.listModels(null, pageable);
    assertThat(results.getContent()).hasSize(1);
}
```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/main/java/com/iwonabendig/car_rental/service/VehicleModelService.java \
        src/main/java/com/iwonabendig/car_rental/controller/VehicleModelController.java \
        src/main/java/com/iwonabendig/car_rental/repository/VehicleModelRepository.java
git commit -m "feat: add pagination to GET /vehicle-models"
```

---

### Task 19: Re-enable and verify the concurrency test

After the `@Lock(PESSIMISTIC_WRITE)` queries are in place (added in Task 7), the concurrency test should pass.

**Files:**
- Modify: `src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java`

- [ ] **Remove `@Disabled` from `concurrent_bookings_onlyOneSucceeds_whenSingleCarAvailable`**

- [ ] **Run the concurrency test**

```bash
./mvnw test -Dtest=ReservationFlowIntegrationTest#concurrent_bookings_onlyOneSucceeds_whenSingleCarAvailable
```

Expected: PASS — exactly 1 success, 9 failures.

- [ ] **If the test fails**, the pessimistic lock in `findByCarTypeActiveLocked` is not being respected by H2. H2 ignores `PESSIMISTIC_WRITE` locks. Two options:
  - Keep the test `@Disabled` with a comment explaining it needs Postgres to verify
  - Or switch to a unique constraint approach (add a `UNIQUE` constraint on `(car_id, start_date_time, end_date_time)` at the DB level)

  For now, if it fails on H2, update the `@Disabled` message:
  ```java
  @Disabled("PESSIMISTIC_WRITE not enforced by H2; re-enable against Postgres")
  ```

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java
git commit -m "test: update concurrency test for new reservation signature"
```

---

### Task 20: Timezone fix — `LocalDateTime` → `Instant` / `OffsetDateTime`

**Files:**
- Modify: `src/main/java/com/iwonabendig/car_rental/entity/Reservation.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/CreateReservationRequest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/ReservationResponse.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/dto/RescheduleReservationRequest.java`
- Modify: `src/main/java/com/iwonabendig/car_rental/service/ReservationService.java`
- Modify: `src/test/java/com/iwonabendig/car_rental/ReservationFlowIntegrationTest.java`
- Modify: `src/test/java/com/iwonabendig/car_rental/service/ReservationServiceTest.java`

> **Rule:** store `Instant` (UTC) in the database; accept and return `OffsetDateTime` at the HTTP boundary; convert in the service layer using `OffsetDateTime.toInstant()` / `instant.atOffset(ZoneOffset.UTC)`.

- [ ] **Update `Reservation.java` — change `startDateTime` and `endDateTime` from `LocalDateTime` to `Instant`**

Replace the two field declarations and their column annotations:

```java
import java.time.Instant;

@NotNull
@Column(name = "start_date_time", nullable = false)
private Instant startDateTime;

@NotNull
@Column(name = "end_date_time", nullable = false)
private Instant endDateTime;
```

Also update `cancel()`, `complete()`, `reschedule()` — `LocalDateTime.now()` → `Instant.now()`, and `plusDays` → `startDateTime.plus(newDurationDays, ChronoUnit.DAYS)`:

```java
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public void cancel() {
    if (status == ReservationStatus.CANCELLED)
        throw new IllegalStateException("Reservation is already cancelled");
    this.status = ReservationStatus.CANCELLED;
    this.cancelledAt = Instant.now();
}

public void reschedule(Instant newStart, int newDurationDays) {
    if (status == ReservationStatus.CANCELLED)
        throw new IllegalStateException("Cannot reschedule a cancelled reservation");
    if (newStart.isBefore(Instant.now()))
        throw new IllegalArgumentException("New start must be in the future");
    if (newDurationDays < 1)
        throw new IllegalArgumentException("Duration must be at least 1 day");
    this.startDateTime = newStart;
    this.endDateTime = newStart.plus(newDurationDays, ChronoUnit.DAYS);
    this.totalCostEur = pricePerDayEur.multiply(BigDecimal.valueOf(newDurationDays))
            .setScale(2, RoundingMode.HALF_UP);
}
```

Also change `cancelledAt`, `createdAt`, `updatedAt` to `Instant`:
```java
@CreatedDate
@Column(nullable = false, updatable = false)
private Instant createdAt;

@LastModifiedDate
@Column(nullable = false)
private Instant updatedAt;

@Column
private Instant cancelledAt;
```

- [ ] **Update `CreateReservationRequest.java` — `LocalDateTime` → `OffsetDateTime`**

```java
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record CreateReservationRequest(
        @NotNull Long vehicleModelId,
        @NotNull Long customerId,
        @NotNull @Future OffsetDateTime startDateTime,
        @NotNull @Min(1) @Max(30) Integer numberOfDays
) {}
```

- [ ] **Update `RescheduleReservationRequest.java`**

```java
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record RescheduleReservationRequest(
        @NotNull @Future OffsetDateTime newStartDateTime,
        @NotNull @Min(1) @Max(30) Integer numberOfDays
) {}
```

- [ ] **Update `ReservationResponse.java` — return `OffsetDateTime` instead of `LocalDateTime`**

```java
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ReservationResponse(
        Long id, Long vehicleId, String vehicleBrand, String vehicleModel,
        String registrationNumber, Long customerId, String customerFullName,
        OffsetDateTime startDateTime, OffsetDateTime endDateTime,
        BigDecimal pricePerDayEur, BigDecimal totalCostEur, ReservationStatus status
) {
    public static ReservationResponse from(Reservation reservation) {
        Vehicle vehicle = reservation.getCar() != null
                ? reservation.getCar() : reservation.getMotorcycle();
        return new ReservationResponse(
                reservation.getId(),
                vehicle.getId(),
                vehicle.getVehicleModel().getBrand(),
                vehicle.getVehicleModel().getModel(),
                vehicle.getRegistrationNumber(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
                reservation.getStartDateTime().atOffset(ZoneOffset.UTC),
                reservation.getEndDateTime().atOffset(ZoneOffset.UTC),
                reservation.getPricePerDayEur(),
                reservation.getTotalCostEur(),
                reservation.getStatus());
    }
}
```

- [ ] **Update `ReservationService.java` — convert `OffsetDateTime` to `Instant` at the service boundary**

Change the `reserve` signature and all `LocalDateTime` usages:

```java
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public ReservationResponse reserve(Long vehicleModelId, Long customerId,
                                   OffsetDateTime startDateTime, int numberOfDays) {
    Instant start = startDateTime.toInstant();
    if (numberOfDays < 1)
        throw new InvalidReservationException("Invalid reservation: reservation too short.");
    if (start.isBefore(Instant.now()))
        throw new InvalidReservationException("Invalid reservation: reservation in the past.");

    // ... rest of method unchanged except use start (Instant) instead of startDateTime (LocalDateTime)
    Instant endDateTime = start.plus(numberOfDays, ChronoUnit.DAYS);
    // ... pass start and endDateTime (both Instant) down to reserveCar/reserveMotorcycle
}

public ReservationResponse reschedule(Long reservationId, OffsetDateTime newStart, int newDays) {
    Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    reservation.reschedule(newStart.toInstant(), newDays);
    return ReservationResponse.from(reservation);
}

public List<CarSummaryResponse> findAvailableCars(Long vehicleModelId,
                                                  OffsetDateTime startDateTime, int days) {
    Instant start = startDateTime.toInstant();
    Instant end = start.plus(days, ChronoUnit.DAYS);
    // ... use start and end (Instant) in repository calls
}
```

Also update `completeExpiredReservations`:
```java
@Scheduled(cron = "0 0 1 * * *")
public void completeExpiredReservations() {
    reservationRepository.findExpiredActiveReservations(Instant.now())
            .forEach(Reservation::complete);
}
```

- [ ] **Update `ReservationRepository.java` — change `LocalDateTime` parameters to `Instant`**

```java
import java.time.Instant;

List<Long> findBookedCarIdsByCarTypeAndWindow(
        @Param("carType") CarType carType,
        @Param("start") Instant start,
        @Param("end") Instant end);

List<Long> findBookedMotorcycleIdsByTypeAndWindow(
        @Param("motorcycleType") MotorcycleType motorcycleType,
        @Param("start") Instant start,
        @Param("end") Instant end);

List<Reservation> findOverlappingForCar(
        @Param("carId") Long carId,
        @Param("start") Instant start,
        @Param("end") Instant end);

List<Reservation> findExpiredActiveReservations(@Param("now") Instant now);
```

- [ ] **Update `ReservationController.java` — change `@DateTimeFormat` to accept `OffsetDateTime`**

```java
import java.time.OffsetDateTime;

@GetMapping("/availability")
public List<CarSummaryResponse> availability(
        @RequestParam Long vehicleModelId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDateTime,
        @RequestParam @Min(1) @Max(30) int numberOfDays) {
    return reservationService.findAvailableCars(vehicleModelId, startDateTime, numberOfDays);
}
```

- [ ] **Update `ReservationServiceTest` and `ReservationFlowIntegrationTest`** — replace all `LocalDateTime.now().plusDays(...)` with `OffsetDateTime.now(ZoneOffset.UTC).plusDays(...)` in test fixtures and request bodies.

- [ ] **Run all tests — expect green**

```bash
./mvnw test
```

- [ ] **Commit**

```bash
git add -A
git commit -m "feat: timezone fix — store Instant in DB, accept/return OffsetDateTime at API"
```

---

## Final check

- [ ] **Run the full test suite one last time**

```bash
./mvnw test
```

Expected: all tests green.

- [ ] **Smoke test the app manually**

```bash
./mvnw spring-boot:run
```

Then in a second terminal:
```bash
# List seeded vehicle models
curl http://localhost:8080/api/vehicle-models

# Create a reservation using seeded model id=1 and customer id=1
curl -s -X POST http://localhost:8080/api/reservations \
  -H 'Content-Type: application/json' \
  -d '{"vehicleModelId":1,"customerId":1,"startDateTime":"2027-06-01T10:00:00","numberOfDays":3}'

# Check Swagger UI
open http://localhost:8080/swagger-ui/index.html
```
