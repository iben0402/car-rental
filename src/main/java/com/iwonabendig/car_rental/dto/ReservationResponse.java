package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.enums.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long carId,
        CarType carType,
        String registrationNumber,
        String customerName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        ReservationStatus status
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getCar().getId(),
                reservation.getCar().getCarType(),
                reservation.getCar().getRegistrationNumber(),
                reservation.getCustomerName(),
                reservation.getStartDateTime(),
                reservation.getEndDateTime(),
                reservation.getStatus()
        );
    }
}