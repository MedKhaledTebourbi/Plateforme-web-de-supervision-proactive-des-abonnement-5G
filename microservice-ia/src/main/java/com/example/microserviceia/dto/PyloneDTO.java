package com.example.microserviceia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PyloneDTO {
    private Long id;
    private String nom;
    private Double capaciteMax;
    private Double chargeActuelle;
    private Boolean estBloque;

    public Double getTauxUtilisation() {
        if (capaciteMax == null || capaciteMax == 0) return 0.0;
        return (chargeActuelle / capaciteMax) * 100;
    }
}