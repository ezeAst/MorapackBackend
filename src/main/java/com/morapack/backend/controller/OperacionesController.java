package com.morapack.backend.controller;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.repository.RutaAsignadaRepository;
import com.morapack.backend.service.OperacionesDiaDiaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/operaciones")
@CrossOrigin(origins = "*")
public class OperacionesController {

    private final OperacionesDiaDiaService operacionesService;
    private final PedidoRepository pedidoRepository;
    private final RutaAsignadaRepository rutaAsignadaRepository;
    private final AeropuertoRepository aeropuertoRepository;

    public OperacionesController(OperacionesDiaDiaService operacionesService,
                                 PedidoRepository pedidoRepository,
                                 RutaAsignadaRepository rutaAsignadaRepository,
                                 AeropuertoRepository aeropuertoRepository) {
        this.operacionesService = operacionesService;
        this.pedidoRepository = pedidoRepository;
        this.rutaAsignadaRepository = rutaAsignadaRepository;
        this.aeropuertoRepository = aeropuertoRepository;
    }

    /**
     * POST /api/operaciones/start
     * Inicia las operaciones día a día
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> iniciarOperaciones() {

        operacionesService.iniciar(); // Sin parámetros

        Map<String, Object> response = new HashMap<>();
        response.put("status", "iniciado");
        response.put("startTime", operacionesService.getInicioOperaciones());
        response.put("message", "Operaciones día a día iniciadas en tiempo real");

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/operaciones/stop
     * Detiene las operaciones día a día
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> detenerOperaciones() {
        operacionesService.detener();

        Map<String, String> response = new HashMap<>();
        response.put("status", "detenido");
        response.put("message", "Operaciones día a día detenidas");

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/status
     * Obtiene el estado actual de las operaciones
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> obtenerEstado() {
        Map<String, Object> response = new HashMap<>();

        LocalDateTime ahora = LocalDateTime.now();

        // Información general
        response.put("currentDateTime", ahora);
        response.put("activo", operacionesService.isActivo());
        response.put("inicioOperaciones", operacionesService.getInicioOperaciones());

        // Vuelos activos
        List<Map<String, Object>> vuelosActivos = obtenerVuelosActivos(ahora);
        response.put("vuelosActivos", vuelosActivos);

        // Almacenes
        List<Map<String, Object>> almacenes = obtenerAlmacenes();
        response.put("almacenes", almacenes);

        // Eventos recientes
        response.put("eventosRecientes", operacionesService.getEventosRecientes());

        // Métricas
        Map<String, Object> metricas = calcularMetricas();
        response.put("metricas", metricas);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/pedidos
     * Lista todos los pedidos con su estado
     */
    @GetMapping("/pedidos")
    public ResponseEntity<List<Map<String, Object>>> listarPedidos(
            @RequestParam(required = false) String estado) {

        List<Pedido> pedidos;

        if (estado != null) {
            EstadoPedido estadoPedido = EstadoPedido.valueOf(estado);
            pedidos = pedidoRepository.findByEstadoIn(List.of(estadoPedido));
        } else {
            pedidos = pedidoRepository.findAll();
        }

        List<Map<String, Object>> response = pedidos.stream()
                .map(this::pedidoToMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/vuelos-activos
     * Lista solo vuelos que están en el aire
     */
    @GetMapping("/vuelos-activos")
    public ResponseEntity<List<Map<String, Object>>> listarVuelosActivos() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Map<String, Object>> vuelos = obtenerVuelosActivos(ahora);
        return ResponseEntity.ok(vuelos);
    }

    // ========== MÉTODOS AUXILIARES ==========

    private List<Map<String, Object>> obtenerVuelosActivos(LocalDateTime ahora) {
        List<Map<String, Object>> vuelos = new ArrayList<>();

        // Buscar pedidos EN_TRANSITO
        List<Pedido> pedidosEnVuelo = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.EN_TRANSITO)
        );

