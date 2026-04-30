package com.example.microserviceia.controller;

import com.example.microserviceia.dto.TicketPredictionRequest;
import com.example.microserviceia.dto.TicketPredictionResponse;
import com.example.microserviceia.service.TicketPrioriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST — Classification de priorité des tickets 5G
 *
 * Appelé par le micro-service réclamation dès qu'un ticket est créé.
 *
 * POST /api/ia/tickets/predict-priorite
 */
@RestController
@RequestMapping("/api/ia/tickets")
@RequiredArgsConstructor

@Slf4j
public class TicketPrioriteController {

    private final TicketPrioriteService ticketPrioriteService;

    /**
     * Prédit la priorité d'un ticket.
     *
     * Body :
     * {
     *   "ticketId": 42,
     *   "typePanne": "RESEAU",
     *   "region": "TUNIS",
     *   "nombreReclamations": 85,
     *   "description": "panne majeure zone dense",
     *   "heure": 14,
     *   "zonePopulation": "HAUTE"
     * }
     */
    @PostMapping("/predict-priorite")
    public ResponseEntity<TicketPredictionResponse> predictPriorite(
            @RequestBody TicketPredictionRequest request) {

        log.info("[Controller] Prédiction priorité — ticketId={}", request.getTicketId());
        TicketPredictionResponse response = ticketPrioriteService.predirePriorite(request);

        if (response.isFallback()) {
            // On retourne 200 avec le fallback pour ne pas bloquer le flux métier
            log.warn("[Controller] Réponse fallback pour ticketId={}", request.getTicketId());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check rapide du service IA Ticket
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("TicketIA service UP");
    }
}