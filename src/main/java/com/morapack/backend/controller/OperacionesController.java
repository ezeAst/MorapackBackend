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
            pedidos = pedidoRepository.findAllWithLimit();
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

        String fechaStr = ahora.toLocalDate().toString();
        String horaActualStr = ahora.toLocalTime().toString().substring(0, 5);

        List<Pedido> pedidosEnVuelo = pedidoRepository.findEnTransitoConVuelosActivos(
                fechaStr,
                horaActualStr
        );

        if (pedidosEnVuelo.isEmpty()) return vuelos;

        List<Long> pedidoIds = pedidosEnVuelo.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);
        Map<Long, RutaAsignada> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.toMap(RutaAsignada::getPedidoId, r -> r));

        // ✅ AGRUPAR POR VUELO: origen-destino-fecha-hora
        Map<String, List<Pedido>> pedidosPorVuelo = new HashMap<>();
        Map<String, RutaTramo> tramoPorVuelo = new HashMap<>();

        for (Pedido pedido : pedidosEnVuelo) {
            RutaAsignada ruta = rutasPorPedido.get(pedido.getId());
            if (ruta == null) continue;

            Integer tramoActual = pedido.getTramoActual();
            if (tramoActual == null || tramoActual >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoActual);

            // Clave única del vuelo
            String vueloKey = tramo.getOrigen() + "-" + tramo.getDestino() + "-" +
                    tramo.getFecha() + "-" + tramo.getHoraSalida();

            pedidosPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(pedido);
            tramoPorVuelo.putIfAbsent(vueloKey, tramo);
        }

        // ✅ CREAR UN SOLO OBJETO POR VUELO
        for (Map.Entry<String, List<Pedido>> entry : pedidosPorVuelo.entrySet()) {
            String vueloKey = entry.getKey();
            List<Pedido> pedidosDelVuelo = entry.getValue();
            RutaTramo tramo = tramoPorVuelo.get(vueloKey);

            AeropuertoEntity origen = aeropuertoRepository.findByCodigo(tramo.getOrigen()).orElse(null);
            AeropuertoEntity destino = aeropuertoRepository.findByCodigo(tramo.getDestino()).orElse(null);
            if (origen == null || destino == null) continue;

            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
            LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

            if (horaLlegada.isBefore(horaSalida)) {
                horaLlegada = horaLlegada.plusDays(1);
            }

            long durationSeconds = java.time.Duration.between(horaSalida, horaLlegada).getSeconds();
            long elapsedSeconds = java.time.Duration.between(horaSalida, ahora).getSeconds();
            double progress = Math.min(1.0, Math.max(0.0, (double) elapsedSeconds / durationSeconds));

            double currentLat = origen.getLat() + (destino.getLat() - origen.getLat()) * progress;
            double currentLng = origen.getLon() + (destino.getLon() - origen.getLon()) * progress;

            // ✅ SUMAR PAQUETES DE TODOS LOS PEDIDOS EN ESTE VUELO
            int totalPaquetes = pedidosDelVuelo.stream().mapToInt(Pedido::getCantidad).sum();

            Map<String, Object> vuelo = new HashMap<>();
            vuelo.put("id", vueloKey); // ID único del vuelo
            vuelo.put("flightCode", tramo.getOrigen().substring(0, 2) + "-" + tramo.getDestino().substring(0, 2));
            vuelo.put("route", new double[][]{{origen.getLon(), origen.getLat()}, {destino.getLon(), destino.getLat()}});
            vuelo.put("origin", origen.getNombre());
            vuelo.put("destination", destino.getNombre());
            vuelo.put("currentLat", currentLat);
            vuelo.put("currentLng", currentLng);
            vuelo.put("departureTime", horaSalida.toString());
            vuelo.put("arrivalTime", horaLlegada.toString());
            vuelo.put("durationSeconds", durationSeconds);
            vuelo.put("elapsedSeconds", elapsedSeconds);
            vuelo.put("packages", totalPaquetes); // ✅ Total de paquetes
            vuelo.put("pedidoCount", pedidosDelVuelo.size()); // ✅ Cuántos pedidos lleva
            vuelo.put("capacity", 1000);
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

        // ✅ OPTIMIZACIÓN: Un solo query con GROUP BY en vez de 5 queries
        List<Object[]> conteos = pedidoRepository.countByEstadoGrouped();

        long noAsignados = 0, asignados = 0, enTransito = 0, enAlmacen = 0, entregados = 0;

        for (Object[] row : conteos) {
            EstadoPedido estado = (EstadoPedido) row[0];
            Long count = (Long) row[1];

            switch (estado) {
                case NO_ASIGNADO -> noAsignados = count;
                case ASIGNADO -> asignados = count;
                case EN_TRANSITO -> enTransito = count;
                case EN_ALMACEN_INTERMEDIO -> enAlmacen = count;
                case ENTREGADO -> entregados = count;
            }
        }

        metricas.put("pedidosNoAsignados", noAsignados);
        metricas.put("pedidosAsignados", asignados);
        metricas.put("pedidosEnTransito", enTransito);
        metricas.put("pedidosEnAlmacen", enAlmacen);
        metricas.put("pedidosEntregados", entregados);
        metricas.put("total", noAsignados + asignados + enTransito + enAlmacen + entregados);

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