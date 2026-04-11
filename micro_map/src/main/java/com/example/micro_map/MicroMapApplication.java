package com.example.micro_map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MicroMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroMapApplication.class, args);
    }

}
