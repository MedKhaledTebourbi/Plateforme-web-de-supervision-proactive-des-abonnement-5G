package com.example.micro_reclamation.Entity;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicienReportDTO {

    private Long technicienId;

    private long ticketsTraites;
    private long ticketsClos;

    private long chantiersRealises;
    private long chantiersTermines;

    private double performance;
    private double tempsMoyenResolution;
    private double efficacite;
    private int chargeTravail;
}