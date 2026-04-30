package com.example.micro_reclamation.Entity;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneDTO {
    private Long id;
    private String nom;
    private Double latitudeCentre;
    private Double longitudeCentre;
    private Double rayonCouverture;
    private String description;
}