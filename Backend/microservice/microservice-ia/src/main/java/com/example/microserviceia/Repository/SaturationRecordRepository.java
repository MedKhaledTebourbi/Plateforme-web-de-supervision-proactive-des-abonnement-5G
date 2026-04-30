package com.example.microserviceia.Repository;

import com.example.microserviceia.entity.SaturationRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
public interface SaturationRecordRepository extends JpaRepository<SaturationRecord, Long> {
    List<SaturationRecord> findByZoneIdAndTimestampAfterOrderByTimestamp(Long zoneId, LocalDateTime since);
   
    List<SaturationRecord> findByZoneIdOrderByTimestampDesc(Long zoneId);

    void deleteByTimestampBefore(LocalDateTime before);

    List<SaturationRecord> findByTimestampAfterOrderByTimestamp(LocalDateTime localDateTime);

    // Historique par zone

    // Top 50 par zone (méthode native Spring Data)
    List<SaturationRecord> findTop50ByZoneIdOrderByTimestampDesc(Long zoneId);

    // Top N avec Pageable
    @Query("SELECT sr FROM SaturationRecord sr WHERE sr.zoneId = :zoneId ORDER BY sr.timestamp DESC")
    List<SaturationRecord> findTopNByZoneId(@Param("zoneId") Long zoneId, Pageable pageable);

    // Méthode utilitaire
    default List<SaturationRecord> findRecentByZoneId(Long zoneId, int limit) {
        return findTopNByZoneId(zoneId, Pageable.ofSize(limit));
    }
}
