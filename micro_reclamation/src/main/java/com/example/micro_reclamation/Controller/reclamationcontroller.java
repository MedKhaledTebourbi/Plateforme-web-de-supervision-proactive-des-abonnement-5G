package com.example.micro_reclamation.Controller;

import com.example.micro_reclamation.Entity.Reclamation;
import com.example.micro_reclamation.Entity.Ticket;
import com.example.micro_reclamation.Repository.TicketRepository;
import com.example.micro_reclamation.Service.reclamationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reclamation")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class reclamationcontroller {
    private final reclamationService ReclamationService;
    private final TicketRepository ticketRepository;

    @PutMapping("/reclamations/geocode")
    public List<Reclamation> geocodeEtSave() throws InterruptedException {
        return ReclamationService.ajouterCoordonneesEtSauvegarder();
    }
    @PutMapping("/{id}/affecter")
    public ResponseEntity<Ticket> affecterTicket(
            @PathVariable Long id,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom) {
        return ResponseEntity.ok(
                ReclamationService.affecterTicket(id, technicienId, technicienNom)
        );
    }


    @PostMapping("/generer")
    public String genererTickets() {
        try {
            ReclamationService.genererTicketsAutomatiquement();
            return "Tickets générés automatiquement";
        } catch (Exception e) {
            return "Erreur génération tickets : " + e.getMessage();
        }
    }

    // Dans reclamationcontroller.java
    @GetMapping("/region/{region}/technicien/{technicienId}")
    public List<Ticket> getTicketsParRegionEtTechnicien(
            @PathVariable String region,
            @PathVariable Long technicienId) {

        return ReclamationService.getTicketsParRegion(region).stream()
                .filter(t -> {
                    String statut = t.getStatut().name();
                    // OUVERT → visible par tous
                    if ("OUVERT".equals(statut)) return true;
                    // EN_COURS → visible seulement par le technicien assigné
                    if ("EN_COURS".equals(statut)) {
                        return technicienId.equals(t.getUpdatedBy());
                    }
                    // CLOS/ANNULE → visible par tous (historique)
                    return true;
                })
                .collect(Collectors.toList());
    }
    @PutMapping("/{ticketId}/statut")
    public Ticket updateStatut(
            @PathVariable Long ticketId,
            @RequestParam String statut,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom
    ) {
        return ReclamationService.updateStatutTicket(ticketId, statut, technicienId, technicienNom);
    }

    /**
     * Récupérer un ticket par ID
     */
    @GetMapping("/{ticketId}")
    public Ticket getTicketById(@PathVariable Long ticketId) {
        return ReclamationService.getTicketsParRegion(null).stream()
                .filter(t -> t.getId().equals(ticketId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
    }
    @GetMapping("/region/{region}")
    public List<Map<String, Object>> getTicketsParRegion(@PathVariable String region) {
        return ReclamationService.getTicketsParRegion(region).stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", t.getId());
                    map.put("zoneId", t.getZoneId());
                    map.put("zoneNom", t.getZoneNom());
                    map.put("region", t.getRegion());
                    map.put("typePanne", t.getTypePanne());
                    map.put("nombreReclamations", t.getNombreReclamations());
                    map.put("statut", t.getStatut());
                    map.put("dateCreation", t.getDateCreation());
                    map.put("createdBy", t.getCreatedBy());
                    map.put("createdByName", t.getCreatedByName());
                    map.put("updatedBy", t.getUpdatedBy());
                    map.put("updatedByName", t.getUpdatedByName());
                    map.put("dateMaj", t.getDateMaj());
                    map.put("assignedTo", t.getAssignedTo());
                    map.put("assignedToName", t.getAssignedToName());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
    }
    @GetMapping
    public List<Map<String, Object>> getAllTickets() {
        return ReclamationService.getTicketsParRegion(null).stream()
                .map(t -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", t.getId());
                    map.put("zoneId", t.getZoneId());
                    map.put("zoneNom", t.getZoneNom());
                    map.put("region", t.getRegion());
                    map.put("typePanne", t.getTypePanne());
                    map.put("nombreReclamations", t.getNombreReclamations());
                    map.put("statut", t.getStatut());
                    map.put("dateCreation", t.getDateCreation());
                    map.put("createdBy", t.getCreatedBy());
                    map.put("createdByName", t.getCreatedByName());
                    map.put("updatedBy", t.getUpdatedBy());
                    map.put("updatedByName", t.getUpdatedByName());
                    map.put("dateMaj", t.getDateMaj());
                    map.put("assignedTo", t.getAssignedTo());
                    map.put("assignedToName", t.getAssignedToName());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
    }
}
