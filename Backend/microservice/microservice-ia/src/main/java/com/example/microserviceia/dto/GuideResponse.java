package com.example.microserviceia.dto;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GuideResponse {
    private Long         ticketId;
    private String       typePanne;
    private String       urgence;
    private String       action;
    private String       solution;
    private List<String> etapes;
    private String       prioriteAction;
    private String       tempsEstime;
    private boolean      automatisable;
    private List<String> outils;
    private String       raison;
    private double       confidenceMl;
    private String       modelVersion;
    private boolean      fallback;
    private String       error;
}