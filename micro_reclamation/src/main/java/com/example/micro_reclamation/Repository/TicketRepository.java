package com.example.micro_reclamation.Repository;

import com.example.micro_reclamation.Entity.Ticket;
import com.example.micro_reclamation.Entity.TicketStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsByZoneIdAndStatut(Long zoneId, String statut);

    List<Ticket> findByRegion(String region);
    List<Ticket> findByZoneId(Long zoneId);

    List<Ticket> findByZoneNom(String zoneNom);

    List<Ticket> findByStatut(TicketStatut ticketStatut);

    Long countByStatut(TicketStatut s);
    @Query("SELECT t FROM Ticket t WHERE MONTH(t.dateCreation) = :mois AND YEAR(t.dateCreation) = :annee")
    List<Ticket> findByMonth(@Param("mois") int mois, @Param("annee") int annee);
}