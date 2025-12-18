package com.morapack.backend.controller;

import com.morapack.backend.dto.VueloCancelacionRequest;
import com.morapack.backend.dto.VueloCancelacionResponse;
import com.morapack.backend.entity.VueloCancelado;
import com.morapack.backend.service.VueloCancelacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador para gestionar cancelaciones de vuelos
 */
@RestController
@RequestMapping("/api/vuelos/cancelaciones")
@CrossOrigin(origins = "*")
public class VueloCancelacionController {

    private final VueloCancelacionService cancelacionService;

    public VueloCancelacionController(VueloCancelacionService cancelacionService) {
        this.cancelacionService = cancelacionService;
    }

    /**
     * POST /api/vuelos/cancelaciones
     *
     * Registra cancelaciones de vuelos y reasigna pedidos afectados
     *
     * Body ejemplo:
     * {
     *   "fecha": "2025-01-15",
     *   "vuelosCancelados": [
     *     "SPIM-SKBO-04:35-08:51-0340",
     *     "SPIM-SKBO-08:02-12:18-0300"
     *   ]
     * }
     */
    @PostMapping
    public ResponseEntity<VueloCancelacionResponse> registrarCancelaciones(
            @RequestBody VueloCancelacionRequest request) {

        try {
            VueloCancelacionResponse response = cancelacionService.procesarCancelaciones(request);

            if (response.isExito()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            System.err.println("❌ Error procesando cancelaciones: " + e.getMessage());
            e.printStackTrace();

            VueloCancelacionResponse errorResponse =
                    VueloCancelacionResponse.error("Error interno: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * GET /api/vuelos/cancelaciones/activas
     *
     * Obtiene todas las cancelaciones activas
     */
    @GetMapping("/activas")
    public ResponseEntity<Map<String, Object>> obtenerCancelacionesActivas() {
        try {
            List<VueloCancelado> cancelaciones = cancelacionService.obtenerCancelacionesActivas();

            Map<String, Object> response = new HashMap<>();
            response.put("total", cancelaciones.size());
            response.put("cancelaciones", cancelaciones);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * GET /api/vuelos/cancelaciones/fecha/{fecha}
     *
     * Obtiene cancelaciones para una fecha específica
     */
    @GetMapping("/fecha/{fecha}")
    public ResponseEntity<Map<String, Object>> obtenerCancelacionesPorFecha(
            @PathVariable String fecha) {

        try {
            LocalDate fechaParsed = LocalDate.parse(fecha);
            List<VueloCancelado> cancelaciones =
                    cancelacionService.obtenerCancelacionesPorFecha(fechaParsed);

            Map<String, Object> response = new HashMap<>();
            response.put("fecha", fecha);
            response.put("total", cancelaciones.size());
            response.put("cancelaciones", cancelaciones);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * DELETE /api/vuelos/cancelaciones/antiguas/{dias}
     *
     * Limpia cancelaciones más antiguas que X días
     */
    @DeleteMapping("/antiguas/{dias}")
    public ResponseEntity<Map<String, Object>> limpiarCancelacionesAntiguas(
            @PathVariable int dias) {

        try {
            int eliminadas = cancelacionService.limpiarCancelacionesAntiguas(dias);

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Cancelaciones antiguas eliminadas");
            response.put("eliminadas", eliminadas);
            response.put("diasAtras", dias);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * POST /api/vuelos/cancelaciones/verificar
     *
     * Verifica si un vuelo específico está cancelado
     */
    @PostMapping("/verificar")
    public ResponseEntity<Map<String, Object>> verificarVueloCancelado(
            @RequestBody Map<String, String> request) {

        try {
            String origen = request.get("origen");
            String destino = request.get("destino");
            LocalDate fecha = LocalDate.parse(request.get("fecha"));
            String horaSalida = request.get("horaSalida");

            boolean cancelado = cancelacionService.estaCancelado(origen, destino, fecha, horaSalida);

            Map<String, Object> response = new HashMap<>();
            response.put("cancelado", cancelado);
            response.put("vuelo", origen + "-" + destino + "-" + fecha + "-" + horaSalida);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}