package com.example.microserviceia.dto;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuideRequest {
    private Long   ticketId;
    private String typePanne;
    private String description;
    private int    nombreReclamations;
    private String zonePopulation;
    private String region;
    private int    heure;
}
