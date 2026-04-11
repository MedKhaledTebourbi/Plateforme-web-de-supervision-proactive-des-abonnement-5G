package com.example.microserviceia.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneFeatureVector {

    private Long zoneId;
    private String zoneNom;

    // Feature 1 : taux global de la zone (0-100%)
    private Double tauxUtilisation;

    // Feature 2 : % de pylones saturés dans la zone
    private Double ratioSatures;

    // Feature 3 : taux moyen de tous les pylones
    private Double tauxMoyenPylones;

    // Feature 4 : écart-type des taux pylones (déséquilibre de charge)
    private Double ecartTypePylones;

    // Feature 5 : delta taux sur les 6 dernières heures
    private Double tendance6h;

    // Feature 6 : accélération (dérivée seconde de la tendance)
    private Double acceleration;

    // Feature 7 : nombre de pylones bloqués
    private Integer nbPylonesBloques;

    // Compteurs bruts
    private Integer nbPylonesSatures;
    private Integer nbPylonesTotal;
}
