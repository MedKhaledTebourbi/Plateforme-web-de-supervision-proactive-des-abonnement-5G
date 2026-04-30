package com.example.micro_map.Service;

import com.example.micro_map.Entity.Client;
import com.example.micro_map.Entity.ContratEvent;
import com.example.micro_map.Entity.ContratTraite;
import com.example.micro_map.Repository.ClientRepository;
import com.example.micro_map.Repository.ContratTraiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContratConsumer {

    private final AffectationService      affectationService;
    private final ClientRepository        clientRepository;
    private final ContratTraiteRepository contratTraiteRepository;

    @KafkaListener(
            topics           = "contrats-topic",
            groupId          = "affectation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void traiterEvenement(
            @Payload ContratEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        log.info("📥 [partition={} | offset={}] Événement reçu : {} | ContratID={}",
                partition, offset, event.getTypeEvenement(), event.getContratId());

        // ── Filtre type d'événement ───────────────────────────
        if (event.getTypeEvenement() == null) {
            log.warn("⚠️  typeEvenement null — message ignoré");
            return;
        }

        // ── Idempotence : cet événement déjà traité pour ce contrat ? ──
        if (contratTraiteRepository.existsByContratIdAndTypeEvenement(
                event.getContratId(), event.getTypeEvenement())) {
            log.warn("⚠️  ContratID={} | {} déjà traité → ignoré",
                    event.getContratId(), event.getTypeEvenement());
            return;
        }

        switch (event.getTypeEvenement()) {
            case "NOUVEAU_CLIENT" -> traiterNouveauClient(event);
            case "ANNULATION"     -> traiterAnnulation(event);
            default -> log.warn("❌ Type événement inconnu : {}", event.getTypeEvenement());
        }

        // ── Marquer traité ────────────────────────────────────
        contratTraiteRepository.save(ContratTraite.builder()
                .contratId(event.getContratId())
                .typeEvenement(event.getTypeEvenement())
                .traiteLe(LocalDateTime.now())
                .build());

        log.info("✅ ContratID={} | {} marqué traité",
                event.getContratId(), event.getTypeEvenement());
    }
    // ─────────────────────────────────────────────────────────
    // NOUVEAU CLIENT
    // ─────────────────────────────────────────────────────────
    private void traiterNouveauClient(ContratEvent event) {

        // Idempotence niveau client : déjà créé pour ce contrat ?
        if (clientRepository.existsByContratId(event.getContratId())) {
            log.warn("⚠️  Client déjà créé pour ContratID={} → skip",
                    event.getContratId());
            return;
        }

        // 1. Créer le client lié au contrat
        Client client = Client.builder()
                .contratId(event.getContratId())   // lien contrat ↔ client
                .adresse(event.getAdresse())
                .typeAbonnement(event.getTypeAbonnement())
                .build();

        Client saved = clientRepository.save(client);
        log.info("✅ Nouveau client créé — id={} | contratId={} | adresse={}",
                saved.getId(), saved.getContratId(), saved.getAdresse());

        // 2. Affecter UNIQUEMENT ce nouveau client (pas tous les clients)
        //    Exécuté en asynchrone pour libérer le thread Kafka
        affecterAsync(saved.getId());
    }

    // ─────────────────────────────────────────────────────────
    // ANNULATION
    // ─────────────────────────────────────────────────────────
    private void traiterAnnulation(ContratEvent event) {

        // Retrouver le client via contratId (et non clientId)
        clientRepository.findByContratId(event.getContratId())
                .ifPresentOrElse(
                        client -> {
                            // Libérer la charge du pylône associé
                            if (client.getPylone() != null) {
                                double conso = client.getTypeAbonnement() != null
                                        ? client.getTypeAbonnement() / 1000.0 : 0.0;
                                double nouvelleCharge = Math.max(
                                        0.0,
                                        client.getPylone().getChargeActuelle() - conso
                                );
                                client.getPylone().setChargeActuelle(nouvelleCharge);
                                log.info("📡 Charge libérée — pylône={} | charge={}→{} Gbps",
                                        client.getPylone().getNom(),
                                        client.getPylone().getChargeActuelle() + conso,
                                        nouvelleCharge);
                            }
                            clientRepository.delete(client);
                            log.info("🗑️  Client supprimé — id={} | contratId={}",
                                    client.getId(), event.getContratId());
                        },
                        () -> log.warn("⚠️  Aucun client trouvé pour ContratID={}",
                                event.getContratId())
                );
    }

    // ─────────────────────────────────────────────────────────
    // PIPELINE ASYNCHRONE — n'affecte que le client passé en id
    // ─────────────────────────────────────────────────────────
    @Async("affectationExecutor")
    public void affecterAsync(Long clientId) {
        log.info("🔄 [async] Démarrage affectation — clientId={}", clientId);
        try {
            affectationService.affecterUnClient(clientId);
            log.info("✅ [async] Affectation terminée — clientId={}", clientId);
        } catch (Exception e) {
            log.error("⚠️  [async] Erreur affectation — clientId={} : {}",
                    clientId, e.getMessage());
        }
    }
}