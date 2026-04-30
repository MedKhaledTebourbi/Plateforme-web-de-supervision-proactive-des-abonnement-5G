package com.example.micro_map.Service;

import com.example.micro_map.Entity.ZoneReseau;
import com.example.micro_map.Repository.PyloneRepository;
import com.example.micro_map.Repository.ZoneReseauRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneReseauService {

    private final ZoneReseauRepository repository;
    private final PyloneRepository pyloneRepository;

    public List<ZoneReseau> getAllZones() {
        List<ZoneReseau> zones = repository.findAll();
        // ✅ syncChargeActuelle = calcul en mémoire uniquement, pas de save
        zones.forEach(this::syncChargeActuelle);
        return zones;
    }

    public Optional<ZoneReseau> getById(Long id) {
        return repository.findById(id).map(this::syncChargeActuelle);
    }

    public ZoneReseau create(ZoneReseau zone) {
        return repository.save(zone);
    }

    public Optional<ZoneReseau> update(Long id, ZoneReseau updated) {
        return repository.findById(id).map(existing -> {
            existing.setNom(updated.getNom());
            existing.setDescription(updated.getDescription());
            existing.setBandePassanteMax(updated.getBandePassanteMax());
            existing.setLatitudeCentre(updated.getLatitudeCentre());
            existing.setLongitudeCentre(updated.getLongitudeCentre());
            existing.setRayonCouverture(updated.getRayonCouverture());
            syncChargeActuelle(existing);
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * ✅ Calcul pur en mémoire — ne persiste JAMAIS en base.
     * La persistance est la responsabilité du scheduler uniquement.
     */
    public ZoneReseau syncChargeActuelle(ZoneReseau zone) {
        double total = zone.getPylones() == null ? 0.0 :
                zone.getPylones().stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getEstBloque()))
                        .mapToDouble(p -> p.getChargeActuelle() != null ? p.getChargeActuelle() : 0.0)
                        .sum();
        zone.setChargeActuelle(total);
        log.debug("[Zone {}] chargeActuelle = {} Mbps", zone.getNom(), total);
        return zone;
    }

    /**
     * Recalcule toutes les zones en mémoire sans persister.
     * Appelé uniquement si besoin de lecture fraîche hors scheduler.
     */
    @Transactional
    public void syncAllZones() {
        List<ZoneReseau> zones = repository.findAll();
        zones.forEach(this::syncChargeActuelle);
        // ✅ Pas de saveAll ici — le scheduler est le seul à persister
        log.info("[Sync] {} zones recalculées (lecture seule)", zones.size());
    }
}