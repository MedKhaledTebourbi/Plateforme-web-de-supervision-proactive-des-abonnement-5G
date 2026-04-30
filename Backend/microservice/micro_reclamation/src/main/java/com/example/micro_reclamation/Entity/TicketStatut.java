package com.example.micro_reclamation.Entity;

public enum TicketStatut {
    OUVERT("Ouvert", "Ticket créé en attente de traitement"),
    EN_COURS("En cours", "Ticket en cours de traitement"),
    EN_ATTENTE("En attente", "Ticket en attente d'informations"),
    RESOLU("Résolu", "Ticket résolu en attente de validation"),
    CLOS("Clos", "Ticket définitivement clos"),
    ANNULE("Annulé", "Ticket annulé");

    private final String libelle;
    private final String description;

    TicketStatut(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }
}