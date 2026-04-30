package com.example.micro_reclamation.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool de threads dédié au pipeline Kafka.
     * Séparé du pool de threads Kafka pour ne jamais bloquer
     * la consommation des messages en attente.
     */
    @Bean(name = "kafkaPipelineExecutor")
    public Executor kafkaPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);        // 1 thread par partition
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);      // file d'attente si tous les threads sont occupés
        executor.setThreadNamePrefix("kafka-pipeline-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}