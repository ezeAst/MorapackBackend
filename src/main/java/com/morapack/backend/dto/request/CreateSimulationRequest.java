package com.morapack.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateSimulationRequest {

    @NotBlank(message = "El tipo de simulación es requerido")
    @Pattern(regexp = "weekly|stress_test", message = "Tipo debe ser 'weekly' o 'stress_test'")
    private String type;

    @NotNull(message = "La fecha de inicio es requerida")
    private LocalDateTime startTime;

    // Parámetros opcionales de GRASP
    private Double alphaGrasp;
    private Integer tamanoRcl;
}