package com.fintrack.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FintrackApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FintrackApiApplication.class, args);
    }
}
