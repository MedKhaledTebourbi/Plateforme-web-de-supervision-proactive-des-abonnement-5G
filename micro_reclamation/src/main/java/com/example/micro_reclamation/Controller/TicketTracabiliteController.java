package com.example.micro_reclamation.Controller;


import com.example.micro_reclamation.Entity.Ticket;
import com.example.micro_reclamation.Entity.TicketHistorique;
import com.example.micro_reclamation.Entity.TicketMetricsDTO;
import com.example.micro_reclamation.Entity.TicketStatut;
import com.example.micro_reclamation.Service.TicketTracabiliteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets/tracabilite")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true", allowedHeaders = "*")
public class TicketTracabiliteController {

    private final TicketTracabiliteService tracabiliteService;

    @GetMapping("/{ticketId}/historique")
    public ResponseEntity<List<TicketHistorique>> getHistorique(@PathVariable Long ticketId) {
        return ResponseEntity.ok(tracabiliteService.getHistoriqueTicket(ticketId));
    }

    @GetMapping("/{ticketId}/metrics")
    public ResponseEntity<TicketMetricsDTO> getMetrics(@PathVariable Long ticketId) {
        return ResponseEntity.ok(tracabiliteService.getTicketMetrics(ticketId));
    }

    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        return ResponseEntity.ok(tracabiliteService.getGlobalTracabiliteStats());
    }

    @PutMapping("/{ticketId}/statut")
    public ResponseEntity<Ticket> updateStatut(
            @PathVariable Long ticketId,
            @RequestParam TicketStatut nouveauStatut,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom,
            @RequestParam(required = false) String commentaire) {

        Ticket updated = tracabiliteService.updateStatutTicketAvance(
                ticketId, nouveauStatut, technicienId, technicienNom, commentaire
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{ticketId}/intervention")
    public ResponseEntity<Ticket> addIntervention(
            @PathVariable Long ticketId,
            @RequestParam String intervention,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom) {

        Ticket ticket = tracabiliteService.ajouterIntervention(
                ticketId, intervention, technicienId, technicienNom
        );
        return ResponseEntity.ok(ticket);
    }
    
}