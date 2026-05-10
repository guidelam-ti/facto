package com.guidelam.facto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FactoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoApplication.class, args);
    }
}
