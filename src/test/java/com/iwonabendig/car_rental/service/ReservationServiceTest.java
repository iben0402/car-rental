package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CarSummaryResponse;
import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.ReservationStatus;
import com.iwonabendig.car_rental.exceptions.InvalidReservationException;
import com.iwonabendig.car_rental.exceptions.NoCarAvailableException;
import com.iwonabendig.car_rental.exceptions.ReservationNotFoundException;
import com.iwonabendig.car_rental.repository.CarRepository;
import com.iwonabendig.car_rental.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private CarRepository carRepository;
    @Mock private ReservationRepository reservationRepository;
    @InjectMocks private ReservationService service;

    private LocalDateTime futureStart;

    @BeforeEach
    void setUp() {
        futureStart = LocalDateTime.now().plusDays(1);
    }

    private Car activeCar(long id, CarType type) {
        return Car.builder()
                .id(id)
                .carType(type)
                .registrationNumber("REG-" + id)
                .active(true)
                .build();
    }

    private void stubSaveAssigningId(long assignedId) {
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            return Reservation.builder()
                    .id(assignedId)
                    .car(r.getCar())
                    .customerName(r.getCustomerName())
                    .startDateTime(r.getStartDateTime())
                    .endDateTime(r.getEndDateTime())
                    .status(r.getStatus())
                    .build();
        });
    }

    @Test
    void reserve_returnsResponse_whenCarIsAvailable() {
        Car car = activeCar(1L, CarType.SEDAN);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SEDAN)).thenReturn(List.of(car));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of());
        stubSaveAssigningId(42L);

        ReservationResponse response = service.reserve(CarType.SEDAN, futureStart, 3, "Alice");

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.carId()).isEqualTo(1L);
        assertThat(response.customerName()).isEqualTo("Alice");
        assertThat(response.startDateTime()).isEqualTo(futureStart);
        assertThat(response.endDateTime()).isEqualTo(futureStart.plusDays(3));
        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getEndDateTime()).isEqualTo(futureStart.plusDays(3));
        assertThat(captor.getValue().getCar().getId()).isEqualTo(1L);
    }

    @Test
    void reserve_throwsNoCarAvailable_whenAllCarsOfTypeAreBooked() {
        Car car = activeCar(1L, CarType.SUV);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SUV)).thenReturn(List.of(car));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SUV), any(), any()))
                .thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.reserve(CarType.SUV, futureStart, 1, "Bob"))
                .isInstanceOf(NoCarAvailableException.class)
                .hasMessageContaining("All cars");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_throwsNoCarAvailable_whenNoActiveCarsExist() {
        when(carRepository.findByCarTypeAndActiveTrue(CarType.VAN)).thenReturn(List.of());

        assertThatThrownBy(() -> service.reserve(CarType.VAN, futureStart, 2, "Carla"))
                .isInstanceOf(NoCarAvailableException.class)
                .hasMessageContaining("No active cars");

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserve_picksAnAvailableCar_whenMultipleAreFree() {
        Car carA = activeCar(1L, CarType.SEDAN);
        Car carB = activeCar(2L, CarType.SEDAN);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SEDAN)).thenReturn(List.of(carA, carB));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of());
        stubSaveAssigningId(99L);

        ReservationResponse response = service.reserve(CarType.SEDAN, futureStart, 1, "Dan");

        assertThat(response.carId()).isIn(1L, 2L);
    }

    @Test
    void reserve_picksRemainingCar_whenOneOfTwoIsBooked() {
        Car carA = activeCar(1L, CarType.SEDAN);
        Car carB = activeCar(2L, CarType.SEDAN);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SEDAN)).thenReturn(List.of(carA, carB));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of(1L));
        stubSaveAssigningId(50L);

        ReservationResponse response = service.reserve(CarType.SEDAN, futureStart, 1, "Eve");

        assertThat(response.carId()).isEqualTo(2L);
    }

    @Test
    void reserve_succeeds_whenExistingReservationsDoNotOverlapWindow() {
        // Mocks emulate the repo: the booked-ids query returns nothing for this window,
        // so the service treats the car as available. Real overlap math is exercised in @DataJpaTest.
        Car car = activeCar(1L, CarType.SEDAN);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SEDAN)).thenReturn(List.of(car));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of());
        stubSaveAssigningId(7L);

        ReservationResponse response = service.reserve(CarType.SEDAN, futureStart, 2, "Frank");

        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void reserve_throwsInvalidReservation_whenNumberOfDaysIsZero() {
        assertThatThrownBy(() -> service.reserve(CarType.SEDAN, futureStart, 0, "X"))
                .isInstanceOf(InvalidReservationException.class);
        verifyNoInteractions(carRepository, reservationRepository);
    }

    @Test
    void reserve_throwsInvalidReservation_whenNumberOfDaysIsNegative() {
        assertThatThrownBy(() -> service.reserve(CarType.SEDAN, futureStart, -1, "X"))
                .isInstanceOf(InvalidReservationException.class);
        verifyNoInteractions(carRepository, reservationRepository);
    }

    @Test
    void reserve_throwsInvalidReservation_whenStartIsInThePast() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> service.reserve(CarType.SEDAN, past, 1, "X"))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessageContaining("past");

        verifyNoInteractions(carRepository, reservationRepository);
    }

    @Test
    void cancel_marksReservationAsCancelled() {
        Car car = activeCar(1L, CarType.SEDAN);
        Reservation reservation = Reservation.builder()
                .id(5L)
                .car(car)
                .customerName("Greta")
                .status(ReservationStatus.ACTIVE)
                .startDateTime(futureStart)
                .endDateTime(futureStart.plusDays(1))
                .build();
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        service.cancel(5L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancel_throwsReservationNotFound_whenIdMissing() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(999L))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void findAvailableCars_returnsOnlyUnbookedCars() {
        Car carA = activeCar(1L, CarType.SEDAN);
        Car carB = activeCar(2L, CarType.SEDAN);
        when(carRepository.findByCarTypeAndActiveTrue(CarType.SEDAN)).thenReturn(List.of(carA, carB));
        when(reservationRepository.findBookedCarIdsByTypeAndWindow(eq(CarType.SEDAN), any(), any()))
                .thenReturn(List.of(2L));

        List<CarSummaryResponse> results = service.findAvailableCars(CarType.SEDAN, futureStart, 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(1L);
    }
}
