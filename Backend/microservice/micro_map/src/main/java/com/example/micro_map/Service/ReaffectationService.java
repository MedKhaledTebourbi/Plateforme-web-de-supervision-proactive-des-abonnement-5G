package com.example.micro_map.Service;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.Pylone;
import com.example.micro_map.Repository.ClientRepository;
import com.example.micro_map.Repository.PyloneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReaffectationService {

    private final ClientRepository clientRepository;
    private final PyloneRepository pyloneRepository;
    private final AffectationService affectationService;

    @Transactional
    public void reaffecterClientsEnAttente(Long pyloneIdDebloque) {

        log.info("🔓 Pylône {} débloqué → réaffectation automatique", pyloneIdDebloque);

        // 1. Récupérer le pylône débloqué
        Pylone pylone = pyloneRepository.findById(pyloneIdDebloque).orElse(null);
        if (pylone == null) {
            log.error("❌ Pylône {} introuvable", pyloneIdDebloque);
            return;
        }

        if (pylone.getChargeActuelle() == null) pylone.setChargeActuelle(0.0);

        // 2. Récupérer TOUS les clients sans pylône (= en attente)
        List<Client> clientsSansPylone = clientRepository.findByPyloneIsNull();
        log.info("📋 {} client(s) sans pylône trouvés", clientsSansPylone.size());

        if (clientsSansPylone.isEmpty()) return;

        // 3. Pour chaque client sans pylône → vérifier s'il peut être affecté à ce pylône
        for (Client client : clientsSansPylone) {

            if (client.getLatitude() == null || client.getLongitude() == null) {
                log.warn("⚠️ Client {} sans coordonnées, ignoré", client.getId());
                continue;
            }

            double consommation = client.getTypeAbonnement() != null
                    ? client.getTypeAbonnement() / 1000.0 : 0;

            double distance = affectationService.calculDistance(
                    client.getLatitude(), client.getLongitude(),
                    pylone.getLatitude(), pylone.getLongitude()
            );

            boolean dansRayon = distance <= pylone.getRayonCouverture();
            boolean capaciteOk = pylone.getChargeActuelle() + consommation <= pylone.getCapaciteMax();

            if (dansRayon && capaciteOk) {
                client.setPylone(pylone);
                pylone.setChargeActuelle(pylone.getChargeActuelle() + consommation);

                clientRepository.save(client);
                pyloneRepository.save(pylone);

                log.info("✅ Client {} réaffecté → pylône {} (distance: {}m)",
                        client.getId(), pylone.getNom(),
                        String.format("%.2f", distance));
            }
        }

        log.info("✅ Réaffectation terminée pour pylône {}", pyloneIdDebloque);
    }
}