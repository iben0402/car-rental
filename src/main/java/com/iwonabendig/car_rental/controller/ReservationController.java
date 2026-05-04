package com.iwonabendig.car_rental.controller;

import com.iwonabendig.car_rental.dto.CarSummaryResponse;
import com.iwonabendig.car_rental.dto.CreateReservationRequest;
import com.iwonabendig.car_rental.dto.ReservationResponse;
import com.iwonabendig.car_rental.enums.CarType;
import com.iwonabendig.car_rental.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Validated
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request) {

        ReservationResponse response = reservationService.reserve(
                request.carType(),
                request.startDateTime(),
                request.numberOfDays(),
                request.customerName()
        );

        URI location = URI.create("/api/reservations/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/availability")
    public List<CarSummaryResponse> availability(
            @RequestParam CarType carType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDateTime,
            @RequestParam @Min(1) @Max(30) int numberOfDays) {

        return reservationService.findAvailableCars(carType, startDateTime, numberOfDays);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }
}
