package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.enums.CarType;

public record CarSummaryResponse(
        Long id,
        CarType type,
        String registrationNumber
) {
    public static CarSummaryResponse from(Car car) {
        return new CarSummaryResponse(
                car.getId(),
                car.getCarType(),
                car.getRegistrationNumber()
        );
    }
}