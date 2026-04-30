package com.example.microserviceia.dto;


import com.example.microserviceia.entity.SaturationStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SaturationReport {

    private Long zoneId;
    private String zoneNom;

    // État actuel
    private Double tauxUtilisation;
    private SaturationStatus statut;
    private Double anomalyScore;
    private Integer nbPylonesSatures;
    private Integer nbPylonesTotal;
    private Double ratioSatures;
    private Double tendance6h;

    // Prédiction
    private boolean saturationPredite;
    private Double heuresAvantSaturation;
    private LocalDateTime datePredicteSaturation;
    private Double confidencePrediction;
    private String messagePrediction;

    // Méta
    private String details;
    private LocalDateTime timestamp;
}