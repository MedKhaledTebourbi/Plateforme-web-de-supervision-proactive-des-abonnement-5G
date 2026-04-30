package com.example.micro_map.Config;




import com.example.micro_map.Entity.ContratEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // ── Lire depuis application.properties ───────────────────
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Producer ──────────────────────────────────────────────
    @Bean
    public ProducerFactory<String, ContratEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS,         false);

        // ── Fiabilité ─────────────────────────────────────────
        props.put(ProducerConfig.ACKS_CONFIG,            "all");
        props.put(ProducerConfig.RETRIES_CONFIG,         3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ContratEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Producer générique pour la DLQ ───────────────────────
    @Bean
    public ProducerFactory<String, Object> producerFactoryDLQ() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS,         false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplateDLQ() {
        return new KafkaTemplate<>(producerFactoryDLQ());
    }

    // ── Consumer ──────────────────────────────────────────────
    @Bean
    public ConsumerFactory<String, ContratEvent> consumerFactory() {
        JsonDeserializer<ContratEvent> deserializer =
                new JsonDeserializer<>(ContratEvent.class, false);
        deserializer.addTrustedPackages("*");

        ErrorHandlingDeserializer<ContratEvent> errorHandling =
                new ErrorHandlingDeserializer<>(deserializer);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,       bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                "affectation-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,       "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,  StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,ErrorHandlingDeserializer.class);

        // ── Optimisation ──────────────────────────────────────
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,        10);

        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), errorHandling);
    }

    // ── Listener Factory avec DLQ ─────────────────────────────
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ContratEvent>
    kafkaListenerContainerFactory() {

        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, ContratEvent>();
        factory.setConsumerFactory(consumerFactory());

        // Retry 3× avec 1s entre chaque → puis DLT
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(
                        new DeadLetterPublishingRecoverer(kafkaTemplateDLQ()),
                        new FixedBackOff(1000L, 3)
                )
        );
        return factory;
    }
}