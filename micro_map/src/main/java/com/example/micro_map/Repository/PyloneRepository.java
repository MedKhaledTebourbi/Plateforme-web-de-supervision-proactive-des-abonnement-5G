package com.example.micro_map.Repository;

import com.example.micro_map.Entity.Pylone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PyloneRepository extends JpaRepository<Pylone, Long> {
    @Query("SELECT p FROM Pylone p WHERE p.zoneReseau.zone_id = :zoneId")
    List<Pylone> findByZoneId(@Param("zoneId") Long zoneId);
}
