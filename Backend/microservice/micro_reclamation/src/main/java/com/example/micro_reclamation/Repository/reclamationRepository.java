package com.example.micro_reclamation.Repository;

import com.example.micro_reclamation.Entity.Reclamation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface reclamationRepository extends JpaRepository<Reclamation, Long> {
    // Dans reclamationRepository.java
    List<Reclamation> findByLatitudeBetweenAndLongitudeBetween(
            Double latMin, Double latMax, Double lonMin, Double lonMax
    );
    Optional<Reclamation> findByCodeReclamation(String codeReclamation);
}
