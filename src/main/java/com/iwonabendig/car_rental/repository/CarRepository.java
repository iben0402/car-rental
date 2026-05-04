package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Car;
import com.iwonabendig.car_rental.enums.CarType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {
    List<Car> findByCarType(CarType carType);
    List<Car> findByCarTypeAndActiveTrue(CarType carType);
}
