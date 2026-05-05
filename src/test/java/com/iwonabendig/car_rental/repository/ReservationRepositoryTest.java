package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReservationRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ReservationRepository reservationRepository;

    private Car sedan1;
    private Car sedan2;
    private Car suv1;
    private LocalDateTime base;

    @BeforeEach
    void setUp() {
        sedan1 = em.persist(Car.builder()
                .carType(CarType.SEDAN).registrationNumber("S-001").active(true).build());
        sedan2 = em.persist(Car.builder()
                .carType(CarType.SEDAN).registrationNumber("S-002").active(true).build());
        suv1 = em.persist(Car.builder()
                .carType(CarType.SUV).registrationNumber("U-001").active(true).build());
        base = LocalDateTime.now()
                .plusDays(7)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private Reservation persist(Car car, LocalDateTime start, LocalDateTime end, ReservationStatus status) {
        Reservation r = Reservation.builder()
                .car(car)
                .customerName("Test")
                .startDateTime(start)
                .endDateTime(end)
                .status(status)
                .build();
        return em.persist(r);
    }

    // ── findOverlappingForCar ────────────────────────────────────────────────

    @Test
    void overlap_emptyWhenNoReservations() {
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(2));

        assertThat(result).isEmpty();
    }

    @Test
    void overlap_excludesReservationTouchingAtEndOfWindow() {
        // existing ends exactly when query window starts → half-open intervals don't overlap
        persist(sedan1, base, base.plusDays(1), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base.plusDays(1), base.plusDays(3));

        assertThat(result).isEmpty();
    }

    @Test
    void overlap_excludesReservationTouchingAtStartOfWindow() {
        // existing starts exactly when query window ends → no overlap
        persist(sedan1, base.plusDays(2), base.plusDays(4), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(2));

        assertThat(result).isEmpty();
    }

    @Test
    void overlap_includesIdenticalRange() {
        persist(sedan1, base, base.plusDays(2), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(2));

        assertThat(result).hasSize(1);
    }

    @Test
    void overlap_includesFullyContainedReservation() {
        // existing [base, base+5d) wholly contains query window [base+1d, base+3d)
        persist(sedan1, base, base.plusDays(5), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base.plusDays(1), base.plusDays(3));

        assertThat(result).hasSize(1);
    }

    @Test
    void overlap_includesFullyContainingReservation() {
        // existing [base+1d, base+3d) is wholly contained in query window [base, base+5d)
        persist(sedan1, base.plusDays(1), base.plusDays(3), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(5));

        assertThat(result).hasSize(1);
    }

    @Test
    void overlap_includesPartialOverlapAtStart() {
        // existing [base, base+3d), window [base+2d, base+5d) → overlap on [base+2d, base+3d)
        persist(sedan1, base, base.plusDays(3), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base.plusDays(2), base.plusDays(5));

        assertThat(result).hasSize(1);
    }

    @Test
    void overlap_excludesCancelledReservations() {
        persist(sedan1, base, base.plusDays(2), ReservationStatus.CANCELLED);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(2));

        assertThat(result).isEmpty();
    }

    @Test
    void overlap_excludesReservationsForDifferentCar() {
        persist(sedan2, base, base.plusDays(2), ReservationStatus.ACTIVE);
        em.flush();

        List<Reservation> result = reservationRepository.findOverlappingForCar(
                sedan1.getId(), base, base.plusDays(2));

        assertThat(result).isEmpty();
    }

    // ── findBookedCarIdsByTypeAndWindow ──────────────────────────────────────

    @Test
    void bookedIds_returnsOnlyOverlappingCarsOfRequestedType() {
        persist(sedan1, base, base.plusDays(2), ReservationStatus.ACTIVE);
        persist(suv1, base, base.plusDays(2), ReservationStatus.ACTIVE);
        em.flush();

        List<Long> sedans = reservationRepository.findBookedCarIdsByTypeAndWindow(
                CarType.SEDAN, base, base.plusDays(2));

        assertThat(sedans).containsExactly(sedan1.getId());
    }

    @Test
    void bookedIds_excludesCancelledReservations() {
        persist(sedan1, base, base.plusDays(2), ReservationStatus.CANCELLED);
        em.flush();

        List<Long> sedans = reservationRepository.findBookedCarIdsByTypeAndWindow(
                CarType.SEDAN, base, base.plusDays(2));

        assertThat(sedans).isEmpty();
    }

    @Test
    void bookedIds_excludesReservationsOutsideWindow() {
        // existing ends at base+1d; queried window starts at base+1d (touch but no overlap)
        persist(sedan1, base, base.plusDays(1), ReservationStatus.ACTIVE);
        em.flush();

        List<Long> sedans = reservationRepository.findBookedCarIdsByTypeAndWindow(
                CarType.SEDAN, base.plusDays(1), base.plusDays(3));

        assertThat(sedans).isEmpty();
    }

    @Test
    void bookedIds_returnsBothBookedCars_whenBothOverlap() {
        persist(sedan1, base, base.plusDays(2), ReservationStatus.ACTIVE);
        persist(sedan2, base.plusDays(1), base.plusDays(3), ReservationStatus.ACTIVE);
        em.flush();

        List<Long> sedans = reservationRepository.findBookedCarIdsByTypeAndWindow(
                CarType.SEDAN, base, base.plusDays(2));

        assertThat(sedans).containsExactlyInAnyOrder(sedan1.getId(), sedan2.getId());
    }
}
