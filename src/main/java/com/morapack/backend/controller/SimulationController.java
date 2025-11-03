package com.morapack.backend.controller;

import com.morapack.backend.dto.request.ControlSimulationRequest;
import com.morapack.backend.dto.request.CreateSimulationRequest;
import com.morapack.backend.dto.response.SimulationResponse;
import com.morapack.backend.dto.response.SimulationStatusResponse;
import com.morapack.backend.service.PlanificadorPersistenciaService;
import com.morapack.backend.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations")
@CrossOrigin(origins = "*")
public class SimulationController {


    @Autowired
    private SimulationService simulationService;



    /**
     * POST /api/simulations
     * Crea y ejecuta una nueva simulación
     */
    @PostMapping
    public ResponseEntity<SimulationResponse> createSimulation(
            @Valid @RequestBody CreateSimulationRequest request) {
        try {
            SimulationResponse response = simulationService.createSimulation(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/simulations/{id}/status
     * Obtiene el estado actual de la simulación
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<SimulationStatusResponse> getSimulationStatus(
            @PathVariable String id) {
        try {
            SimulationStatusResponse response = simulationService.getSimulationStatus(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PATCH /api/simulations/{id}/control
     * Controla la simulación (pause/resume/stop)
     */
    @PatchMapping("/{id}/control")
    public ResponseEntity<Map<String, String>> controlSimulation(
            @PathVariable String id,
            @Valid @RequestBody ControlSimulationRequest request) {
        try {
            Map<String, String> response = simulationService.controlSimulation(id, request.getAction());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/simulations
     * Lista todas las simulaciones activas
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllSimulations() {
        try {
            List<Map<String, Object>> simulations = simulationService.getAllSimulations();
            return ResponseEntity.ok(simulations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/simulations/health
     * Health check del servicio de simulaciones
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "Simulation Service",
                "version", "1.0.0"
        ));
    }



}