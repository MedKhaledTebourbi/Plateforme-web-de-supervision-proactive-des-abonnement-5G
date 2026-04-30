package com.example.micro_reclamation.Config;

import com.example.micro_reclamation.Entity.PyloneDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class Config {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    public PyloneDTO pyloneDTO() {
        // Create and return an instance of PyloneDTO
        return new PyloneDTO();
    }
}