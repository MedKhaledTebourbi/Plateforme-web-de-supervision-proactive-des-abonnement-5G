package com.example.micro_reclamation.Service;

import com.example.micro_reclamation.Entity.*;
import com.example.micro_reclamation.Repository.TicketRepository;
import com.example.micro_reclamation.Repository.reclamationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class reclamationService {

    private final reclamationRepository reclamationRepository;
    private final GeocodingService geocodingService;
    private final TicketRepository ticketRepository;
    private final ZoneClient zoneClient;
    private final UserClient userClient;
    private final ChantierService chantierService;  // ✅ correctement injecté
    private final PyloneClient pyloneClient;         // ✅ ajouté

    private static final int SEUIL_RECLAMATION = 2;

    // ============================================================
    // ✅ GÉOCODER + REMPLIR pyloneId pour toutes les réclamations
    // ============================================================
    public List<Reclamation> ajouterCoordonneesEtSauvegarder() throws InterruptedException {

        List<Reclamation> list = reclamationRepository.findAll();

        // Récupérer tous les pylônes UNE SEULE FOIS
        List<Map<String, Object>> pylones;
        try {
            pylones = pyloneClient.getAllPylones();
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de récupérer les pylônes : " + e.getMessage());
            pylones = new ArrayList<>();
        }

        for (Reclamation rec : list) {

            if (rec.getAdresse() != null && !rec.getAdresse().isEmpty()
                    && (rec.getLatitude() == null || rec.getLongitude() == null)) {

                double[] coords = geocodingService.getCoordinates(rec.getAdresse());

                if (coords != null) {
                    rec.setLatitude(coords[0]);
                    rec.setLongitude(coords[1]);

                    // Trouver le pylône le plus proche
                    Long pyloneIdTrouve = null;
                    String pyloneNomTrouve = null;
                    double distanceMin = Double.MAX_VALUE;

                    for (Map<String, Object> pylone : pylones) {
                        Double pyloneLat = pylone.get("latitude") != null
                                ? ((Number) pylone.get("latitude")).doubleValue() : null;
                        Double pyloneLon = pylone.get("longitude") != null
                                ? ((Number) pylone.get("longitude")).doubleValue() : null;
                        Double rayon = pylone.get("rayonCouverture") != null
                                ? ((Number) pylone.get("rayonCouverture")).doubleValue() : null;

                        if (pyloneLat == null || pyloneLon == null || rayon == null) continue;

                        double distance = calculerDistance(
                                coords[0], coords[1], pyloneLat, pyloneLon
                        );

                        if (distance <= rayon && distance < distanceMin) {
                            distanceMin = distance;
                            pyloneIdTrouve = ((Number) pylone.get("id")).longValue();
                            pyloneNomTrouve = (String) pylone.get("nom");
                        }
                    }

                    // ✅ Vérifier si le pylône est bloqué par un chantier
                    if (pyloneIdTrouve != null && chantierService.pyloneEstBloque(pyloneIdTrouve)) {
                        Chantier chantierActif = chantierService.getChantierActifByPylone(pyloneIdTrouve);
                        System.out.println("⚠️ Réclamation " + rec.getId()
                                + " → pylône " + pyloneNomTrouve
                                + " BLOQUÉ par chantier '" + chantierActif.getNom()
                                + "' — réclamation non affectée");
                        // On affecte quand même les coordonnées GPS
                        // mais PAS le pyloneId → réclamation exclue des tickets
                        Thread.sleep(1100);
                        continue;
                    }

                    // ✅ Pylône libre → affecter
                    rec.setPyloneId(pyloneIdTrouve);
                    rec.setPyloneNom(pyloneNomTrouve);

                    if (pyloneIdTrouve != null) {
                        System.out.println("📡 Réclamation " + rec.getId()
                                + " → pylône " + pyloneNomTrouve);
                    } else {
                        System.out.println("⚠️ Réclamation " + rec.getId()
                                + " → aucun pylône trouvé pour : " + rec.getAdresse());
                    }
                }

                Thread.sleep(1100);
            }
        }

        return reclamationRepository.saveAll(list);
    }

    // ============================================================
    // ✅ GÉNÉRER TICKETS AUTOMATIQUEMENT
    // ============================================================
    public void genererTicketsAutomatiquement() {

        List<Reclamation> reclamations = reclamationRepository.findAll();
        System.out.println("📋 Réclamations en base : " + reclamations.size());

        // Récupérer toutes les zones
        List<ZoneDTO> zones = null;
        try {
            zones = zoneClient.getZones();
            System.out.println("✅ Zones récupérées : " + zones.size());
        } catch (Exception e) {
            System.out.println("⚠️ ZoneClient indisponible : " + e.getMessage());
            return;
        }

        final List<ZoneDTO> zonesList = zones;

        // Grouper les réclamations par zone
        Map<String, List<Reclamation>> reclamationsParZone = new HashMap<>();

        for (Reclamation rec : reclamations) {
            if (rec.getLatitude() == null || rec.getLongitude() == null) {
                System.out.println("⚠️ Sans coordonnées : " + rec.getAdresse());
                continue;
            }

            // Ignorer les réclamations dont le pylône est bloqué par un chantier
            if (rec.getPyloneId() != null && chantierService.pyloneEstBloque(rec.getPyloneId())) {
                System.out.println("⚠️ Réclamation " + rec.getId()
                        + " ignorée — pylône " + rec.getPyloneId() + " bloqué par un chantier");
                continue;
            }

            String zoneNom = detecterZone(rec.getLatitude(), rec.getLongitude(), zonesList);
            if (zoneNom != null) {
                reclamationsParZone
                        .computeIfAbsent(zoneNom, k -> new ArrayList<>())
                        .add(rec);
            }
        }

        // Créer un ticket si le seuil est atteint
        for (Map.Entry<String, List<Reclamation>> entry : reclamationsParZone.entrySet()) {
            String zoneNom = entry.getKey();
            List<Reclamation> reclamationsZone = entry.getValue(); // ✅ réclamations de cette zone
            int count = reclamationsZone.size();

            System.out.println("🔍 Zone: " + zoneNom + " → " + count + " réclamations");

            if (count >= SEUIL_RECLAMATION) {

                // Vérifier si un ticket NON CLOS existe déjà pour cette zone
                List<Ticket> ticketsExistants = ticketRepository.findByZoneNom(zoneNom);
                boolean dejaCree = ticketsExistants.stream()
                        .anyMatch(t -> !"CLOS".equalsIgnoreCase(t.getStatut().name())
                                && !"ANNULE".equalsIgnoreCase(t.getStatut().name()));

                if (dejaCree) {
                    System.out.println("⏭️ Ticket actif déjà existant pour : " + zoneNom);
                    continue;
                }

                // ✅ Région depuis le nom de zone
                String region = detecterRegionDepuisNom(zoneNom);

                // ✅ Type de panne depuis les réclamations de CETTE zone
                String typePanne = detecterTypeParPylone(
                        reclamationsZone.get(0).getPyloneId(), reclamationsZone);

                // ✅ Zone population depuis la ZoneDTO
                ZoneDTO zoneDTO = zonesList.stream()
                        .filter(z -> zoneNom.equalsIgnoreCase(z.getNom()))
                        .findFirst().orElse(null);

                // ✅ Déduire zonePopulation depuis le rayon de couverture
                String zonePopulation = "MOYENNE"; // valeur par défaut
                if (zoneDTO != null && zoneDTO.getRayonCouverture() != null) {
                    double rayon = zoneDTO.getRayonCouverture();
                    if (rayon >= 10.0)      zonePopulation = "TRES_HAUTE";
                    else if (rayon >= 5.0)  zonePopulation = "HAUTE";
                    else if (rayon >= 2.0)  zonePopulation = "MOYENNE";
                    else                    zonePopulation = "FAIBLE";
                }

                // ✅ Technicien de la région
                Long technicienId = null;
                String technicienNom = "SYSTEM";
                try {
                    List<Map<String, Object>> techniciens = userClient.getTechniciensByRegion(region);
                    if (techniciens != null && !techniciens.isEmpty()) {
                        technicienId  = ((Number) techniciens.get(0).get("id")).longValue();
                        technicienNom = (String)  techniciens.get(0).get("username");
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ UserClient : " + e.getMessage());
                }

                // ✅ Créer le ticket avec zonePopulation
                Ticket ticket = Ticket.builder()
                        .zoneId(reclamationsZone.get(0).getId())
                        .zoneNom(zoneNom)
                        .region(region)
                        .typePanne(typePanne)
                        .nombreReclamations(count)
                        .zonePopulation(zonePopulation) // ✅ NOUVEAU
                        .statut(TicketStatut.OUVERT)
                        .dateCreation(LocalDateTime.now())
                        .createdBy(technicienId)
                        .createdByName(technicienNom)
                        .build();

                ticketRepository.save(ticket);

                System.out.println("✅ Ticket créé → zone: " + zoneNom
                        + " | région: " + region
                        + " | type: " + typePanne
                        + " | population: " + zonePopulation
                        + " | technicien: " + technicienNom);
            }
        }
    }
    private String detecterTypeParPylone(Long pyloneId, List<Reclamation> reclamations) {

        if (pyloneId == null || reclamations == null || reclamations.isEmpty()) {
            return "PANNE_TRANSPORT";
        }

        List<Reclamation> reclamationsPylone = reclamations.stream()
                .filter(r -> pyloneId.equals(r.getPyloneId()))
                .collect(Collectors.toList());

        if (reclamationsPylone.isEmpty()) {
            return "PANNE_TRANSPORT";
        }

        Map<String, Long> stats = reclamationsPylone.stream()
                .filter(r -> r.getTypeReclamation() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getTypeReclamation().toLowerCase(),
                        Collectors.counting()
                ));

        if (stats.isEmpty()) return "PANNE_TRANSPORT";

        String typeDominant = Collections.max(stats.entrySet(),
                Map.Entry.comparingByValue()).getKey();

        // ✅ Mapping compatible avec TYPES_PANNE_ALL du script Python
        Map<String, String> mapping = Map.ofEntries(
                Map.entry("signal faible",        "DEGRADATION_DEBIT"),
                Map.entry("pas de signal",         "PERTE_SIGNAL_5G"),
                Map.entry("réseau indisponible",   "PERTE_SIGNAL_5G"),
                Map.entry("perte de signal",       "PERTE_SIGNAL_5G"),
                Map.entry("internet lent",         "DEGRADATION_DEBIT"),
                Map.entry("débit faible",          "DEGRADATION_DEBIT"),
                Map.entry("pas d'internet",        "PERTE_SIGNAL_5G"),
                Map.entry("connexion instable",    "INTERFERENCE_COCANAL"),
                Map.entry("appel impossible",      "HANDOVER_ECHEC"),
                Map.entry("voix hachée",           "INTERFERENCE_COCANAL"),
                Map.entry("coupure appel",         "HANDOVER_ECHEC"),
                Map.entry("panne pylone",          "PANNE_ANTENNE"),
                Map.entry("antenne down",          "PANNE_RRH"),
                Map.entry("maintenance",           "MISE_A_JOUR_ECHOUEE"),
                Map.entry("coupure courant",       "PANNE_ALIMENTATION"),
                Map.entry("batterie faible",       "PANNE_ALIMENTATION"),
                Map.entry("surcharge",             "SURCHARGE_CELLULE"),
                Map.entry("latence",               "LATENCE_ELEVEE"),
                Map.entry("fibre",                 "COUPURE_FIBRE"),
                Map.entry("transport",             "PANNE_TRANSPORT"),
                Map.entry("inondation",            "INONDATION_SITE"),
                Map.entry("vandalisme",            "VANDALISME_PYLONE"),
                Map.entry("groupe electrogene",    "PANNE_GROUPE_ELECTROGENE"),
                Map.entry("climatisation",         "PANNE_CLIMATISATION")
        );

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (typeDominant.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Fallback : valeur par défaut valide pour le modèle Python
        return "PANNE_TRANSPORT";
    }

    // ============================================================
    // ✅ METTRE À JOUR LE STATUT D'UN TICKET
    // ============================================================
    public Ticket updateStatutTicket(Long ticketId, String statut,
                                     Long technicienId, String technicienNom) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        if ("EN_COURS".equalsIgnoreCase(statut)) {
            if (ticket.getUpdatedBy() != null
                    && !ticket.getUpdatedBy().equals(technicienId)
                    && "EN_COURS".equalsIgnoreCase(ticket.getStatut().name())) {
                throw new RuntimeException("Ticket déjà pris en charge par " + ticket.getUpdatedByName());
            }
            if (ticket.getDateDebutTraitement() == null) {
                ticket.setDateDebutTraitement(LocalDateTime.now());
            }
            ticket.setUpdatedBy(technicienId);
            ticket.setUpdatedByName(technicienNom);
        }

        if ("CLOS".equalsIgnoreCase(statut)) {
            if (ticket.getDateDebutTraitement() == null) {
                ticket.setDateDebutTraitement(ticket.getDateCreation() != null
                        ? ticket.getDateCreation() : LocalDateTime.now());
            }
            ticket.setDateFinTraitement(LocalDateTime.now());
            ticket.setUpdatedBy(technicienId);
            ticket.setUpdatedByName(technicienNom);
        }

        ticket.setStatut(TicketStatut.valueOf(statut));
        ticket.setDateMaj(LocalDateTime.now());
        ticketRepository.save(ticket);

        if ("CLOS".equalsIgnoreCase(statut)) {
            supprimerReclamationsDeZone(ticket.getZoneNom());
        }

        return ticket;
    }

    // ============================================================
    // ✅ RÉCUPÉRER TICKETS PAR RÉGION
    // ============================================================
    public List<Ticket> getTicketsParRegion(String region) {
        if (region == null) return ticketRepository.findAll();

        return ticketRepository.findAll().stream()
                .filter(t -> t.getRegion() != null &&
                        t.getRegion().equalsIgnoreCase(region))
                .filter(t -> {
                    String statut = t.getStatut().name();
                    if ("OUVERT".equals(statut)) return true;
                    if ("CLOS".equals(statut) || "ANNULE".equals(statut)) return true;
                    return true;
                })
                .collect(Collectors.toList());
    }

    // ============================================================
    // MÉTHODES PRIVÉES
    // ============================================================

    private String detecterZone(double lat, double lon, List<ZoneDTO> zones) {
        for (ZoneDTO zone : zones) {
            if (zone.getLatitudeCentre() == null || zone.getLongitudeCentre() == null) continue;

            double distance = calculerDistance(lat, lon,
                    zone.getLatitudeCentre(),
                    zone.getLongitudeCentre());

            System.out.println("📍 Distance vers " + zone.getNom()
                    + " : " + distance + " km | rayon: " + zone.getRayonCouverture());

            if (distance <= zone.getRayonCouverture()) {
                return zone.getNom();
            }
        }
        return null;
    }

    private double calculerDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String detecterType(int count) {
        if (count > 50) return "PANNE CRITIQUE";
        if (count > 30) return "PANNE MAJEURE";
        return "PANNE MINEURE";
    }

    private String detecterRegionDepuisNom(String nomZone) {
        if (nomZone == null || nomZone.isEmpty()) return "Inconnue";
        String zoneNormalized = Normalizer.normalize(nomZone.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        String premierMot = zoneNormalized.split(" ")[0];
        return premierMot.substring(0, 1).toUpperCase() + premierMot.substring(1);
    }

    private void supprimerReclamationsDeZone(String zoneNom) {
        try {
            List<ZoneDTO> zones = zoneClient.getZones();

            ZoneDTO zone = zones.stream()
                    .filter(z -> zoneNom.equalsIgnoreCase(z.getNom()))
                    .findFirst()
                    .orElse(null);

            if (zone == null) {
                System.out.println("⚠️ Zone introuvable pour suppression : " + zoneNom);
                return;
            }

            List<Reclamation> toutes = reclamationRepository.findAll();
            List<Reclamation> aSupprimer = toutes.stream()
                    .filter(rec -> rec.getLatitude() != null && rec.getLongitude() != null)
                    .filter(rec -> calculerDistance(
                            rec.getLatitude(), rec.getLongitude(),
                            zone.getLatitudeCentre(), zone.getLongitudeCentre()
                    ) <= zone.getRayonCouverture())
                    .collect(Collectors.toList());

            System.out.println("🗑️ Suppression de " + aSupprimer.size()
                    + " réclamations pour zone : " + zoneNom);
            reclamationRepository.deleteAll(aSupprimer);
            System.out.println("✅ Réclamations supprimées avec succès");

        } catch (Exception e) {
            System.out.println("⚠️ Erreur suppression réclamations : " + e.getMessage());
        }
    }
    public Ticket affecterTicket(Long ticketId, Long technicienId, String technicienNom) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket non trouvé"));

        if ("CLOS".equalsIgnoreCase(ticket.getStatut().name())
                || "ANNULE".equalsIgnoreCase(ticket.getStatut().name())) {
            throw new RuntimeException("Impossible d'affecter un ticket " + ticket.getStatut());
        }

        ticket.setAssignedTo(technicienId);
        ticket.setAssignedToName(technicienNom);
        ticket.setDateMaj(LocalDateTime.now());

        // Passer automatiquement en EN_COURS si OUVERT
        if ("OUVERT".equalsIgnoreCase(ticket.getStatut().name())) {
            ticket.setStatut(TicketStatut.EN_COURS);
            ticket.setDateDebutTraitement(LocalDateTime.now());
        }

        return ticketRepository.save(ticket);
    }
    // TicketService.java dans microservice-reclamation
    public void updatePriorite(Long ticketId, String priorite) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            ticket.setPriorite(priorite);
            ticketRepository.save(ticket);
        });
    }
    public void geocoderUneReclamation(Long reclamationId) throws InterruptedException {

        Reclamation rec = reclamationRepository.findById(reclamationId)
                .orElse(null);

        if (rec == null) {
            log.warn("⚠️  Réclamation introuvable pour géocodage — id={}", reclamationId);
            return;
        }

        // Déjà géocodée → rien à faire
        if (rec.getLatitude() != null && rec.getLongitude() != null) {
            log.info("⏭️  Réclamation {} déjà géocodée — skip", reclamationId);
            return;
        }

        if (rec.getAdresse() == null || rec.getAdresse().isBlank()) {
            log.warn("⚠️  Adresse vide pour réclamation id={}", reclamationId);
            return;
        }

        // Récupérer les pylônes
        List<Map<String, Object>> pylones;
        try {
            pylones = pyloneClient.getAllPylones();
        } catch (Exception e) {
            log.error("⚠️  Impossible de récupérer les pylônes : {}", e.getMessage());
            pylones = new ArrayList<>();
        }

        double[] coords = geocodingService.getCoordinates(rec.getAdresse());
        Thread.sleep(1100); // respecter la limite de l'API de géocodage

        if (coords == null) {
            log.warn("⚠️  Géocodage échoué pour : {}", rec.getAdresse());
            return;
        }

        rec.setLatitude(coords[0]);
        rec.setLongitude(coords[1]);

        // Trouver le pylône le plus proche
        Long   pyloneIdTrouve  = null;
        String pyloneNomTrouve = null;
        double distanceMin     = Double.MAX_VALUE;

        for (Map<String, Object> pylone : pylones) {
            Double pyloneLat = pylone.get("latitude")       != null ? ((Number) pylone.get("latitude")).doubleValue()       : null;
            Double pyloneLon = pylone.get("longitude")      != null ? ((Number) pylone.get("longitude")).doubleValue()      : null;
            Double rayon     = pylone.get("rayonCouverture") != null ? ((Number) pylone.get("rayonCouverture")).doubleValue() : null;

            if (pyloneLat == null || pyloneLon == null || rayon == null) continue;

            double distance = calculerDistance(coords[0], coords[1], pyloneLat, pyloneLon);

            if (distance <= rayon && distance < distanceMin) {
                distanceMin    = distance;
                pyloneIdTrouve = ((Number) pylone.get("id")).longValue();
                pyloneNomTrouve = (String) pylone.get("nom");
            }
        }

        // Vérifier si le pylône est bloqué par un chantier
        if (pyloneIdTrouve != null && chantierService.pyloneEstBloque(pyloneIdTrouve)) {
            Chantier chantierActif = chantierService.getChantierActifByPylone(pyloneIdTrouve);
            log.warn("⚠️  Réclamation {} → pylône {} BLOQUÉ par chantier '{}'",
                    rec.getId(), pyloneNomTrouve, chantierActif.getNom());
            reclamationRepository.save(rec); // sauvegarder les coordonnées GPS quand même
            return;
        }

        rec.setPyloneId(pyloneIdTrouve);
        rec.setPyloneNom(pyloneNomTrouve);
        reclamationRepository.save(rec);

        if (pyloneIdTrouve != null) {
            log.info("📡 Réclamation {} → pylône {}", rec.getId(), pyloneNomTrouve);
        } else {
            log.warn("⚠️  Aucun pylône trouvé pour : {}", rec.getAdresse());
        }
    }
    public Reclamation cloturerReclamation(String codeReclamation,
                                           String motif,
                                           Long   clotureParId,
                                           String clotureParNom) {

        Reclamation rec = reclamationRepository
                .findByCodeReclamation(codeReclamation)
                .orElseThrow(() -> new RuntimeException(
                        "Réclamation introuvable — code: " + codeReclamation));

        if (ReclamationStatut.CLOTUREE.equals(rec.getStatut())) {
            log.warn("⏭️  Réclamation {} déjà clôturée — skip", codeReclamation);
            return rec;
        }

        rec.setStatut(ReclamationStatut.CLOTUREE);
        rec.setDateCloture(LocalDateTime.now());
        rec.setMotifCloture(motif);

        Reclamation saved = reclamationRepository.save(rec);
        log.info("✅ Réclamation {} clôturée par {} (id={}) — dateCloture={}",
                codeReclamation, clotureParNom, clotureParId, saved.getDateCloture());

        // Réévaluer le ticket de la zone
        try {
            genererTicketsAutomatiquement();
        } catch (Exception e) {
            log.warn("⚠️  Réévaluation ticket après clôture échouée : {}", e.getMessage());
        }

        return saved;
    }
    /**
     * Supprime physiquement une réclamation de la table
     * lors de la réception d'un événement CLOTURE_RECLAMATION.
     */
    public void supprimerReclamationParCode(String codeReclamation) {

        Reclamation rec = reclamationRepository
                .findByCodeReclamation(codeReclamation)
                .orElse(null);

        if (rec == null) {
            log.warn("⚠️  Réclamation introuvable pour suppression — code: {}", codeReclamation);
            return;
        }

        reclamationRepository.delete(rec);
        log.info("✅ Réclamation {} supprimée de la table", codeReclamation);

        // Réévaluer les tickets après suppression
        try {
            genererTicketsAutomatiquement();
        } catch (Exception e) {
            log.warn("⚠️  Réévaluation ticket après suppression échouée : {}", e.getMessage());
        }
    }
}