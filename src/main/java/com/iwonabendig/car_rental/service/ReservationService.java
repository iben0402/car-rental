package com.iwonabendig.car_rental.service;

import com.iwonabendig.car_rental.dto.CarSummaryResponse;
import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.exceptions.InvalidReservationException;
import com.iwonabendig.car_rental.exceptions.NoCarAvailableException;
import com.iwonabendig.car_rental.exceptions.ReservationNotFoundException;
import com.iwonabendig.car_rental.repository.CarRepository;
import com.iwonabendig.car_rental.repository.ReservationRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {
    private final CarRepository carRepository;
    private final ReservationRepository reservationRepository;

    public ReservationResponse reserve(CarType carType, LocalDateTime startDateTime, int numberOfDays, String customerName) {
        if(numberOfDays < 1) throw new InvalidReservationException("Invalid reservation: reservation to short.");
        if(startDateTime.isBefore(LocalDateTime.now())) throw new InvalidReservationException("Invalid reservation: reservation in the past.");

        LocalDateTime endDateTime = startDateTime.plusDays(numberOfDays);

        List<Car> candidates = carRepository.findByCarTypeAndActiveTrue(carType);
        if (candidates.isEmpty()) throw new NoCarAvailableException("No active cars of type " + carType + " exist.");

        List<Long> bookedCarsIds = reservationRepository.findBookedCarIdsByTypeAndWindow(carType, startDateTime, endDateTime);

        List<Car> notBookedAvailableCars = candidates.stream().filter(car -> !bookedCarsIds.contains(car.getId())).toList();
        if(notBookedAvailableCars.isEmpty()) throw new NoCarAvailableException("All cars of type " + carType + " are booked.");

        // TODO: for now pick first car, implement smarter logic later
        Car selectedCar = notBookedAvailableCars.get(0);

        Reservation reservation = Reservation.builder().car(selectedCar).customerName(customerName).startDateTime(startDateTime).endDateTime(endDateTime).build();
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    public void cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        reservation.cancel();
    }

    @Transactional(readOnly = true)
    public List<CarSummaryResponse> findAvailableCars(
            CarType carType, LocalDateTime startDateTime, int days) {
        LocalDateTime end = startDateTime.plusDays(days);
        List<Car> all = carRepository.findByCarTypeAndActiveTrue(carType);
        Set<Long> bookedIds = new HashSet<>(
                reservationRepository.findBookedCarIdsByTypeAndWindow(
                        carType, startDateTime, end));
        return all.stream()
                .filter(car -> !bookedIds.contains(car.getId()))
                .map(CarSummaryResponse::from)
                .toList();
    }
}