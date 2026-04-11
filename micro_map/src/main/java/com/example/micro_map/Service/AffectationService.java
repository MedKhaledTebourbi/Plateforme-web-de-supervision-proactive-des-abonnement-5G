package com.example.micro_map.Service;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Repository.ClientRepository;
import com.example.micro_map.Repository.PyloneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AffectationService {

    private final ClientRepository clientRepository;
    private final PyloneRepository pyloneRepository;
    private final GeocodingService geocodingService;
    private final RestTemplate restTemplate;
    private final ChantierClient chantierClient;
    private boolean estPyloneBloque(Long pyloneId) {
        try {
            Boolean result = restTemplate.getForObject(
                    "http://localhost:8083/api/chantiers/check/" + pyloneId,
                    Boolean.class
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.err.println("⚠️ Erreur vérification chantier: " + e.getMessage());
            return false; // on laisse passer si erreur (optionnel)
        }
    }

    public void affecterClientsAutomatiquement() {

        List<Client> clients = clientRepository.findAll();
        List<Pylone> pylones = pyloneRepository.findAll();

        // ✅ Variables pour le suivi des affectations
        List<Client> clientsNonAffectes = new ArrayList<>();
        List<Client> clientsDejaAffectes = new ArrayList<>();
        List<Client> clientsNouvellementAffectes = new ArrayList<>();
        List<Client> clientsErreurGeocodage = new ArrayList<>();

        System.out.println("\n========================================");
        System.out.println("🚀 DÉBUT DE L'AFFECTATION AUTOMATIQUE");
        System.out.println("========================================");
        System.out.println("📊 Nombre total de clients: " + clients.size());
        System.out.println("📊 Nombre de pylônes disponibles: " + pylones.size());
        System.out.println("========================================\n");

        for (Client client : clients) {

            // ✅ MODIFICATION 1 : Vérifier si le client a déjà un pylône
            // ✅ Vérifier si le client a déjà un pylône
            if (client.getPylone() != null) {
                // ✅ NOUVEAU : Si le pylône assigné est bloqué, on le désaffecte et on continue
                if (estPyloneBloque(client.getPylone().getId())) {
                    System.out.println("⚠️ Client " + client.getId() + " - pylône "
                            + client.getPylone().getNom() + " BLOQUÉ → réaffectation nécessaire");
                    // Ne pas faire "continue" ici pour laisser la logique de réaffectation tourner
                } else {
                    System.out.println("ℹ️ Client " + client.getId() + " déjà affecté à "
                            + client.getPylone().getNom() + " - Ignoré");
                    clientsDejaAffectes.add(client);
                    continue;
                }
            }
            try {
                // Géocoder si coordonnées manquantes
                if (client.getLatitude() == null || client.getLongitude() == null) {
                    System.out.println("🔍 Geocoding: " + client.getAdresse());

                    Thread.sleep(1100); // AVANT l'appel Nominatim (rate limit)

                    try {
                        double[] coords = geocodingService.getCoordinatesFromAddress(client.getAdresse());

                        client.setLatitude(coords[0]);
                        client.setLongitude(coords[1]);

                        // ✅ Flush immédiat pour persister les coordonnées
                        client = clientRepository.saveAndFlush(client);

                        System.out.println("✅ Coords: " + coords[0] + ", " + coords[1]);
                    } catch (RuntimeException e) {
                        System.err.println("❌ Échec géocodage pour client " + client.getId() + ": " + e.getMessage());
                        clientsErreurGeocodage.add(client);
                        continue; // Passer au client suivant
                    }
                }

                // ✅ Vérifier que les coordonnées ne sont pas null après géocodage
                if (client.getLatitude() == null || client.getLongitude() == null) {
                    System.err.println("❌ Client " + client.getId() + " a des coordonnées null après géocodage");
                    clientsErreurGeocodage.add(client);
                    continue;
                }

                double latClient = client.getLatitude();
                double lonClient = client.getLongitude();

                // ✅ MODIFICATION 2 : Calculer la consommation UNE SEULE FOIS
                double consommation = client.getTypeAbonnement() != null
                        ? client.getTypeAbonnement() / 1000.0
                        : 0;

                System.out.println("📊 Client " + client.getId() + " - Abonnement: "
                        + client.getTypeAbonnement() + " Mbps → " + consommation + " Gbps");
                System.out.println("📍 Position: lat=" + latClient + ", lon=" + lonClient);

                Pylone meilleurPylone = null;
                double distanceMin = Double.MAX_VALUE;
                List<Pylone> pylonesAPorte = new ArrayList<>();

                // ✅ Choisir le pylône le PLUS PROCHE
                for (Pylone pylone : pylones) {
                    if (estPyloneBloque(pylone.getId())) {
                        System.out.println("   ⚠️ Pylône " + pylone.getNom() + " est BLOQUÉ par un chantier - ignoré");
                        continue; // Sauter ce pylône
                    }

                    if (pylone.getChargeActuelle() == null) {
                        pylone.setChargeActuelle(0.0);
                    }

                    double distance = calculDistance(
                            latClient, lonClient,
                            pylone.getLatitude(), pylone.getLongitude()
                    );

                    if (distance <= pylone.getRayonCouverture()) {
                        pylonesAPorte.add(pylone);
                        // ✅ MODIFICATION 3 : Utiliser la même consommation partout
                        if (pylone.getChargeActuelle() + consommation <= pylone.getCapaciteMax()) {
                            if (distance < distanceMin) {
                                distanceMin = distance;
                                meilleurPylone = pylone;
                            }
                        }
                    }
                }

                // Afficher les pylônes à portée trouvés
                if (!pylonesAPorte.isEmpty()) {
                    System.out.println("   📡 Pylônes à portée: " + pylonesAPorte.size());
                    for (Pylone p : pylonesAPorte) {
                        double dist = calculDistance(latClient, lonClient, p.getLatitude(), p.getLongitude());
                        System.out.println("      - " + p.getNom() + " (distance: " + String.format("%.2f", dist) + "m, charge: " + p.getChargeActuelle() + "/" + p.getCapaciteMax() + " Gbps)");
                    }
                } else {
                    System.out.println("   ❌ Aucun pylône à portée");
                }

                if (meilleurPylone != null) {
                    // ✅ MODIFICATION 4 : Utiliser la MÊME consommation pour la mise à jour
                    client.setPylone(meilleurPylone);
                    meilleurPylone.setChargeActuelle(meilleurPylone.getChargeActuelle() + consommation);

                    pyloneRepository.save(meilleurPylone);
                    clientRepository.save(client);

                    System.out.println("✅ Client " + client.getId() + " → " + meilleurPylone.getNom() +
                            " (distance: " + String.format("%.2f", distanceMin) + "m)");
                    clientsNouvellementAffectes.add(client);
                } else {
                    System.out.println("❌ Aucun pylône disponible pour client " + client.getId());
                    clientsNonAffectes.add(client);
                }

            } catch (Exception e) {
                System.err.println("❌ Erreur client " + client.getId() + ": " + e.getMessage());
                e.printStackTrace();
                clientsNonAffectes.add(client);
            }
        }

        // ✅ AFFICHAGE FINAL DES RÉSULTATS (avec vérification des nulls)
        afficherResume(clientsDejaAffectes, clientsNouvellementAffectes, clientsNonAffectes, clientsErreurGeocodage, pylones);
    }

    // ✅ Méthode pour afficher le résumé des affectations (CORRIGÉE)
    private void afficherResume(List<Client> dejaAffectes, List<Client> nouvellementAffectes,
                                List<Client> nonAffectes, List<Client> erreurGeocodage, List<Pylone> pylones) {

        System.out.println("\n\n========================================");
        System.out.println("📊 RÉSUMÉ DE L'AFFECTATION");
        System.out.println("========================================");
        System.out.println("✅ Clients déjà affectés: " + dejaAffectes.size());
        System.out.println("✅ Clients nouvellement affectés: " + nouvellementAffectes.size());
        System.out.println("❌ Clients non affectés (hors portée): " + nonAffectes.size());
        System.out.println("⚠️ Clients en erreur de géocodage: " + erreurGeocodage.size());
        System.out.println("========================================\n");

        // Afficher les clients en erreur de géocodage
        if (!erreurGeocodage.isEmpty()) {
            System.out.println("⚠️ LISTE DES CLIENTS EN ERREUR DE GÉOCODAGE:");
            System.out.println("--------------------------------------------------");
            for (Client client : erreurGeocodage) {
                System.out.println("ID: " + client.getId());
                System.out.println("   Adresse: " + client.getAdresse());
                System.out.println("   Abonnement: " + client.getTypeAbonnement() + " Mbps");
                System.out.println("   ❌ Adresse non trouvée par Nominatim");
                System.out.println("--------------------------------------------------");
            }
        }

        // Afficher les clients non affectés (hors portée) avec leurs coordonnées
        if (!nonAffectes.isEmpty()) {
            System.out.println("\n🔍 LISTE DES CLIENTS NON AFFECTÉS (HORS PORTÉE):");
            System.out.println("--------------------------------------------------");
            for (Client client : nonAffectes) {
                // ✅ Vérifier que les coordonnées ne sont pas null
                Double lat = client.getLatitude();
                Double lon = client.getLongitude();

                System.out.println("ID: " + client.getId());
                System.out.println("   Adresse: " + client.getAdresse());

                if (lat != null && lon != null) {
                    System.out.println("   Position: lat=" + lat + ", lon=" + lon);
                    System.out.println("   Abonnement: " + client.getTypeAbonnement() + " Mbps");

                    // Trouver le pylône le plus proche pour information
                    Pylone pylonePlusProche = null;
                    double distanceMin = Double.MAX_VALUE;
                    for (Pylone pylone : pylones) {
                        double distance = calculDistance(lat, lon,
                                pylone.getLatitude(), pylone.getLongitude());
                        if (distance < distanceMin) {
                            distanceMin = distance;
                            pylonePlusProche = pylone;
                        }
                    }
                    if (pylonePlusProche != null) {
                        System.out.println("   📡 Pylône le plus proche: " + pylonePlusProche.getNom() +
                                " (distance: " + String.format("%.2f", distanceMin) + "m, rayon: " +
                                pylonePlusProche.getRayonCouverture() + "m)");
                        if (distanceMin <= pylonePlusProche.getRayonCouverture()) {
                            System.out.println("      ⚠️ À portée mais capacité insuffisante!");
                        } else {
                            System.out.println("      ❌ Hors de portée (manque " +
                                    String.format("%.2f", distanceMin - pylonePlusProche.getRayonCouverture()) + "m)");
                        }
                    }
                } else {
                    System.out.println("   ❌ Coordonnées non disponibles (géocodage échoué)");
                }
                System.out.println("--------------------------------------------------");
            }
        }

        // Afficher l'état des pylônes après affectation
        System.out.println("\n📡 ÉTAT DES PYLÔNES APRÈS AFFECTATION:");
        System.out.println("--------------------------------------------------");
        for (Pylone pylone : pylones) {
            int nbClients = 0;
            for (Client client : nouvellementAffectes) {
                if (client.getPylone() != null && client.getPylone().getId().equals(pylone.getId())) {
                    nbClients++;
                }
            }
            for (Client client : dejaAffectes) {
                if (client.getPylone() != null && client.getPylone().getId().equals(pylone.getId())) {
                    nbClients++;
                }
            }
            System.out.println("Pylône " + pylone.getNom() +
                    " - Charge: " + (pylone.getChargeActuelle() != null ? pylone.getChargeActuelle() : 0) +
                    " / " + pylone.getCapaciteMax() + " Gbps" +
                    " - Clients affectés: " + nbClients);
        }
        System.out.println("========================================\n");
        System.out.println("\n🔒 PYLÔNES BLOQUÉS PAR DES CHANTIERS:");
        System.out.println("--------------------------------------------------");
        for (Pylone pylone : pylones) {
            if (estPyloneBloque(pylone.getId())) {
                System.out.println("   ⚠️ " + pylone.getNom() + " (ID: " + pylone.getId() + ") - BLOQUÉ");
            }
        }
        System.out.println("========================================\n");
    }


    // 📏 Calcul distance
    public double calculDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6_371_000; // Rayon de la Terre en mètres

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        return R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}