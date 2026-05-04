package com.iwonabendig.car_rental.dto;

import com.iwonabendig.car_rental.enums.CarType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotNull CarType carType,
        @NotNull @Future LocalDateTime startDateTime,
        @NotNull @Min(1) @Max(30) Integer numberOfDays,
        @NotBlank @Size(min = 2, max = 100) String customerName
) {}