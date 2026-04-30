package com.example.micro_reclamation.Service;

import com.example.micro_reclamation.Entity.Reclamation;
import com.example.micro_reclamation.Entity.ReclamationEvent;
import com.example.micro_reclamation.Repository.reclamationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReclamationConsumer {

    private final reclamationRepository reclamationRepository;
    private final reclamationService    reclamationService;

    @KafkaListener(
            topics           = "reclamation-topic",
            groupId          = "reclamation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload ReclamationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        String type = event.getEventType();

        if ("RECLAMATION".equalsIgnoreCase(type)) {
            handleCreation(event, partition, offset);

        } else if ("CLOTURE_RECLAMATION".equalsIgnoreCase(type)) {
            handleCloture(event, partition, offset);

        } else {
            log.warn("⏭️  Message ignoré — eventType inconnu : {} [partition={}, offset={}]",
                    type, partition, offset);
        }
    }

    // ── Création ──────────────────────────────────────────────
    private void handleCreation(ReclamationEvent event, int partition, long offset) {
        log.info("📥 [partition={} | offset={}] Création réclamation — adresse: {}",
                partition, offset, event.getAdresse());

        Reclamation reclamation = Reclamation.builder()
                .codeReclamation(event.getCodeReclamation())
                .adresse(event.getAdresse())
                .typeReclamation(event.getTypeReclamation())
                .dateReclamation(event.getDateReclamation())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .pyloneId(event.getPyloneId())
                .pyloneNom(event.getPyloneNom())
                .build();
        // codeReclamation et dateCreation générés par @PrePersist

        Reclamation saved = reclamationRepository.save(reclamation);
        log.info("✅ Réclamation persistée — id={} | code={} | dateCreation={}",
                saved.getId(), saved.getCodeReclamation(), saved.getDateCreation());

        lancerPipelineAsync(saved.getId());
    }

    // ── Clôture ───────────────────────────────────────────────
    // ── Clôture → suppression physique ───────────────────────────
    private void handleCloture(ReclamationEvent event, int partition, long offset) {
        log.info("🗑️ [partition={} | offset={}] Suppression réclamation — code: {}",
                partition, offset, event.getCodeReclamation());

        reclamationService.supprimerReclamationParCode(
                event.getCodeReclamation()
        );
    }

    // ── Pipeline asynchrone ───────────────────────────────────
    @Async("kafkaPipelineExecutor")
    public void lancerPipelineAsync(Long reclamationId) {
        log.info("🔄 [async] Démarrage pipeline — reclamationId={}", reclamationId);
        try {
            log.info("🗺️  [async] Géocodage en cours...");
            reclamationService.geocoderUneReclamation(reclamationId);

            log.info("🎫 [async] Génération de tickets...");
            reclamationService.genererTicketsAutomatiquement();

            log.info("✅ [async] Pipeline terminé — reclamationId={}", reclamationId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("⚠️  [async] Pipeline interrompu — reclamationId={} : {}",
                    reclamationId, e.getMessage());
        } catch (Exception e) {
            log.error("⚠️  [async] Erreur pipeline — reclamationId={} : {}",
                    reclamationId, e.getMessage());
        }
    }
}