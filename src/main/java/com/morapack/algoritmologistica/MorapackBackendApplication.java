package com.morapack.algoritmologistica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.morapack.algoritmologistica",
        "com.morapack.backend"
})
@EnableJpaRepositories(basePackages = "com.morapack.backend.repository")
@EntityScan(basePackages = "com.morapack.backend.entity")
public class MorapackBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MorapackBackendApplication.class, args);
        System.out.println("\n=== 🚀 APLICACIÓN INICIADA ===");
        System.out.println("=== 📍 API: http://localhost:8080/api/planificacion ===");
        System.out.println("=== 🏢 Aeropuertos: http://localhost:8080/api/aeropuertos ===\n");
    }
}