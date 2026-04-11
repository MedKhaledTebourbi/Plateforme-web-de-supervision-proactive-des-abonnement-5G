package com.example.micro_reclamation.Entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportData {

    private int mois;
    private int annee;

    private long totalTickets;
    private long ticketsClos;
    private long ticketsOuverts;

    private long totalChantiers;
    private long chantiersTermines;

    private double tempsMoyenResolution;
    private double tempsMedianResolution;
}