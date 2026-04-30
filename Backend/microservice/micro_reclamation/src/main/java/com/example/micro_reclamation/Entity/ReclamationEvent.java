package com.example.micro_reclamation.Entity;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReclamationEvent {

    // ── Discriminant : filtre côté consommateur ──────────────
    // Toujours valorisé à "RECLAMATION" par le producteur
    @Builder.Default
    private String eventType = "RECLAMATION";
    // commun aux deux types
    private String codeReclamation;

    private String    adresse;
    private String    typeReclamation;
    private LocalDate dateReclamation;
    private Double    latitude;
    private Double    longitude;
    private Long      pyloneId;
    private String    pyloneNom;
    // champs CLOTURE_RECLAMATION
    private String motifCloture;
    private Long   clotureParId;
    private String clotureParNom;
}