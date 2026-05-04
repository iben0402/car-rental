package com.iwonabendig.car_rental.exceptions;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(Long reservationId) {
        super("Reservetion with ID: " + reservationId + " not found.");
    }
}
