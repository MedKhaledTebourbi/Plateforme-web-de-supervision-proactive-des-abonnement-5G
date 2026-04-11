package com.example.micro_reclamation.Service;

import com.example.micro_reclamation.Entity.*;
import com.example.micro_reclamation.Repository.TicketRepository;
import com.example.micro_reclamation.Repository.reclamationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class reclamationService {

    private final reclamationRepository reclamationRepository;
    private final GeocodingService geocodingService;
    private final TicketRepository ticketRepository;
    private final ZoneClient zoneClient;
    private final UserClient userClient;
    private final ChantierService chantierService;  // ✅ correctement injecté
    private final PyloneClient pyloneClient;         // ✅ ajouté

    private static final int SEUIL_RECLAMATION = 3;

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

            // ✅ Ignorer les réclamations dont le pylône est bloqué par un chantier
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
            int count = entry.getValue().size();

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

                String region = detecterRegionDepuisNom(zoneNom);
                String typePanne = detecterType(count);

                Long technicienId = null;
                String technicienNom = "SYSTEM";
                try {
                    List<Map<String, Object>> techniciens = userClient.getTechniciensByRegion(region);
                    if (techniciens != null && !techniciens.isEmpty()) {
                        technicienId = ((Number) techniciens.get(0).get("id")).longValue();
                        technicienNom = (String) techniciens.get(0).get("username");
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ UserClient : " + e.getMessage());
                }

                Ticket ticket = Ticket.builder()
                        .zoneId(entry.getValue().get(0).getId())
                        .zoneNom(zoneNom)
                        .region(region)
                        .typePanne(typePanne)
                        .nombreReclamations(count)
                        .statut(TicketStatut.valueOf("OUVERT"))
                        .dateCreation(LocalDateTime.now())
                        .createdBy(technicienId)
                        .createdByName(technicienNom)
                        .build();

                ticketRepository.save(ticket);
                System.out.println("✅ Ticket créé → zone: " + zoneNom
                        + " | région: " + region
                        + " | technicien: " + technicienNom);
            }
        }
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
}