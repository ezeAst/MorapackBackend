package com.morapack.algoritmologistica.controller;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.*;
import com.morapack.algoritmologistica.service.PlanificadorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planificacion")
@CrossOrigin(origins = "*") // Permite peticiones desde cualquier origen
public class PlanificacionController {

    @Autowired
    private PlanificadorService planificadorService;

    /**
     * Endpoint principal: ejecuta la planificación con datos por defecto
     * GET http://localhost:8080/api/planificacion/ejecutar
     */
    @GetMapping("/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutarPlanificacion() {
        try {
            Solucion solucion = planificadorService.ejecutarPlanificacion();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mensaje", "Planificación ejecutada correctamente");
            response.put("fitness", solucion.getFitness());
            response.put("numeroRutas", solucion.getNumeroDeRutas());
            response.put("pedidosATiempo", solucion.getPedidosEntregadosATiempo());
            response.put("violacionesVuelos", solucion.getViolacionesCapacidadVuelos());
            response.put("violacionesAlmacenes", solucion.getViolacionesCapacidadAlmacenes());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("mensaje", "Error al ejecutar planificación");
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Obtiene todos los aeropuertos
     * GET http://localhost:8080/api/planificacion/aeropuertos
     */
    @GetMapping("/aeropuertos")
    public ResponseEntity<List<Aeropuerto>> obtenerAeropuertos() {
        try {
            List<Aeropuerto> aeropuertos = planificadorService.obtenerAeropuertos();
            return ResponseEntity.ok(aeropuertos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene todos los vuelos
     * GET http://localhost:8080/api/planificacion/vuelos
     */
    @GetMapping("/vuelos")
    public ResponseEntity<List<Vuelo>> obtenerVuelos() {
        try {
            List<Vuelo> vuelos = planificadorService.obtenerVuelos();
            return ResponseEntity.ok(vuelos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtiene todos los pedidos
     * GET http://localhost:8080/api/planificacion/pedidos
     */
    @GetMapping("/pedidos")
    public ResponseEntity<List<Pedido>> obtenerPedidos() {
        try {
            List<Pedido> pedidos = planificadorService.obtenerPedidos();
            return ResponseEntity.ok(pedidos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check
     * GET http://localhost:8080/api/planificacion/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "OK");
        status.put("servicio", "Planificación de Rutas");
        status.put("version", "1.0.0");
        return ResponseEntity.ok(status);
    }
}