        for (Pedido pedido : pedidosEnVuelo) {
            RutaAsignada ruta = rutaAsignadaRepository.findByPedidoId(pedido.getId());
            if (ruta == null) continue;

            Integer tramoActual = pedido.getTramoActual();
            if (tramoActual == null || tramoActual >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoActual);

            // Obtener coordenadas de aeropuertos
            AeropuertoEntity origen = aeropuertoRepository.findByCodigo(tramo.getOrigen()).orElse(null);
            AeropuertoEntity destino = aeropuertoRepository.findByCodigo(tramo.getDestino()).orElse(null);

            if (origen == null || destino == null) continue;

            // Construir fecha/hora de salida y llegada
            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
            LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

            // Ajustar si la llegada es al día siguiente
            if (horaLlegada.isBefore(horaSalida)) {
                horaLlegada = horaLlegada.plusDays(1);
            }

            // Calcular duración y progreso
            long durationSeconds = java.time.Duration.between(horaSalida, horaLlegada).getSeconds();
            long elapsedSeconds = java.time.Duration.between(horaSalida, ahora).getSeconds();

            double progress = Math.min(1.0, Math.max(0.0, (double) elapsedSeconds / durationSeconds));

            // Interpolar coordenadas
            double currentLat = origen.getLat() + (destino.getLat() - origen.getLat()) * progress;
            double currentLng = origen.getLon() + (destino.getLon() - origen.getLon()) * progress;

            Map<String, Object> vuelo = new HashMap<>();
            vuelo.put("id", "F-" + tramo.getOrigen() + "-" + tramo.getDestino() + "-" + pedido.getId());
            vuelo.put("flightCode", tramo.getOrigen().substring(0, 2) + "-" + tramo.getDestino().substring(0, 2));

            // Ruta: [[lng_origen, lat_origen], [lng_destino, lat_destino]]
            vuelo.put("route", new double[][] {
                    {origen.getLon(), origen.getLat()},
                    {destino.getLon(), destino.getLat()}
            });

            vuelo.put("origin", origen.getNombre());
            vuelo.put("destination", destino.getNombre());

            // Posición actual interpolada
            vuelo.put("currentLat", currentLat);
            vuelo.put("currentLng", currentLng);

            vuelo.put("departureTime", horaSalida.toString());
            vuelo.put("arrivalTime", horaLlegada.toString());
            vuelo.put("durationSeconds", durationSeconds);
            vuelo.put("elapsedSeconds", elapsedSeconds);

            vuelo.put("packages", pedido.getCantidad());
            vuelo.put("capacity", 1000); // Capacidad por defecto (puedes ajustar)

            vuelo.put("status", "EN_VUELO");
            vuelo.put("statusLabel", "En vuelo");
            vuelo.put("progressPercentage", progress * 100.0);

            vuelos.add(vuelo);
        }

        return vuelos;
    }

    private List<Map<String, Object>> obtenerAlmacenes() {
        List<AeropuertoEntity> aeropuertos = aeropuertoRepository.findAll();

        return aeropuertos.stream().map(aero -> {
            Map<String, Object> almacen = new HashMap<>();
            almacen.put("codigo", aero.getCodigo());
            almacen.put("nombre", aero.getNombre());
            almacen.put("capacidad", aero.getCapacidad());
            almacen.put("capacidadActual", aero.getCapacidadActual());

            double ocupacion = (aero.getCapacidadActual() * 100.0) / aero.getCapacidad();
            almacen.put("ocupacion", Math.round(ocupacion * 10) / 10.0);

            // Estados permitidos: normal, warning, critical.
            // Se elimina el estado "full" y se considera >=100 como critical.
            String status = "normal";
            if (ocupacion >= 90) {
                status = "critical"; // incluye saturación >=100
            } else if (ocupacion >= 70) {
                status = "warning";
            }

            almacen.put("status", status);
            almacen.put("lat", aero.getLat());
            almacen.put("lon", aero.getLon());

            return almacen;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> calcularMetricas() {
        Map<String, Object> metricas = new HashMap<>();

        long noAsignados = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.NO_ASIGNADO)).size();
        long asignados = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.ASIGNADO)).size();
        long enTransito = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.EN_TRANSITO)).size();
        long enAlmacen = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.EN_ALMACEN_INTERMEDIO)).size();
        long entregados = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.ENTREGADO)).size();

        metricas.put("pedidosNoAsignados", noAsignados);
        metricas.put("pedidosAsignados", asignados);
        metricas.put("pedidosEnTransito", enTransito);
        metricas.put("pedidosEnAlmacen", enAlmacen);
        metricas.put("pedidosEntregados", entregados);

        long total = noAsignados + asignados + enTransito + enAlmacen + entregados;
        metricas.put("total", total);

        return metricas;
    }

    private Map<String, Object> pedidoToMap(Pedido pedido) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pedido.getId());
        map.put("dia", pedido.getDia());
        map.put("mes", pedido.getMes());
        map.put("hora", pedido.getHora());
        map.put("minuto", pedido.getMinuto());
        map.put("destino", pedido.getAeropuertoDestino());
        map.put("cantidad", pedido.getCantidad());
        map.put("estado", pedido.getEstado().toString());
        map.put("tramoActual", pedido.getTramoActual());
        return map;
    }


}