package com.iwonabendig.car_rental.exceptions;

public class NoCarAvailableException extends RuntimeException {
    public NoCarAvailableException(String message) {
        super(message);
    }
}
