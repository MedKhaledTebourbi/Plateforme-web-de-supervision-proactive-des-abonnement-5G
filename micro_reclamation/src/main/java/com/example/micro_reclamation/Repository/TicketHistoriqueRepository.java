package com.example.micro_reclamation.Repository;

import com.example.micro_reclamation.Entity.TicketHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TicketHistoriqueRepository extends JpaRepository<TicketHistorique, Long> {
    List<TicketHistorique> findByTicketIdOrderByDateActionDesc(Long ticketId);
    List<TicketHistorique> findByTicketIdOrderByDateActionAsc(Long ticketId);
    List<TicketHistorique> findByUtilisateurIdOrderByDateActionDesc(Long utilisateurId);
}