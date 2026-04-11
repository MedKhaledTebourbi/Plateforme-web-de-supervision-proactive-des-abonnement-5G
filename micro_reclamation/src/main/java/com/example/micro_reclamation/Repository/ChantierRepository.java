package com.example.micro_reclamation.Repository;

import com.example.micro_reclamation.Entity.Chantier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChantierRepository extends JpaRepository<Chantier, Long> {
    List<Chantier> findByPyloneId(Long pyloneId);
    List<Chantier> findByStatut(String statut);
    List<Chantier> findByRegion(String region);
    List<Chantier> findByTechnicienId(Long technicienId);
    boolean existsByPyloneIdAndStatut(Long pyloneId, String statut);
    @Query("SELECT c FROM Chantier c WHERE MONTH(c.dateCreation) = :mois AND YEAR(c.dateCreation) = :annee")
    List<Chantier> findByMonth(@Param("mois") int mois, @Param("annee") int annee);
}