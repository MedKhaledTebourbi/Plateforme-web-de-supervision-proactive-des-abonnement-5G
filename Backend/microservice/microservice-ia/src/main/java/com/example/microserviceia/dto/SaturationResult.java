package com.example.microserviceia.dto;


import com.example.microserviceia.entity.SaturationStatus;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SaturationResult {

    private Long zoneId;
    private SaturationStatus statut;
    private Double anomalyScore;       // entre -1 (anormal) et 1 (normal)
    private Double tauxUtilisation;
    private Integer nbPylonesSatures;
    private Integer nbPylonesTotal;
    private String details;            // message lisible pour les logs/API
}