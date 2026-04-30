package com.example.micro_reclamation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MicroReclamationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroReclamationApplication.class, args);
    }

}
