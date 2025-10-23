package com.morapack.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ControlSimulationRequest {

    @NotBlank(message = "La acción es requerida")
    @Pattern(regexp = "pause|resume|stop", message = "Acción debe ser 'pause', 'resume' o 'stop'")
    private String action;
}