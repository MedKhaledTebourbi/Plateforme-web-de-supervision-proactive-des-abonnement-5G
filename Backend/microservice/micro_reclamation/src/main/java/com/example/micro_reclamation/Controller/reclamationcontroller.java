package com.example.micro_reclamation.Controller;

import com.example.micro_reclamation.Entity.Reclamation;
import com.example.micro_reclamation.Entity.ReclamationEvent;
import com.example.micro_reclamation.Entity.Ticket;
import com.example.micro_reclamation.Repository.TicketRepository;
import com.example.micro_reclamation.Service.reclamationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reclamation")
@RequiredArgsConstructor
public class reclamationcontroller {

    private final reclamationService ReclamationService;
    private final TicketRepository   ticketRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Géocodage bulk ────────────────────────────────────────
    @PutMapping("/reclamations/geocode")
    public List<Reclamation> geocodeEtSave() throws InterruptedException {
        return ReclamationService.ajouterCoordonneesEtSauvegarder();
    }

    // ── Affecter un ticket ────────────────────────────────────
    @PutMapping("/{id}/affecter")
    public ResponseEntity<Ticket> affecterTicket(
            @PathVariable Long id,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom) {
        return ResponseEntity.ok(
                ReclamationService.affecterTicket(id, technicienId, technicienNom));
    }

    // ── Générer tickets manuellement ─────────────────────────
    @PostMapping("/generer")
    public String genererTickets() {
        try {
            ReclamationService.genererTicketsAutomatiquement();
            return "Tickets générés automatiquement";
        } catch (Exception e) {
            return "Erreur génération tickets : " + e.getMessage();
        }
    }

    // ── Tickets par région + filtre technicien ────────────────
    @GetMapping("/region/{region}/technicien/{technicienId}")
    public List<Ticket> getTicketsParRegionEtTechnicien(
            @PathVariable String region,
            @PathVariable Long technicienId) {

        return ReclamationService.getTicketsParRegion(region).stream()
                .filter(t -> {
                    String statut = t.getStatut().name();
                    if ("EN_COURS".equals(statut))
                        return technicienId.equals(t.getUpdatedBy());
                    return true;
                })
                .collect(Collectors.toList());
    }

    // ── Mettre à jour statut ticket ───────────────────────────
    @PutMapping("/{ticketId}/statut")
    public Ticket updateStatut(
            @PathVariable Long ticketId,
            @RequestParam String statut,
            @RequestParam Long technicienId,
            @RequestParam String technicienNom) {
        return ReclamationService.updateStatutTicket(
                ticketId, statut, technicienId, technicienNom);
    }

    // ── Ticket par ID ─────────────────────────────────────────
    @GetMapping("/{ticketId}")
    public Ticket getTicketById(@PathVariable Long ticketId) {
        return ReclamationService.getTicketsParRegion(null).stream()
                .filter(t -> t.getId().equals(ticketId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));
    }

    // ── Tickets par région ────────────────────────────────────
    @GetMapping("/region/{region}")
    public List<Map<String, Object>> getTicketsParRegion(@PathVariable String region) {
        return mapTickets(ReclamationService.getTicketsParRegion(region));
    }

    // ── Tous les tickets ──────────────────────────────────────
    @GetMapping
    public List<Map<String, Object>> getAllTickets() {
        return mapTickets(ReclamationService.getTicketsParRegion(null));
    }

    // ── Priorité ticket ───────────────────────────────────────
    @PatchMapping("/{id}/priorite")
    public ResponseEntity<Void> updatePriorite(
            @PathVariable Long id,
            @RequestParam String priorite) {
        ReclamationService.updatePriorite(id, priorite);
        return ResponseEntity.ok().build();
    }

    // ── Test ──────────────────────────────────────────────────
    @GetMapping("/test")
    public String test() {
        return "message from backend successfully";
    }

    // ── Publier événement RECLAMATION ─────────────────────────
    @PostMapping("/test-kafka")
    public ResponseEntity<String> testKafka(
            @RequestParam String adresse,
            @RequestParam String typeReclamation) {

        ReclamationEvent event = ReclamationEvent.builder()
                .adresse(adresse)
                .typeReclamation(typeReclamation)
                .dateReclamation(LocalDate.now())
                .build();

        kafkaTemplate.send("reclamation-topic", event);
        return ResponseEntity.ok("✅ Événement envoyé : " + adresse);
    }

    // ── Publier événement CLOTURE_RECLAMATION ─────────────────
    @PostMapping("/{codeReclamation}/cloturer")
    public ResponseEntity<String> cloturerViaKafka(
            @PathVariable String codeReclamation,
            @RequestParam(required = false) String motif,
            @RequestParam Long clotureParId,
            @RequestParam String clotureParNom) {

        ReclamationEvent event = ReclamationEvent.builder()
                .eventType("CLOTURE_RECLAMATION")
                .codeReclamation(codeReclamation)
                .motifCloture(motif)
                .clotureParId(clotureParId)
                .clotureParNom(clotureParNom)
                .build();

        kafkaTemplate.send("reclamation-topic", event);
        return ResponseEntity.ok("🔒 Clôture envoyée pour : " + codeReclamation);
    }

    // ── Helper mapping ticket → Map ───────────────────────────
    private List<Map<String, Object>> mapTickets(List<Ticket> tickets) {
        return tickets.stream().map(t -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id",                  t.getId());
            map.put("zoneId",              t.getZoneId());
            map.put("zoneNom",             t.getZoneNom());
            map.put("region",              t.getRegion());
            map.put("typePanne",           t.getTypePanne());
            map.put("nombreReclamations",  t.getNombreReclamations());
            map.put("statut",              t.getStatut());
            map.put("dateCreation",        t.getDateCreation());
            map.put("createdBy",           t.getCreatedBy());
            map.put("createdByName",       t.getCreatedByName());
            map.put("updatedBy",           t.getUpdatedBy());
            map.put("updatedByName",       t.getUpdatedByName());
            map.put("dateMaj",             t.getDateMaj());
            map.put("assignedTo",          t.getAssignedTo());
            map.put("assignedToName",      t.getAssignedToName());
            return map;
        }).collect(Collectors.toList());
    }
}