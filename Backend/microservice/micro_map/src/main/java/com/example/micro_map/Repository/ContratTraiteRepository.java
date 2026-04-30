package com.example.micro_map.Repository;

import com.example.micro_map.Entity.ContratTraite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratTraiteRepository extends JpaRepository<ContratTraite, Long> {
    boolean existsByContratId(Long contratId);
    // ✅ Nouvelle méthode — recherche par contratId ET typeEvenement
    boolean existsByContratIdAndTypeEvenement(Long contratId, String typeEvenement);
}