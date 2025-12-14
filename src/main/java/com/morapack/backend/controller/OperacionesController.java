package com.morapack.backend.controller;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.algoritmologistica.algorithm.models.Vuelo; // ✅ NUEVO
import com.morapack.backend.dto.InicioOperacionesRequest;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.repository.RutaAsignadaRepository;
import com.morapack.backend.repository.VueloRepository; // ✅ NUEVO
import com.morapack.backend.service.OperacionesDiaDiaService;
import com.morapack.backend.service.TiempoSimuladoService;
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
    private final TiempoSimuladoService tiempoSimuladoService;
    private final VueloRepository vueloRepository; // ✅ NUEVO

    public OperacionesController(OperacionesDiaDiaService operacionesService,
                                 PedidoRepository pedidoRepository,
                                 RutaAsignadaRepository rutaAsignadaRepository,
                                 AeropuertoRepository aeropuertoRepository,
                                 TiempoSimuladoService tiempoSimuladoService,
                                 VueloRepository vueloRepository) {
        this.operacionesService = operacionesService;
        this.pedidoRepository = pedidoRepository;
        this.rutaAsignadaRepository = rutaAsignadaRepository;
        this.aeropuertoRepository = aeropuertoRepository;
        this.tiempoSimuladoService = tiempoSimuladoService;
        this.vueloRepository = vueloRepository; // ✅ NUEVO
    }

    /**
     * POST /api/operaciones/start
     * Inicia las operaciones día a día con una fecha/hora específica
     *
     * Body JSON:
     * {
     *   "fechaHoraInicio": "2025-01-15T08:00:00"
     * }
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> iniciarOperaciones(@RequestBody InicioOperacionesRequest request) {
        try {
            System.out.println("📥 Request recibido para iniciar operaciones");
            System.out.println("📋 Request string: " + request.getFechaHoraInicio());

            // Validar que el string no sea null
            if (request.getFechaHoraInicio() == null || request.getFechaHoraInicio().trim().isEmpty()) {
                System.err.println("❌ Error: fechaHoraInicio es null o vacío");
                Map<String, Object> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "Debe proporcionar fechaHoraInicio en formato ISO: yyyy-MM-dd'T'HH:mm:ss");
                return ResponseEntity.badRequest().body(error);
            }

            // Parsear el string a LocalDateTime
            LocalDateTime fechaHoraInicio;
            try {
                fechaHoraInicio = request.toLocalDateTime();
                System.out.println("📅 Fecha/Hora parseada: " + fechaHoraInicio);
            } catch (Exception e) {
                System.err.println("❌ Error parseando fecha: " + e.getMessage());
                Map<String, Object> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "Formato de fecha inválido. Use: yyyy-MM-dd'T'HH:mm:ss (ejemplo: 2025-01-15T08:00:00)");
                return ResponseEntity.badRequest().body(error);
            }

            System.out.println("🚀 Iniciando operaciones con fecha/hora: " + fechaHoraInicio);

            // Iniciar operaciones con la fecha/hora proporcionada
            operacionesService.iniciar(fechaHoraInicio);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "iniciado");
            response.put("startTime", operacionesService.getInicioOperaciones().toString());
            response.put("tiempoSimulado", fechaHoraInicio.toString());
            response.put("message", "Operaciones día a día iniciadas desde " + fechaHoraInicio);

            System.out.println("✅ Operaciones iniciadas exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error al iniciar operaciones: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al iniciar operaciones: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
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

        // Usar tiempo simulado en lugar de tiempo real
        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();

        // Información general
        response.put("currentDateTime", ahora);
        response.put("usandoTiempoSimulado", tiempoSimuladoService.isUsandoTiempoSimulado());
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
        // ✅ Usar tiempo simulado en lugar de tiempo real
        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();
        List<Map<String, Object>> vuelos = obtenerVuelosActivos(ahora);
        return ResponseEntity.ok(vuelos);
    }

    /**
     * GET /api/operaciones/vuelos/{origen}/{destino}/{fecha}/{hora}/pedidos
     * Lista los pedidos que van en un vuelo específico
     * Ejemplo: /api/operaciones/vuelos/JFK/MAD/2025-01-15/14:30/pedidos
     */
    @GetMapping("/vuelos/{origen}/{destino}/{fecha}/{hora}/pedidos")
    public ResponseEntity<List<Map<String, Object>>> listarPedidosEnVuelo(
            @PathVariable String origen,
            @PathVariable String destino,
            @PathVariable String fecha,
            @PathVariable String hora) {

        List<Pedido> pedidos = pedidoRepository.findPedidosEnVuelo(origen, destino, fecha, hora);

        List<Map<String, Object>> response = pedidos.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("aeropuertoDestino", p.getAeropuertoDestino());
                    map.put("cantidad", p.getCantidad());
                    map.put("estado", p.getEstado().toString());
                    map.put("tramoActual", p.getTramoActual());
                    map.put("fecha", String.format("%04d-%02d-%02d", p.getAnho(), p.getMes(), p.getDia()));
                    map.put("idCliente", p.getIdCliente());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
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


        // ✅ NUEVO: Estructura para agrupar pedidos Y sus rutas por vuelo
        Map<String, List<Pedido>> pedidosPorVuelo = new HashMap<>();
        Map<String, List<RutaAsignada>> rutasPorVuelo = new HashMap<>(); // ✅ NUEVO
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
            rutasPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(ruta); // ✅ NUEVO
            tramoPorVuelo.putIfAbsent(vueloKey, tramo);
        }


        for (Map.Entry<String, List<Pedido>> entry : pedidosPorVuelo.entrySet()) {
            String vueloKey = entry.getKey();
            List<Pedido> pedidosDelVuelo = entry.getValue();
            List<RutaAsignada> rutasDelVuelo = rutasPorVuelo.get(vueloKey); // ✅ NUEVO
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


            // ✅ CAMBIO PRINCIPAL: Usar cantidad de RutaAsignada en lugar de Pedido
            int totalPaquetes = rutasDelVuelo.stream()
                    .mapToInt(RutaAsignada::getCantidad)
                    .sum();

            // ✅ Extraer IDs y cantidad de RUTAS (no de pedidos)
            List<Map<String, Object>> orderDetails = new ArrayList<>();
            List<String> orderIds = new ArrayList<>(); // ✅ NUEVO: Lista simple de IDs para frontend

            for (int i = 0; i < pedidosDelVuelo.size(); i++) {
                Pedido pedido = pedidosDelVuelo.get(i);
                RutaAsignada ruta = rutasDelVuelo.get(i);

                String pedidoId = String.valueOf(pedido.getId());

                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("id", pedidoId);
                orderMap.put("cantidad", ruta.getCantidad()); // ✅ Usar cantidad de la ruta
                orderDetails.add(orderMap);

                orderIds.add(pedidoId); // ✅ NUEVO: Para el tooltip del frontend
            }

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
            vuelo.put("pedidoCount", pedidosDelVuelo.size()); // ✅ Cuántos pedidos lleva
            vuelo.put("orders", orderDetails); // ✅ Lista de objetos con id y cantidad de RUTA
            vuelo.put("orderIds", orderIds); // ✅ NUEVO: Lista simple de IDs para tooltip del frontend

            // ✅ Obtener capacidad real desde tabla de vuelos
            int capacidadVuelo = obtenerCapacidadVuelo(tramo.getOrigen(), tramo.getDestino(), horaSalida);
            vuelo.put("capacity", capacidadVuelo);

            // 🎯 CAP: Si excede capacidad, mostrar máximo la capacidad (para que muestre 100% máx)
            int paquetesMostrados = Math.min(totalPaquetes, capacidadVuelo);
            vuelo.put("packages", paquetesMostrados);

            vuelo.put("status", "EN_VUELO");
            vuelo.put("statusLabel", "En vuelo");
            vuelo.put("progressPercentage", progress * 100.0);

            vuelos.add(vuelo);
        }

        return vuelos;
    }


    private List<Map<String, Object>> obtenerAlmacenes() {
        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();
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
            String status = "normal";
            if (ocupacion >= 90) {
                status = "critical";
            } else if (ocupacion >= 70) {
                status = "warning";
            }

            almacen.put("status", status);
            almacen.put("lat", aero.getLat());
            almacen.put("lon", aero.getLon());

            // ✅ Obtener los 3 próximos vuelos programados desde este almacén
            List<Map<String, Object>> outgoingFlights = obtenerProximosVuelosDesdeAlmacen(aero.getCodigo(), ahora);
            almacen.put("outgoingFlights", outgoingFlights);

            // ✅ NUEVO: Obtener los 3 primeros pedidos próximos a salir desde este almacén
            List<Map<String, Object>> outgoingOrders = obtenerProximosPedidosDesdeAlmacen(aero.getCodigo(), ahora);
            almacen.put("outgoingOrders", outgoingOrders);

            return almacen;
        }).collect(Collectors.toList());
    }

    /**
     * ✅ NUEVO: Obtiene los 3 próximos vuelos programados desde un almacén
     */
    private List<Map<String, Object>> obtenerProximosVuelosDesdeAlmacen(String codigoAlmacen, LocalDateTime ahora) {
        List<Map<String, Object>> vuelos = new ArrayList<>();

        // Buscar pedidos ASIGNADOS o EN_ALMACEN_INTERMEDIO que saldrán desde este almacén
        String fechaStr = ahora.toLocalDate().toString();
        String fechaMananaStr = ahora.plusDays(1).toLocalDate().toString();
        String horaActualStr = ahora.toLocalTime().toString().substring(0, 5);

        // Query para encontrar pedidos con próximos vuelos desde este almacén
        List<Pedido> pedidosConVuelos = pedidoRepository.findActivosConVuelosProximos(
                fechaStr,
                fechaMananaStr,
                horaActualStr,
                "23:59", // Buscar hasta final del día siguiente
                ahora.minusDays(1).toLocalDate().toString()
        );

        if (pedidosConVuelos.isEmpty()) return vuelos;

        // Cargar rutas
        List<Long> pedidoIds = pedidosConVuelos.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);
        Map<Long, RutaAsignada> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.toMap(RutaAsignada::getPedidoId, r -> r));

        // ✅ NUEVO: Agrupar pedidos Y rutas por vuelo
        Map<String, List<Pedido>> pedidosPorVuelo = new HashMap<>();
        Map<String, List<RutaAsignada>> rutasPorVuelo = new HashMap<>(); // ✅ NUEVO
        Map<String, RutaTramo> tramoPorVuelo = new HashMap<>();
        Map<String, LocalDateTime> horaSalidaPorVuelo = new HashMap<>();

        for (Pedido pedido : pedidosConVuelos) {
            RutaAsignada ruta = rutasPorPedido.get(pedido.getId());
            if (ruta == null) continue;

            // Determinar el tramo correcto según el estado del pedido
            Integer tramoIndex = null;
            if (pedido.getEstado() == EstadoPedido.ASIGNADO) {
                tramoIndex = 0; // Primer vuelo
            } else if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
                tramoIndex = pedido.getTramoActual(); // Siguiente vuelo
            }

            if (tramoIndex == null || tramoIndex >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoIndex);

            // Filtrar solo vuelos que salen desde este almacén
            if (!tramo.getOrigen().equals(codigoAlmacen)) continue;

            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));

            // Solo vuelos futuros
            if (horaSalida.isBefore(ahora)) continue;

            String vueloKey = tramo.getOrigen() + "-" + tramo.getDestino() + "-" +
                    tramo.getFecha() + "-" + tramo.getHoraSalida();

            pedidosPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(pedido);
            rutasPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(ruta); // ✅ NUEVO
            tramoPorVuelo.putIfAbsent(vueloKey, tramo);
            horaSalidaPorVuelo.putIfAbsent(vueloKey, horaSalida);
        }

        // Ordenar vuelos por hora de salida y tomar los 3 primeros
        List<Map.Entry<String, LocalDateTime>> vuelosOrdenados = new ArrayList<>(horaSalidaPorVuelo.entrySet());
        vuelosOrdenados.sort(Map.Entry.comparingByValue());

        int count = 0;
        for (Map.Entry<String, LocalDateTime> entry : vuelosOrdenados) {
            if (count >= 3) break;

            String vueloKey = entry.getKey();
            RutaTramo tramo = tramoPorVuelo.get(vueloKey);
            List<Pedido> pedidosDelVuelo = pedidosPorVuelo.get(vueloKey);
            List<RutaAsignada> rutasDelVuelo = rutasPorVuelo.get(vueloKey); // ✅ NUEVO
            LocalDateTime horaSalida = entry.getValue();

            AeropuertoEntity destino = aeropuertoRepository.findByCodigo(tramo.getDestino()).orElse(null);
            if (destino == null) continue;

            // ✅ CAMBIO: Usar cantidad de RutaAsignada en lugar de Pedido
            int totalPaquetes = rutasDelVuelo.stream()
                    .mapToInt(RutaAsignada::getCantidad)
                    .sum();

            // Calcular hora de llegada
            LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));
            if (horaLlegada.isBefore(horaSalida)) {
                horaLlegada = horaLlegada.plusDays(1);
            }

            Map<String, Object> vuelo = new HashMap<>();
            vuelo.put("id", vueloKey);
            vuelo.put("flightCode", tramo.getOrigen() + "-" + tramo.getDestino());
            vuelo.put("destination", destino.getNombre());
            vuelo.put("departureTime", horaSalida.toString());
            vuelo.put("arrivalTime", horaLlegada.toString());

            // ✅ Obtener capacidad real desde tabla de vuelos
            int capacidadVuelo = obtenerCapacidadVuelo(tramo.getOrigen(), tramo.getDestino(), horaSalida);
            vuelo.put("capacity", capacidadVuelo);

            // 🎯 CAP: Si excede capacidad, mostrar máximo la capacidad (para que muestre 100% máx)
            int paquetesMostrados = Math.min(totalPaquetes, capacidadVuelo);
            vuelo.put("packages", paquetesMostrados);

            vuelo.put("status", "scheduled");
            vuelo.put("occupancyPercentage", Math.min(100.0, (totalPaquetes * 100.0) / capacidadVuelo)); // ✅ Cap a 100%

            vuelos.add(vuelo);
            count++;
        }

        return vuelos;
    }

    /**
     * ✅ NUEVO: Obtiene los 3 primeros pedidos próximos a salir desde un almacén
     */
    private List<Map<String, Object>> obtenerProximosPedidosDesdeAlmacen(String codigoAlmacen, LocalDateTime ahora) {
        List<Map<String, Object>> pedidosProximos = new ArrayList<>();

        // Buscar pedidos ASIGNADOS o EN_ALMACEN_INTERMEDIO que están en este almacén
        String fechaStr = ahora.toLocalDate().toString();
        String fechaMananaStr = ahora.plusDays(1).toLocalDate().toString();
        String horaActualStr = ahora.toLocalTime().toString().substring(0, 5);

        // Obtener pedidos activos
        List<Pedido> pedidosActivos = pedidoRepository.findActivosConVuelosProximos(
                fechaStr,
                fechaMananaStr,
                horaActualStr,
                "23:59",
                ahora.minusDays(1).toLocalDate().toString()
        );

        if (pedidosActivos.isEmpty()) return pedidosProximos;

        // Cargar rutas
        List<Long> pedidoIds = pedidosActivos.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);
        Map<Long, RutaAsignada> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.toMap(RutaAsignada::getPedidoId, r -> r));

        // Lista de pedidos candidatos con su hora de salida
        List<Map.Entry<Pedido, LocalDateTime>> pedidosConHora = new ArrayList<>();

        for (Pedido pedido : pedidosActivos) {
            // Solo pedidos ASIGNADOS o EN_ALMACEN_INTERMEDIO
            if (pedido.getEstado() != EstadoPedido.ASIGNADO &&
                    pedido.getEstado() != EstadoPedido.EN_ALMACEN_INTERMEDIO) {
                continue;
            }

            RutaAsignada ruta = rutasPorPedido.get(pedido.getId());
            if (ruta == null) continue;

            // Determinar el tramo actual
            Integer tramoIndex = null;
            if (pedido.getEstado() == EstadoPedido.ASIGNADO) {
                tramoIndex = 0; // Primer vuelo
            } else if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
                tramoIndex = pedido.getTramoActual(); // Siguiente vuelo
            }

            if (tramoIndex == null || tramoIndex >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoIndex);

            // Filtrar solo pedidos que salen desde este almacén
            if (!tramo.getOrigen().equals(codigoAlmacen)) continue;

            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));

            // Solo vuelos futuros
            if (horaSalida.isBefore(ahora)) continue;

            pedidosConHora.add(Map.entry(pedido, horaSalida));
        }

        // Ordenar por hora de salida y tomar los 3 primeros
        pedidosConHora.sort(Map.Entry.comparingByValue());

        int count = 0;
        for (Map.Entry<Pedido, LocalDateTime> entry : pedidosConHora) {
            if (count >= 3) break;

            Pedido pedido = entry.getKey();
            LocalDateTime horaSalida = entry.getValue();

            RutaAsignada ruta = rutasPorPedido.get(pedido.getId());
            Integer tramoIndex = (pedido.getEstado() == EstadoPedido.ASIGNADO) ? 0 : pedido.getTramoActual();
            RutaTramo tramo = ruta.getTramos().get(tramoIndex);

            Map<String, Object> pedidoMap = new HashMap<>();
            pedidoMap.put("orderId", String.valueOf(pedido.getId()));
            pedidoMap.put("destination", pedido.getAeropuertoDestino());
            pedidoMap.put("flightCode", tramo.getOrigen() + "-" + tramo.getDestino());
            pedidoMap.put("departureTime", horaSalida.toString());
            pedidoMap.put("weight", pedido.getCantidad());
            pedidoMap.put("registeredTime", pedido.getFechaPedido().toString());

            pedidosProximos.add(pedidoMap);
            count++;
        }

        return pedidosProximos;
    }

    private Map<String, Object> calcularMetricas() {
        Map<String, Object> metricas = new HashMap<>();


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
                case RECOGIDO -> {} // Estado de limpieza, no contabilizar
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



    /**
     * ✅ NUEVO: Obtiene la capacidad real de un vuelo desde la tabla de vuelos
     *
     * @param origen Código ICAO del aeropuerto de origen
     * @param destino Código ICAO del aeropuerto de destino
     * @param horaSalida Hora de salida del vuelo
     * @return Capacidad máxima del vuelo, o 1000 por defecto si no se encuentra
     */
    private int obtenerCapacidadVuelo(String origen, String destino, LocalDateTime horaSalida) {
        try {
            // Intentar buscar el vuelo exacto por origen, destino y hora de salida
            return vueloRepository.findByOrigenDestinoHoraSalida(origen, destino, horaSalida)
                    .map(Vuelo::getCapacidadMaxima)
                    .orElseGet(() -> {
                        // Si no se encuentra por hora exacta, buscar por origen y destino
                        // y tomar el primero (asumiendo que todos los vuelos de esa ruta tienen la misma capacidad)
                        List<Vuelo> vuelos = vueloRepository.findByOrigenAndDestino(origen, destino);
                        if (!vuelos.isEmpty()) {
                            int capacidad = vuelos.get(0).getCapacidadMaxima();
                            System.out.println("⚠️ Vuelo exacto no encontrado, usando capacidad de ruta " +
                                    origen + "-" + destino + ": " + capacidad);
                            return capacidad;
                        }
                        // Si no hay ningún vuelo, usar valor por defecto
                        System.out.println("⚠️ No se encontró vuelo para " + origen + "-" + destino +
                                ", usando capacidad por defecto: 1000");
                        return 1000;
                    });
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo capacidad del vuelo: " + e.getMessage());
            return 1000; // Valor por defecto en caso de error
        }
    }
}