package com.example.micro_reclamation.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PyloneDTO {
    private Long id;

    private String nom;

    private Double latitude;
    private Double longitude;

    private Double capaciteMax;
    private Double chargeActuelle;
    private Double rayonCouverture;
    private Boolean estBloque = false;
}
