package com.morapack.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimulationEvent {

    private String message;
    private String type;
    private long simulatedSeconds;
    private LocalDateTime timestamp;

    public SimulationEvent(String message, String type, long simulatedSeconds) {
        this.message = message;
        this.type = type;
        this.simulatedSeconds = simulatedSeconds;
        this.timestamp = LocalDateTime.now();
    }
}