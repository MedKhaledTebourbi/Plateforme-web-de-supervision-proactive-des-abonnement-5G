package com.example.micro_map.Repository;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.Pylone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByAdresse(String adresse);
    List<Client> findByPyloneIsNull();
    // Recherche par contratId pour la suppression lors d'ANNULATION
    Optional<Client> findByContratId(Long contratId);

    // Vérifier si un client existe déjà pour ce contrat (idempotence)
    boolean existsByContratId(Long contratId);
}
