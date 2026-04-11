package com.example.microserviceia.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LinearRegressionResult {

    private Double slope;       // pente : % par heure
    private Double intercept;   // ordonnée à l'origine
    private Double rSquared;    // coefficient de détermination (0-1)
}