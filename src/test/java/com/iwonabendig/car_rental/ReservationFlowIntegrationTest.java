package com.iwonabendig.car_rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iwonabendig.car_rental.dto.CreateReservationRequest;
import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.repository.CarRepository;
import com.iwonabendig.car_rental.repository.ReservationRepository;
import com.iwonabendig.car_rental.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CarRepository carRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationService reservationService;

    @BeforeEach
    void cleanDatabase() {
        // delete reservations first to avoid FK violations
        reservationRepository.deleteAll();
        carRepository.deleteAll();
    }

    @Test
    void postReservation_then_availabilityShowsDecrementedCount() throws Exception {
        carRepository.save(Car.builder().carType(CarType.SEDAN).registrationNumber("INT-1").active(true).build());
        carRepository.save(Car.builder().carType(CarType.SEDAN).registrationNumber("INT-2").active(true).build());

        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);

        // before booking, both cars are available
        mockMvc.perform(get("/api/reservations/availability")
                        .param("carType", "SEDAN")
                        .param("startDateTime", start.toString())
                        .param("numberOfDays", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        CreateReservationRequest request = new CreateReservationRequest(
                CarType.SEDAN, start, 3, "Integration Tester");

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customerName").value("Integration Tester"));

        // after booking, only the other car remains available for that window
        mockMvc.perform(get("/api/reservations/availability")
                        .param("carType", "SEDAN")
                        .param("startDateTime", start.toString())
                        .param("numberOfDays", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void postReservation_returns400_whenPayloadFailsValidation() throws Exception {
        String invalidJson = """
                {"carType":"SEDAN","startDateTime":"%s","numberOfDays":0,"customerName":""}
                """.formatted(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availability_returns400_whenCarTypeIsUnknown() throws Exception {
        mockMvc.perform(get("/api/reservations/availability")
                        .param("carType", "FERRARI")
                        .param("startDateTime", LocalDateTime.now().plusDays(1).toString())
                        .param("numberOfDays", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("FERRARI")));
    }

    /**
     * Race-condition demonstration. Currently disabled because ReservationService.reserve()
     * has a TOCTOU window between the booked-cars query and the save() — multiple threads
     * can pick the same car. Enable this once a pessimistic lock (or DB exclusion constraint)
     * is wired in, then it should pass cleanly.
     */
    @Test
    @Disabled("Demonstrates known race in reserve(); enable once locking is added")
    void concurrent_bookings_onlyOneSucceeds_whenSingleCarAvailable() throws Exception {
        carRepository.save(Car.builder().carType(CarType.VAN).registrationNumber("V-001").active(true).build());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        LocalDateTime startDt = LocalDateTime.now().plusDays(5).withNano(0);

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        reservationService.reserve(CarType.VAN, startDt, 2, "Concurrent " + idx);
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

        assertThat(successes.get())
                .as("exactly one thread should have booked the only available car")
                .isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
    }
}
