package com.example.microserviceia.entity;

public enum SaturationStatus {
    NORMAL,      // taux < 60%
    ATTENTION,   // taux entre 60-80%
    SATURE,      // taux > 80% OU >50% pylones saturés
    CRITIQUE     // taux > 95%
}