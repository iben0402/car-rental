package com.iwonabendig.car_rental.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CarType {
    SEDAN(50.0),
    SUV(75.0),
    VAN(100.0);

    private final double dailyPriceEur;
}
