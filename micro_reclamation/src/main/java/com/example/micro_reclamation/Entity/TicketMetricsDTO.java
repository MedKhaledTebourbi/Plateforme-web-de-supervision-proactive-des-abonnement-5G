package com.example.micro_reclamation.Entity;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TicketMetricsDTO {
    private Long ticketId;
    private String zoneNom;
    private String region;
    private LocalDateTime dateCreation;
    private LocalDateTime dateDebutTraitement;
    private LocalDateTime dateFinTraitement;
    private Long dureeTraitementMinutes; // Temps de traitement total
    private Long dureePremiereReponseMinutes; // Temps avant première action
    private Integer nombreChangementsStatut;
    private Integer nombreInterventions;
    private List<ActionDTO> actions;

    @Data
    @Builder
    public static class ActionDTO {
        private LocalDateTime date;
        private String action;
        private String utilisateur;
        private String details;
    }
}