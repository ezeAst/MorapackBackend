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

        List<Pedido> pedidosEnTransito = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.EN_TRANSITO));
        if (pedidosEnTransito.isEmpty()) return vuelos;

        System.out.println("🔍 Pedidos EN_TRANSITO encontrados: " + pedidosEnTransito.size());

        List<Long> pedidoIds = pedidosEnTransito.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);

        System.out.println("🔍 Rutas totales: " + todasLasRutas.size());

        // ✅ NUEVO: Agrupar por vuelo físico (origen-destino-fecha-hora)
        Map<String, List<RutaAsignada>> rutasPorVuelo = new HashMap<>();
        Map<String, RutaTramo> tramoPorVuelo = new HashMap<>();
        Map<String, Pedido> pedidoPorRuta = new HashMap<>();

        // Crear índice de pedidos
        Map<Long, Pedido> pedidosMap = pedidosEnTransito.stream()
                .collect(Collectors.toMap(Pedido::getId, p -> p));

        // Agrupar rutas por vuelo
        for (RutaAsignada ruta : todasLasRutas) {
            Pedido pedido = pedidosMap.get(ruta.getPedidoId());
            if (pedido == null) continue;

            Integer tramoActual = pedido.getTramoActual();
            if (tramoActual == null || tramoActual >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoActual);

            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
            LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

            if (horaLlegada.isBefore(horaSalida)) {
                horaLlegada = horaLlegada.plusDays(1);
            }

            // Solo vuelos en el aire
            if (ahora.isBefore(horaSalida) || ahora.isAfter(horaLlegada)) continue;

            // Clave única por vuelo físico
            String vueloKey = tramo.getOrigen() + "-" + tramo.getDestino() + "-" +
                    tramo.getFecha() + "-" + tramo.getHoraSalida();

            rutasPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(ruta);
            tramoPorVuelo.putIfAbsent(vueloKey, tramo);
            pedidoPorRuta.put(ruta.getId().toString(), pedido);
        }

        System.out.println("📊 Vuelos físicos únicos: " + rutasPorVuelo.size());

        // Construir objetos de vuelo agrupados
        for (Map.Entry<String, List<RutaAsignada>> entry : rutasPorVuelo.entrySet()) {
            String vueloKey = entry.getKey();
            List<RutaAsignada> rutasEnVuelo = entry.getValue();
            RutaTramo tramo = tramoPorVuelo.get(vueloKey);

            System.out.println("✈️ Procesando vuelo: " + vueloKey + " con " + rutasEnVuelo.size() + " rutas");

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

            // ✅ Calcular totales sumando todas las rutas en este vuelo
            int totalPaquetes = rutasEnVuelo.stream()
                    .mapToInt(RutaAsignada::getCantidad)
                    .sum();

            // ✅ Recopilar todos los IDs de pedidos
            List<String> orderIds = rutasEnVuelo.stream()
                    .map(r -> String.valueOf(r.getPedidoId()))
                    .distinct()
                    .collect(Collectors.toList());

            // ✅ Detalles de cada pedido/ruta en el vuelo
            List<Map<String, Object>> orderDetails = new ArrayList<>();
            for (RutaAsignada ruta : rutasEnVuelo) {
                Pedido pedido = pedidoPorRuta.get(ruta.getId().toString());
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("id", String.valueOf(pedido.getId()));
                orderMap.put("cantidad", ruta.getCantidad());
                orderMap.put("destino", pedido.getAeropuertoDestino());
                orderDetails.add(orderMap);
            }

            Map<String, Object> vuelo = new HashMap<>();
            vuelo.put("id", vueloKey);
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
            vuelo.put("pedidoCount", orderIds.size()); // ✅ Cantidad real de pedidos
            vuelo.put("orderIds", orderIds); // ✅ Todos los IDs
            vuelo.put("orders", orderDetails); // ✅ Detalles completos

            int capacidadVuelo = obtenerCapacidadVuelo(tramo.getOrigen(), tramo.getDestino(), horaSalida);
            vuelo.put("capacity", capacidadVuelo);
            vuelo.put("packages", Math.min(totalPaquetes, capacidadVuelo)); // ✅ Total real
            vuelo.put("status", "EN_VUELO");
            vuelo.put("statusLabel", "En vuelo");
            vuelo.put("progressPercentage", progress * 100.0);

            vuelos.add(vuelo);
            System.out.println("✅ Vuelo agregado: " + vueloKey + " - Pedidos: " + orderIds.size() + " - Paquetes: " + totalPaquetes);
        }

        System.out.println("📊 Total vuelos activos mostrados: " + vuelos.size());
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

        // Buscar TODOS los pedidos activos (sin filtro de fecha restrictivo)
        List<Pedido> pedidosActivos = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.ASIGNADO,
                        EstadoPedido.EN_TRANSITO,
                        EstadoPedido.EN_ALMACEN_INTERMEDIO)
        );



        if (pedidosActivos.isEmpty()) return vuelos;

        // Cargar todas las rutas
        List<Long> pedidoIds = pedidosActivos.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);
        Map<Long, List<RutaAsignada>> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.groupingBy(RutaAsignada::getPedidoId));

        // Agrupar por vuelo
        Map<String, List<Pedido>> pedidosPorVuelo = new HashMap<>();
        Map<String, List<RutaAsignada>> rutasPorVuelo = new HashMap<>();
        Map<String, RutaTramo> tramoPorVuelo = new HashMap<>();
        Map<String, LocalDateTime> horaSalidaPorVuelo = new HashMap<>();

        for (Pedido pedido : pedidosActivos) {
            List<RutaAsignada> rutasDelPedido = rutasPorPedido.get(pedido.getId());
            if (rutasDelPedido == null || rutasDelPedido.isEmpty()) continue;

            RutaAsignada ruta = rutasDelPedido.get(0);


            // ✅ CAMBIO CLAVE: Iterar por TODOS los tramos, no solo el actual
            for (int i = 0; i < ruta.getTramos().size(); i++) {
                RutaTramo tramo = ruta.getTramos().get(i);

                // Solo considerar tramos que salen de este almacén
                if (!tramo.getOrigen().equals(codigoAlmacen)) {
                    continue;
                }


                LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));

                // Solo vuelos futuros


                // ✅ LÓGICA CORREGIDA
                boolean puedeTomarVuelo = false;
                String razon = "";

                if (pedido.getEstado() == EstadoPedido.ASIGNADO && i == 0) {
                    // Pedido aún no ha salido, solo mostrar en su almacén de origen
                    puedeTomarVuelo = true;
                    razon = "Pedido ASIGNADO en almacén de origen";

                } else if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO && i == pedido.getTramoActual()) {
                    // Pedido YA ESTÁ en este almacén esperando
                    puedeTomarVuelo = true;
                    razon = "Pedido EN_ALMACEN_INTERMEDIO esperando en " + codigoAlmacen;

                } else if (pedido.getEstado() == EstadoPedido.EN_TRANSITO && i == pedido.getTramoActual() + 1) {
                    // Verificar si está llegando a este almacén
                    RutaTramo tramoActual = ruta.getTramos().get(pedido.getTramoActual());
                    if (tramoActual.getDestino().equals(codigoAlmacen)) {
                        puedeTomarVuelo = true;
                        razon = "Pedido EN_TRANSITO llegará a " + codigoAlmacen;
                    } else {
                        razon = "Pedido EN_TRANSITO pero no aterriza en " + codigoAlmacen +
                                " (va a " + tramoActual.getDestino() + ")";
                    }
                } else {
                    razon = "Estado=" + pedido.getEstado() + ", tramoActual=" + pedido.getTramoActual() +
                            ", buscando tramo " + i;
                }


                if (!puedeTomarVuelo) {
                    continue;
                }

                String vueloKey = tramo.getOrigen() + "-" + tramo.getDestino() + "-" +
                        tramo.getFecha() + "-" + tramo.getHoraSalida();

                pedidosPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).add(pedido);
                rutasPorVuelo.computeIfAbsent(vueloKey, k -> new ArrayList<>()).addAll(rutasDelPedido);
                tramoPorVuelo.putIfAbsent(vueloKey, tramo);
                horaSalidaPorVuelo.putIfAbsent(vueloKey, horaSalida);
            }
        }

        // Ordenar y tomar los 3 primeros
        List<Map.Entry<String, LocalDateTime>> vuelosOrdenados = new ArrayList<>(horaSalidaPorVuelo.entrySet());
        vuelosOrdenados.sort(Map.Entry.comparingByValue());

        int count = 0;
        for (Map.Entry<String, LocalDateTime> entry : vuelosOrdenados) {
            if (count >= 3) break;

            String vueloKey = entry.getKey();
            RutaTramo tramo = tramoPorVuelo.get(vueloKey);
            List<Pedido> pedidosDelVuelo = pedidosPorVuelo.get(vueloKey);
            List<RutaAsignada> rutasDelVuelo = rutasPorVuelo.get(vueloKey);
            LocalDateTime horaSalida = entry.getValue();

            AeropuertoEntity destino = aeropuertoRepository.findByCodigo(tramo.getDestino()).orElse(null);
            if (destino == null) continue;

            int totalPaquetes = rutasDelVuelo.stream()
                    .mapToInt(RutaAsignada::getCantidad)
                    .sum();

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

            int capacidadVuelo = obtenerCapacidadVuelo(tramo.getOrigen(), tramo.getDestino(), horaSalida);
            vuelo.put("capacity", capacidadVuelo);

            int paquetesMostrados = Math.min(totalPaquetes, capacidadVuelo);
            vuelo.put("packages", paquetesMostrados);
            vuelo.put("status", "scheduled");
            vuelo.put("occupancyPercentage", Math.min(100.0, (totalPaquetes * 100.0) / capacidadVuelo));

            vuelos.add(vuelo);
            count++;
        }
        return vuelos;
    }

    /**
     * ✅ NUEVO: Obtiene los 3 primeros pedidos próximos a salir desde un almacén
     */
    /**
     * ✅ CORREGIDO: Obtiene los 3 primeros pedidos próximos a salir desde un almacén
     */
    /**
     * ✅ DEPURACIÓN: Obtiene los 3 primeros pedidos próximos a salir desde un almacén
     */
    private List<Map<String, Object>> obtenerProximosPedidosDesdeAlmacen(String codigoAlmacen, LocalDateTime ahora) {
        // ✅ FIX: Defensive copy para evitar problemas de referencia
        final String almacenBuscado = String.valueOf(codigoAlmacen);

        List<Map<String, Object>> pedidosProximos = new ArrayList<>();

        List<Pedido> pedidosActivos = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.ASIGNADO,
                        EstadoPedido.EN_TRANSITO,
                        EstadoPedido.EN_ALMACEN_INTERMEDIO)
        );

        if (pedidosActivos.isEmpty()) return pedidosProximos;

        List<Long> pedidoIds = pedidosActivos.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);

        if (todasLasRutas.isEmpty()) return pedidosProximos;

        Map<Long, List<RutaAsignada>> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.groupingBy(RutaAsignada::getPedidoId));

        List<Map.Entry<Pedido, LocalDateTime>> pedidosConHora = new ArrayList<>();

        for (Pedido pedido : pedidosActivos) {
            List<RutaAsignada> rutasDelPedido = rutasPorPedido.get(pedido.getId());
            if (rutasDelPedido == null || rutasDelPedido.isEmpty()) continue;

            RutaAsignada ruta = rutasDelPedido.get(0);

            for (int i = 0; i < ruta.getTramos().size(); i++) {
                RutaTramo tramo = ruta.getTramos().get(i);

                // ✅ Usar almacenBuscado en lugar de codigoAlmacen
                if (!tramo.getOrigen().equals(almacenBuscado)) {
                    continue;
                }

                LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));

                if (horaSalida.isBefore(ahora)) {
                    continue;
                }

                // ✅ LÓGICA CORREGIDA para pedidos ASIGNADOS con múltiples tramos
                boolean puedeTomarVuelo = false;

                if (pedido.getEstado() == EstadoPedido.ASIGNADO) {
                    if (i == 0) {
                        // Primer vuelo - sale del origen
                        puedeTomarVuelo = true;
                    } else {
                        // Vuelo de conexión - verificar que el tramo anterior llegue a este almacén
                        RutaTramo tramoAnterior = ruta.getTramos().get(i - 1);
                        if (tramoAnterior.getDestino().equals(almacenBuscado)) {
                            puedeTomarVuelo = true;
                        }
                    }

                } else if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
                    if (i == pedido.getTramoActual()) {
                        puedeTomarVuelo = true;
                    }

                } else if (pedido.getEstado() == EstadoPedido.EN_TRANSITO) {
                    if (i == pedido.getTramoActual() + 1) {
                        RutaTramo tramoActual = ruta.getTramos().get(pedido.getTramoActual());
                        if (tramoActual.getDestino().equals(almacenBuscado)) {
                            puedeTomarVuelo = true;
                        }
                    }
                }

                if (!puedeTomarVuelo) continue;

                pedidosConHora.add(Map.entry(pedido, horaSalida));
                break; // Solo agregar el pedido una vez
            }
        }

        if (pedidosConHora.isEmpty()) return pedidosProximos;

        // Ordenar por hora de salida y tomar los 3 primeros
        pedidosConHora.sort(Map.Entry.comparingByValue());

        int count = 0;
        for (Map.Entry<Pedido, LocalDateTime> entry : pedidosConHora) {
            if (count >= 3) break;

            Pedido pedido = entry.getKey();
            LocalDateTime horaSalida = entry.getValue();

            List<RutaAsignada> rutasDelPedido = rutasPorPedido.get(pedido.getId());
            RutaAsignada ruta = rutasDelPedido.get(0);

            // Encontrar el tramo correcto que sale de este almacén
            RutaTramo tramoSalida = null;
            for (RutaTramo t : ruta.getTramos()) {
                if (t.getOrigen().equals(almacenBuscado)) {
                    LocalDateTime horaTramo = LocalDateTime.of(t.getFecha(), LocalTime.parse(t.getHoraSalida()));
                    if (horaTramo.equals(horaSalida)) {
                        tramoSalida = t;
                        break;
                    }
                }
            }

            if (tramoSalida == null) continue;

            Map<String, Object> pedidoMap = new HashMap<>();
            pedidoMap.put("orderId", String.valueOf(pedido.getId()));
            pedidoMap.put("destination", pedido.getAeropuertoDestino());
            pedidoMap.put("flightCode", tramoSalida.getOrigen() + "-" + tramoSalida.getDestino());
            pedidoMap.put("departureTime", horaSalida.toString());
            pedidoMap.put("weight", ruta.getCantidad());
            pedidoMap.put("registeredTime", pedido.getFechaPedido() != null ? pedido.getFechaPedido().toString() : "");

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

    /**
     * GET /api/operaciones/pedidos/{id}/asignacion
     * Obtiene información de asignación de un pedido: ruta, tramos y vuelos asignados
     */
    @GetMapping("/pedidos/{id}/asignacion")
    public ResponseEntity<Map<String, Object>> obtenerAsignacionPedido(@PathVariable Long id) {
        Pedido pedido = pedidoRepository.findById(id).orElse(null);
        if (pedido == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> asignacion = new HashMap<>();
        asignacion.put("pedidoId", pedido.getId());
        asignacion.put("estado", pedido.getEstado().toString());
        asignacion.put("tramoActual", pedido.getTramoActual());
        asignacion.put("cantidadPaquetes", pedido.getCantidad());
        asignacion.put("destino", pedido.getAeropuertoDestino());

        // ✅ CAMBIO: Obtener TODAS las rutas asignadas
        List<RutaAsignada> rutas = rutaAsignadaRepository.findByPedidoIdIn(List.of(id));

        if (!rutas.isEmpty()) {
            List<Map<String, Object>> rutasInfo = new ArrayList<>();

            // ✅ Procesar cada ruta
            for (RutaAsignada ruta : rutas) {
                List<Map<String, Object>> tramosInfo = new ArrayList<>();

                for (int i = 0; i < ruta.getTramos().size(); i++) {
                    RutaTramo tramo = ruta.getTramos().get(i);
                    Map<String, Object> tramoMap = new HashMap<>();
                    tramoMap.put("orden", i);
                    tramoMap.put("origen", tramo.getOrigen());
                    tramoMap.put("destino", tramo.getDestino());
                    tramoMap.put("fecha", tramo.getFecha().toString());
                    tramoMap.put("horaSalida", tramo.getHoraSalida());
                    tramoMap.put("horaLlegada", tramo.getHoraLlegada());
                    tramoMap.put("esActual", i == pedido.getTramoActual());
                    tramosInfo.add(tramoMap);
                }

                Map<String, Object> rutaInfo = new HashMap<>();
                rutaInfo.put("rutaId", ruta.getId());
                rutaInfo.put("cantidad", ruta.getCantidad());
                rutaInfo.put("totalTramos", ruta.getTramos().size());
                rutaInfo.put("tramos", tramosInfo);

                rutasInfo.add(rutaInfo);
            }

            asignacion.put("rutas", rutasInfo);
            asignacion.put("totalRutas", rutas.size());
            asignacion.put("totalVuelos", rutas.size()); // ✅ Para el card "Total Vuelos"
        } else {
            asignacion.put("rutas", null);
            asignacion.put("totalRutas", 0);
            asignacion.put("totalVuelos", 0);
        }

        return ResponseEntity.ok(asignacion);
    }

    /**
     * GET /api/operaciones/resumen-estado
     * Obtiene resumen de todos los pedidos por estado para el dashboard
     */
    @GetMapping("/resumen-estado")
    public ResponseEntity<Map<String, Object>> obtenerResumenEstado() {
        Map<String, Object> resumen = new HashMap<>();

        // Contar por cada estado
        Map<EstadoPedido, Long> conteoPorEstado = new HashMap<>();
        for (EstadoPedido estado : EstadoPedido.values()) {
            List<Pedido> pedidos = pedidoRepository.findByEstadoIn(List.of(estado));
            conteoPorEstado.put(estado, (long) pedidos.size());
        }

        // Crear respuesta
        Map<String, Object> estadoCounts = new HashMap<>();
        conteoPorEstado.forEach((estado, count) ->
                estadoCounts.put(estado.toString(), count)
        );

        resumen.put("pedidosPorEstado", estadoCounts);
        resumen.put("activo", operacionesService.isActivo());
        resumen.put("inicioOperaciones", operacionesService.getInicioOperaciones());

        return ResponseEntity.ok(resumen);
    }

    /**
     * GET /api/operaciones/pedidos-sin-asignar
     * Lista todos los pedidos que aún no han sido asignados a una ruta
     */
    @GetMapping("/pedidos-sin-asignar")
    public ResponseEntity<List<Map<String, Object>>> obtenerPedidosSinAsignar() {
        List<Pedido> pedidos = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.NO_ASIGNADO));

        List<Map<String, Object>> response = pedidos.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("aeropuertoDestino", p.getAeropuertoDestino());
                    map.put("cantidad", p.getCantidad());
                    map.put("fecha", String.format("%04d-%02d-%02d", p.getAnho(), p.getMes(), p.getDia()));
                    map.put("hora", String.format("%02d:%02d", p.getHora(), p.getMinuto()));
                    map.put("idCliente", p.getIdCliente());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/pedidos-en-transito
     * Lista todos los pedidos que están en vuelo (EN_TRANSITO)
     */
    @GetMapping("/pedidos-en-transito")
    public ResponseEntity<List<Map<String, Object>>> obtenerPedidosEnTransito() {
        List<Pedido> pedidos = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.EN_TRANSITO));

        List<Map<String, Object>> response = pedidos.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("aeropuertoDestino", p.getAeropuertoDestino());
                    map.put("cantidad", p.getCantidad());
                    map.put("tramoActual", p.getTramoActual());
                    map.put("horaEntrega", p.getHoraEntrega() != null ? p.getHoraEntrega().toString() : null);
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/pedidos-entregados
     * Lista todos los pedidos que ya han sido entregados (ENTREGADO)
     */
    @GetMapping("/pedidos-entregados")
    public ResponseEntity<List<Map<String, Object>>> obtenerPedidosEntregados() {
        List<Pedido> pedidos = pedidoRepository.findByEstadoIn(List.of(EstadoPedido.ENTREGADO));

        List<Map<String, Object>> response = pedidos.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("destino", p.getAeropuertoDestino());
                    map.put("cantidad", p.getCantidad());
                    map.put("horaEntrega", p.getHoraEntrega() != null ? p.getHoraEntrega().toString() : null);
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/operaciones/reporte-cierre
     * Genera un reporte CSV con todos los pedidos y sus rutas asignadas
     * Se usa al detener las operaciones para tener un registro completo
     */
    @GetMapping("/reporte-cierre")
    public ResponseEntity<String> generarReporteCierre() {
        try {
            StringBuilder csv = new StringBuilder();

            // Encabezados del CSV
            csv.append("PEDIDO_ID,CLIENTE_ID,DESTINO,CANTIDAD,ESTADO,TRAMO_ACTUAL,FECHA_PEDIDO,HORA_ENTREGA,");
            csv.append("RUTA_ID,TRAMO_ORDEN,ORIGEN,DESTINO_TRAMO,FECHA_VUELO,HORA_SALIDA,HORA_LLEGADA\n");

            // Obtener todos los pedidos
            List<Pedido> todosPedidos = pedidoRepository.findAllWithLimit();

            for (Pedido pedido : todosPedidos) {
                // Información básica del pedido
                String pedidoInfo = String.format("%d,%s,%s,%d,%s,%s,%s,%s",
                        pedido.getId(),
                        escapeCsv(pedido.getIdCliente()),
                        escapeCsv(pedido.getAeropuertoDestino()),
                        pedido.getCantidad(),
                        pedido.getEstado().toString(),
                        pedido.getTramoActual() != null ? pedido.getTramoActual().toString() : "",
                        formatFechaPedido(pedido),
                        pedido.getHoraEntrega() != null ? pedido.getHoraEntrega().toString() : ""
                );

                // Buscar rutas asignadas a este pedido
                List<RutaAsignada> rutas = rutaAsignadaRepository.findByPedidoIdIn(List.of(pedido.getId()));

                if (rutas.isEmpty()) {
                    // Pedido sin ruta asignada
                    csv.append(pedidoInfo).append(",,,,,,,\n");
                } else {
                    // Pedido con rutas - una línea por cada tramo
                    for (RutaAsignada ruta : rutas) {
                        if (ruta.getTramos().isEmpty()) {
                            csv.append(pedidoInfo).append(",").append(ruta.getId()).append(",,,,,,\n");
                        } else {
                            for (RutaTramo tramo : ruta.getTramos()) {
                                csv.append(pedidoInfo).append(",");
                                csv.append(String.format("%d,%d,%s,%s,%s,%s,%s\n",
                                        ruta.getId(),
                                        tramo.getOrden(),
                                        escapeCsv(tramo.getOrigen()),
                                        escapeCsv(tramo.getDestino()),
                                        tramo.getFecha() != null ? tramo.getFecha().toString() : "",
                                        escapeCsv(tramo.getHoraSalida()),
                                        escapeCsv(tramo.getHoraLlegada())
                                ));
                            }
                        }
                    }
                }
            }

            // Configurar headers para descarga de archivo
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .header("Content-Disposition", "attachment; filename=reporte_cierre_operaciones.csv")
                    .body(csv.toString());

        } catch (Exception e) {
            System.err.println("Error generando reporte de cierre: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error al generar reporte: " + e.getMessage());
        }
    }

    /**
     * Escapa valores CSV para evitar problemas con comas y comillas
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Formatea la fecha del pedido desde los campos separados
     */
    private String formatFechaPedido(Pedido pedido) {
        if (pedido.getAnho() > 0 && pedido.getMes() > 0 && pedido.getDia() > 0) {
            return String.format("%04d-%02d-%02d %02d:%02d",
                    pedido.getAnho(),
                    pedido.getMes(),
                    pedido.getDia(),
                    pedido.getHora(),
                    pedido.getMinuto()
            );
        }
        return "";
    }
}