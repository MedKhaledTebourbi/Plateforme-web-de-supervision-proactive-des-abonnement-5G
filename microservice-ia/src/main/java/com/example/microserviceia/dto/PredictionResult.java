package com.example.microserviceia.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PredictionResult {

    private Long zoneId;
    private boolean saturationPredite;

    // null si pas de saturation prévue
    private Double heuresAvantSaturation;
    private LocalDateTime datePredite;

    private Double vitesseAugmentation; // % par heure
    private Double rSquared;            // qualité de la régression (0-1)
    private Double confidence;          // indice de confiance global (0-1)
    private String message;
}