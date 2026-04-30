package com.example.micro_map.Service;

import com.example.micro_map.Entity.ContratEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContratProducer {

    private static final String TOPIC = "contrats-topic";
    private final KafkaTemplate<String, ContratEvent> kafkaTemplate;

    public void envoyerEvenement(ContratEvent event) {
        // Clé = contratId → garantit même partition + idempotence
        kafkaTemplate.send(TOPIC, event.getContratId().toString(), event);
        log.info("📤 Événement envoyé : {} | ContratID={}",
                event.getTypeEvenement(), event.getContratId());
    }
}