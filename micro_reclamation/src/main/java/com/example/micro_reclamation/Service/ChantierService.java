package com.example.micro_reclamation.Service;

import com.example.micro_reclamation.Entity.Chantier;
import com.example.micro_reclamation.Repository.ChantierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChantierService {

    private final ChantierRepository chantierRepository;
    private final PyloneClient pyloneClient;

    // ✅ Créer un chantier manuellement
    @Transactional
    public Chantier creerChantier(Chantier chantier) {
        log.info("Création d'un nouveau chantier pour le pylône: {}", chantier.getPyloneId());

        // Validation des champs obligatoires
        if (chantier.getPyloneId() == null) {
            throw new RuntimeException("L'ID du pylône est obligatoire");
        }

        // Vérifier si le pylône a déjà un chantier validé
        if (chantierRepository.existsByPyloneIdAndStatut(chantier.getPyloneId(), "VALIDE")) {
            throw new RuntimeException(
                    "Ce pylône a déjà un chantier validé en cours. " +
                            "Terminez ou annulez le chantier existant avant d'en créer un nouveau."
            );
        }

        // Vérifier si le pylône a un chantier en cours (non terminé)
        List<Chantier> chantiersExistants = chantierRepository.findByPyloneId(chantier.getPyloneId());
        boolean hasActiveChantier = chantiersExistants.stream()
                .anyMatch(c -> List.of("PLANIFIE", "EN_COURS", "VALIDE").contains(c.getStatut()));

        if (hasActiveChantier) {
            throw new RuntimeException(
                    "Un chantier est déjà actif sur ce pylône (statut: PLANIFIE, EN_COURS ou VALIDE)"
            );
        }

        // ✅ Récupérer infos pylône depuis micro_map
        Map<String, Object> pylone = pyloneClient.getPylone(chantier.getPyloneId());
        if (pylone != null) {
            chantier.setPyloneNom((String) pylone.get("nom"));

            // Gestion des coordonnées
            if (pylone.get("latitude") != null) {
                chantier.setLatitude(((Number) pylone.get("latitude")).doubleValue());
            }
            if (pylone.get("longitude") != null) {
                chantier.setLongitude(((Number) pylone.get("longitude")).doubleValue());
            }

            // Extraire la région
            String zoneNom = (String) pylone.get("zoneNom");
            if (zoneNom != null) {
                chantier.setRegion(extraireRegion(zoneNom));
            }
        } else {
            log.warn("Impossible de récupérer les infos du pylône {} depuis micro_map", chantier.getPyloneId());
        }

        // Définir les valeurs par défaut
        if (chantier.getStatut() == null) {
            chantier.setStatut("PLANIFIE");
        }

        if (chantier.getDateDebut() == null) {
            chantier.setDateDebut(LocalDateTime.now());
        }

        Chantier savedChantier = chantierRepository.save(chantier);
        log.info("Chantier créé avec succès: ID={}, Nom={}", savedChantier.getId(), savedChantier.getNom());

        return savedChantier;
    }

    // ✅ Valider un chantier → bloque le pylône
    @Transactional
    public Chantier validerChantier(Long chantierId) {
        log.info("Validation du chantier: {}", chantierId);

        Chantier chantier = chantierRepository.findById(chantierId)
                .orElseThrow(() -> new RuntimeException("Chantier non trouvé avec l'ID: " + chantierId));

        if ("VALIDE".equals(chantier.getStatut())) {
            throw new RuntimeException("Ce chantier est déjà validé");
        }

        if ("TERMINE".equals(chantier.getStatut()) || "ANNULE".equals(chantier.getStatut())) {
            throw new RuntimeException("Impossible de valider un chantier " + chantier.getStatut().toLowerCase());
        }

        try {
            // ✅ Notifier micro_map de bloquer le pylône
            pyloneClient.bloquerPylone(chantier.getPyloneId(), true);

            chantier.setStatut("VALIDE");
            chantier.setDateValidation(LocalDateTime.now());
            chantier.setPyloneBloque(true);

            Chantier updatedChantier = chantierRepository.save(chantier);
            log.info("✅ Chantier validé — pylône {} bloqué", chantier.getPyloneId());

            return updatedChantier;
        } catch (Exception e) {
            log.error("Erreur lors de la validation du chantier: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la validation: " + e.getMessage());
        }
    }

    // ✅ Terminer un chantier → débloque le pylône
    @Transactional
    public Chantier terminerChantier(Long chantierId) {
        log.info("Terminaison du chantier: {}", chantierId);

        Chantier chantier = chantierRepository.findById(chantierId)
                .orElseThrow(() -> new RuntimeException("Chantier non trouvé avec l'ID: " + chantierId));

        if ("TERMINE".equals(chantier.getStatut())) {
            throw new RuntimeException("Ce chantier est déjà terminé");
        }

        try {
            // ✅ Notifier micro_map de débloquer le pylône
            pyloneClient.bloquerPylone(chantier.getPyloneId(), false);

            chantier.setStatut("TERMINE");
            chantier.setDateFin(LocalDateTime.now());
            chantier.setPyloneBloque(false);

            Chantier updatedChantier = chantierRepository.save(chantier);
            log.info("✅ Chantier terminé — pylône {} débloqué", chantier.getPyloneId());

            return updatedChantier;
        } catch (Exception e) {
            log.error("Erreur lors de la terminaison du chantier: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la terminaison: " + e.getMessage());
        }
    }

    // ✅ Annuler un chantier
    @Transactional
    public Chantier annulerChantier(Long chantierId) {
        log.info("Annulation du chantier: {}", chantierId);

        Chantier chantier = chantierRepository.findById(chantierId)
                .orElseThrow(() -> new RuntimeException("Chantier non trouvé avec l'ID: " + chantierId));

        if ("ANNULE".equals(chantier.getStatut())) {
            throw new RuntimeException("Ce chantier est déjà annulé");
        }

        // Si le chantier était validé, il faut débloquer le pylône
        if ("VALIDE".equals(chantier.getStatut())) {
            try {
                pyloneClient.bloquerPylone(chantier.getPyloneId(), false);
            } catch (Exception e) {
                log.error("Erreur lors du déblocage du pylône: {}", e.getMessage());
                // On continue même si le déblocage échoue
            }
        }

        chantier.setStatut("ANNULE");
        chantier.setPyloneBloque(false);
        chantier.setDateFin(LocalDateTime.now()); // Optionnel

        Chantier updatedChantier = chantierRepository.save(chantier);
        log.info("Chantier annulé: {}", chantierId);

        return updatedChantier;
    }

    // ✅ Vérifier si pylône bloqué (appelé avant ajout client/réclamation)
    public boolean pyloneEstBloque(Long pyloneId) {
        boolean isBlocked = chantierRepository.existsByPyloneIdAndStatut(pyloneId, "VALIDE");
        log.debug("Vérification blocage pylône {}: {}", pyloneId, isBlocked);
        return isBlocked;
    }

    // ✅ Récupérer le chantier actif d'un pylône
    public Chantier getChantierActifByPylone(Long pyloneId) {
        List<Chantier> chantiers = chantierRepository.findByPyloneId(pyloneId);
        return chantiers.stream()
                .filter(c -> "VALIDE".equals(c.getStatut()))
                .findFirst()
                .orElse(null);
    }

    public List<Chantier> getAllChantiers() {
        return chantierRepository.findAll();
    }

    public List<Chantier> getChantiersByPylone(Long pyloneId) {
        return chantierRepository.findByPyloneId(pyloneId);
    }

    public List<Chantier> getChantiersByRegion(String region) {
        return chantierRepository.findByRegion(region);
    }

    public List<Chantier> getChantiersByTechnicien(Long technicienId) {
        return chantierRepository.findByTechnicienId(technicienId);
    }

    public List<Chantier> getChantiersByStatut(String statut) {
        return chantierRepository.findByStatut(statut);
    }

    private String extraireRegion(String zoneNom) {
        if (zoneNom == null) return "Inconnue";

        Map<String, String> regionMap = new HashMap<>();
        regionMap.put("Tunis", "Tunis");
        regionMap.put("Ariana", "Ariana");
        regionMap.put("Ben Arous", "Ben Arous");
        regionMap.put("Sfax", "Sfax");
        regionMap.put("Sousse", "Sousse");
        regionMap.put("Bizerte", "Bizerte");
        regionMap.put("Nabeul", "Nabeul");
        regionMap.put("Manouba", "Manouba");
        regionMap.put("Kairouan", "Kairouan");
        regionMap.put("Gabès", "Gabès");
        regionMap.put("Gafsa", "Gafsa");

        return regionMap.entrySet().stream()
                .filter(e -> zoneNom.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(zoneNom.split(" ")[0]);
    }
}