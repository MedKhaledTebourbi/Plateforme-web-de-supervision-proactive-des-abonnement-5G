package com.example.microserviceia.dto;

import lombok.*;

/**
 * DTO envoyé au script Python predict_ticket.py
 * Construit depuis l'entité Ticket du micro-service réclamation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPredictionRequest {

    private Long   ticketId;
    private String typePanne;
    private String region;
    private int    nombreReclamations;
    private String description;
    private int    heure;              // heure de création (0-23)
    private String zonePopulation;    // FAIBLE | MOYENNE | HAUTE | TRES_HAUTE
    private String zoneNom;
    private String priorite;
}