package com.example.microserviceia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoneReseauDTO {
    private Long zone_id;
    private String nom;
    private Double bandePassanteMax;
    private Double chargeActuelle;
    private List<PyloneDTO> pylones;

    public Double getTauxUtilisation() {
        if (bandePassanteMax == null || bandePassanteMax == 0) return 0.0;
        return (chargeActuelle / bandePassanteMax) * 100;
    }
    // Remplacez la méthode calculateRatioAuto

}