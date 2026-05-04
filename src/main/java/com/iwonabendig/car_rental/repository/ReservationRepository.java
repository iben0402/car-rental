package com.iwonabendig.car_rental.repository;

import com.iwonabendig.car_rental.entity.Reservation;
import com.iwonabendig.car_rental.enums.CarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("""
    SELECT r FROM Reservation r
    WHERE r.car.id = :carId
      AND r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
      AND r.startDateTime < :end
      AND r.endDateTime > :start
    """)
    List<Reservation> findOverlappingForCar(
            @Param("carId") Long carId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
    SELECT r.car.id FROM Reservation r
    WHERE r.car.carType = :type
      AND r.status = com.iwonabendig.car_rental.enums.ReservationStatus.ACTIVE
      AND r.startDateTime < :end
      AND r.endDateTime > :start
""")
    List<Long> findBookedCarIdsByTypeAndWindow(
            @Param("type") CarType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
