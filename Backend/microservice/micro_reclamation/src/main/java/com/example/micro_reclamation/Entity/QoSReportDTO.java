package com.example.micro_reclamation.Entity;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QoSReportDTO {

    private double tempsMoyenResolution;
    private double tauxSatisfaction;
    private double disponibiliteReseau;

    private long totalIncidents;
    private long incidentsResolus;

    private double tauxSLA;
    private long pannesCritiques;
}