package com.example.micro_map.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic contratsTopic() {
        return TopicBuilder.name("contrats-topic")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic contratsDLT() {
        return TopicBuilder.name("contrats-topic.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }
}