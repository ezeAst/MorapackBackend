package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.GraspBatchCallback;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.dto.request.CreateSimulationRequest;
import com.morapack.backend.dto.response.SimulationResponse;
import com.morapack.backend.dto.response.SimulationStatusResponse;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SimulationService {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean planificacionEnProgreso = false;
    private final SimulationEngine simulationEngine;
    private final PedidoRepository pedidoRepository;
    private final Map<String, Integer> simulationNextFlightId = new ConcurrentHashMap<>();

    @Value("${app.data.aeropuertos-path}")
    private String aeropuertosPath;

    @Value("${app.data.vuelos-path}")
    private String vuelosPath;

    @Value("${app.data.pedidos-path}")
    private String pedidosPath;

    // Almacén de simulaciones activas (en memoria)
    private final Map<String, Simulation> activeSimulations = new ConcurrentHashMap<>();
    private final Map<String, List<FlightSnapshot>> simulationFlights = new ConcurrentHashMap<>();
    private final Map<String, List<Aeropuerto>> simulationAeropuertos = new ConcurrentHashMap<>();
    private final Map<String, List<OrderSnapshot>> simulationOrders = new ConcurrentHashMap<>();
    private final Map<String, List<OrderSnapshot>> deliveredOrders = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateSeconds = new ConcurrentHashMap<>();
    private final Map<String, Integer> simulationProcessedOrders = new ConcurrentHashMap<>();
    private final Map<String, Solucion> simulationPartialSolution = new ConcurrentHashMap<>();
    // Junto con los otros Maps
    private final Map<String, Boolean> simulationPlanningStatus = new ConcurrentHashMap<>();
    // ✅ CONSTRUCTOR - Actualizar este también
    public SimulationService(SimulationEngine simulationEngine,
                             PedidoRepository pedidoRepository) {  // ← Agregar parámetro
        this.simulationEngine = simulationEngine;
        this.pedidoRepository = pedidoRepository;  // ← Inicializar
    }


    /**
     * Crea y ejecuta una nueva simulación
     */
    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        System.out.println("\n=== 🎮 INICIANDO NUEVA SIMULACIÓN ===");
        System.out.println("Tipo: " + request.getType());
        System.out.println("Fecha inicio (Lima): " + request.getStartTime());

        LocalDateTime startTimeLima = request.getStartTime();
        LocalDateTime endTime = startTimeLima.plusWeeks(1);

        int anho = startTimeLima.getYear();
        int mesInicio = startTimeLima.getMonthValue();
        int diaInicio = startTimeLima.getDayOfMonth();
        int mesFin = endTime.getMonthValue();
        int diaFin = endTime.getDayOfMonth();

        System.out.println("📅 Semana: " + startTimeLima + " → " + endTime);
        System.out.println("🔍 Buscando pedidos: mes " + mesInicio + " día " + diaInicio +
                " → mes " + mesFin + " día " + diaFin);

        // 1. Cargar aeropuertos
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // 2. Generar vuelos para la semana específica
        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos, startTimeLima);

        // 3. Filtrar pedidos de la semana específica
        List<Pedido> pedidosSemana = pedidoRepository.findPedidosByWeek(
                anho,
                mesInicio,
                diaInicio,
                mesFin,
                diaFin
        );

        System.out.println("📦 Pedidos en esta semana: " + pedidosSemana.size());

        if (pedidosSemana.isEmpty()) {
            throw new RuntimeException("❌ No hay pedidos para la semana especificada");
        }

        // 4. Crear simulación E INICIAR
        Simulation simulation = new Simulation();
        simulation.setType(request.getType());
        simulation.setStartTime(startTimeLima);
        simulation.start(); // ← Iniciar INMEDIATAMENTE
        String simulationId = simulation.getId();
        simulationNextFlightId.put(simulationId, 1);

        // 5. Inicializar estructuras vacías
        activeSimulations.put(simulationId, simulation);
        System.out.println("✅ Simulación guardada en Map con ID: " + simulationId);
        System.out.println("✅ activeSimulations contiene: " + activeSimulations.keySet());
        simulationAeropuertos.put(simulationId, aeropuertos);
        simulationFlights.put(simulationId, new ArrayList<>());
        simulationOrders.put(simulationId, new ArrayList<>());
        deliveredOrders.put(simulationId, new ArrayList<>());
        lastUpdateSeconds.put(simulationId, 0L);
        simulationProcessedOrders.put(simulationId, 0);
        simulationPartialSolution.put(simulationId, new Solucion(new ArrayList<>()));

        // 6. Inicializar engine
        simulationEngine.initializeCoordinatesCache(aeropuertos);

        // 7. Configurar Planificador
        Planificador planificador = new Planificador(pedidosSemana, vuelos, aeropuertos, sedesPrincipales);

        if (request.getAlphaGrasp() != null && request.getTamanoRcl() != null) {
            planificador.setParametrosGRASP(request.getAlphaGrasp(), request.getTamanoRcl());
        }

        // 8. Configurar callback para procesamiento incremental
        GraspBatchCallback callback = (rutasBatch, pedidosProcesados, totalPedidos) -> {
            System.out.println("📦 Batch completado: " + pedidosProcesados + "/" + totalPedidos);
            procesarRutasBatch(simulationId, rutasBatch, startTimeLima);
        };

        planificador.setBatchCallback(callback);

        // 9. Ejecutar planificación en background
        simulationPlanningStatus.put(simulationId, true);
        CompletableFuture.runAsync(() -> {
            try {
                Solucion solucion = planificador.ejecutarPlanificacion(anho);
                simulation.setSolucion(solucion);
                // NO llamar a simulation.start() aquí - ya está corriendo
                simulationPlanningStatus.put(simulationId, false);
                System.out.println("✅ Planificación completa - Fitness: " + solucion.getFitness());
            } catch (Exception e) {
                simulationPlanningStatus.put(simulationId, false);
                System.err.println("❌ Error en planificación: " + e.getMessage());
                e.printStackTrace();
            }
        }, executorService);

        // 10. Crear respuesta inmediata
        SimulationResponse response = new SimulationResponse();
        response.setSimulationId(simulationId);
        response.setStatus("PLANNING_IN_PROGRESS");
        response.setMessage("Planificación iniciada. Los vuelos aparecerán progresivamente.");
        response.setFlights(new ArrayList<>());
        response.setWarehouses(simulationEngine.generateWarehouseSnapshots(aeropuertos, 0, startTimeLima));
        response.setTotalOrders(pedidosSemana.size());
        response.setTotalFlights(0);
        response.setTotalPackages(0);
        response.setEstimatedDurationSeconds(90 * 60);


        return response;
    }

    // Nuevo método para procesar batches incrementales
// Nuevo método para procesar batches incrementales
    private void procesarRutasBatch(String simulationId, List<Ruta> rutasBatch, LocalDateTime startTime) {
        // Contar pedidos únicos en este batch
        Set<String> pedidosUnicos = new HashSet<>();
        for (Ruta ruta : rutasBatch) {
            pedidosUnicos.add(ruta.getPedido().getIdCliente());
        }

        // Actualizar contador
        int currentCount = simulationProcessedOrders.getOrDefault(simulationId, 0);
        simulationProcessedOrders.put(simulationId, currentCount + pedidosUnicos.size());

        int nextId = simulationNextFlightId.getOrDefault(simulationId, 1);


        // Generar snapshots de vuelos del batch
        Solucion solucionParcial = new Solucion(rutasBatch);
        List<FlightSnapshot> nuevosVuelos = simulationEngine.generateInitialFlightSnapshots(
                solucionParcial,
                startTime,
                nextId
        );


        // Agregar solo vuelos que no existen ya
        List<FlightSnapshot> vuelosActuales = simulationFlights.get(simulationId);
        Set<String> vuelosExistentes = new HashSet<>();

        // Crear Set con IDs únicos basados en origen-destino-fecha-hora
        for (FlightSnapshot existing : vuelosActuales) {
            String key = existing.getOrigin() + "-" + existing.getDestination() + "-" +
                    existing.getDepartureTime().toString();
            vuelosExistentes.add(key);
        }

        int agregados = 0;
        for (FlightSnapshot nuevoVuelo : nuevosVuelos) {
            String key = nuevoVuelo.getOrigin() + "-" + nuevoVuelo.getDestination() + "-" +
                    nuevoVuelo.getDepartureTime().toString();

            if (!vuelosExistentes.contains(key)) {
                vuelosActuales.add(nuevoVuelo);
                vuelosExistentes.add(key);
                agregados++;
            }
        }

        Solucion solucionAcumulativa = simulationPartialSolution.get(simulationId);
        solucionAcumulativa.getRutas().addAll(rutasBatch);

    }

    /**
     * Obtiene el estado actual de una simulación
     */
    public SimulationStatusResponse getSimulationStatus(String simulationId) {
        Simulation simulation = activeSimulations.get(simulationId);
        if (simulation == null) {
            throw new RuntimeException("Simulación no encontrada: " + simulationId);
        }

        // Determinar estado actual
        Boolean isPlanning = simulationPlanningStatus.getOrDefault(simulationId, false);
        String currentStatus;
        if (isPlanning) {
            currentStatus = "PLANNING_IN_PROGRESS";
        } else {
            currentStatus = simulation.getStatus().name();
        }

        long currentSeconds = simulation.calculateElapsedSimulatedSeconds();
        long previousSeconds = lastUpdateSeconds.getOrDefault(simulationId, 0L);

        // Usar solución parcial si existe, sino la completa
        Solucion solucionActual = simulation.getSolucion();
        if (solucionActual == null) {
            solucionActual = simulationPartialSolution.get(simulationId);
        }

        // Si está en planificación, devolver respuesta básica
        if (isPlanning || simulation.getSolucion() == null) {
            List<FlightSnapshot> allFlights = simulationFlights.getOrDefault(simulationId, new ArrayList<>());

            // ✅ Calcular progreso basado en tiempo (7 días = 604800 segundos)
            long DURACION_SEMANA_SEGUNDOS = 7 * 24 * 60 * 60;
            double progressPorTiempo = Math.min(100.0, (currentSeconds * 100.0) / DURACION_SEMANA_SEGUNDOS);

            List<FlightSnapshot> activeFlights = simulationEngine.updateFlightStates(
                    allFlights, currentSeconds, simulation.getStartTime()
            );

            List<WarehouseSnapshot> warehouses = simulationEngine.generateWarehouseSnapshots(
                    simulationAeropuertos.get(simulationId), currentSeconds, simulation.getStartTime(), allFlights, solucionActual
            );

            // Generar eventos incluso durante planificación
            List<SimulationEvent> newEvents = simulationEngine.generateEvents(
                    simulation,
                    activeFlights,
                    warehouses,
                    previousSeconds
            );

            for (SimulationEvent event : newEvents) {
                simulation.addEvent(event.getMessage(), event.getType());
            }

            // Actualizar último update
            lastUpdateSeconds.put(simulationId, currentSeconds);

            // Calcular métricas parciales
            SimulationMetrics metrics = new SimulationMetrics();
            int flightsCompleted = (int) allFlights.stream()
                    .filter(f -> "landed".equals(f.getStatus()))
                    .count();

            int totalPackages = allFlights.stream().mapToInt(FlightSnapshot::getPackages).sum();
            int deliveredPackages = allFlights.stream()
                    .filter(f -> "landed".equals(f.getStatus()))
                    .mapToInt(FlightSnapshot::getPackages)
                    .sum();

            metrics.setFlightsCompleted(flightsCompleted);
            metrics.setPackagesDelivered(deliveredPackages);
            metrics.setPackagesPending(totalPackages - deliveredPackages);
            metrics.setOrdersProcessed(simulationProcessedOrders.getOrDefault(simulationId, 0));
            metrics.setWarehouseViolations(0);
            metrics.setFlightViolations(0);
            metrics.updateSuccessRate();

            SimulationStatusResponse response = new SimulationStatusResponse();
            response.setElapsedSeconds(currentSeconds);
            response.setProgressPercentage(progressPorTiempo);
            response.setStatus(currentStatus);
            response.setCurrentDay(simulation.getStartTime().plusSeconds(currentSeconds).getDayOfMonth());
            response.setCurrentHour(simulation.getStartTime().plusSeconds(currentSeconds).getHour());
            response.setCurrentMinute(simulation.getStartTime().plusSeconds(currentSeconds).getMinute());
            response.setActiveFlights(activeFlights);
            response.setWarehouses(warehouses);
            response.setActiveOrders(new ArrayList<>());
            response.setRecentlyDeliveredOrders(new ArrayList<>());
            response.setMetrics(metrics);
            response.setRecentEvents(simulation.getRecentEvents(10));
            response.setCurrentDateTime(simulation.getStartTime().plusSeconds(currentSeconds).toString());

            return response;
        }

        // Verificar si completó
        if (simulation.isCompleted() && simulation.getStatus() != SimulationStatus.COMPLETED) {
            simulation.complete();
        }

        // Actualizar estados
        List<FlightSnapshot> allFlights = simulationFlights.get(simulationId);
        List<Aeropuerto> aeropuertos = simulationAeropuertos.get(simulationId);

        List<FlightSnapshot> activeFlights = simulationEngine.updateFlightStates(
                allFlights, currentSeconds, simulation.getStartTime()
        );

        List<WarehouseSnapshot> warehouses = simulationEngine.generateWarehouseSnapshots(
                aeropuertos, currentSeconds, simulation.getStartTime(), allFlights, solucionActual
        );

        // Generar eventos nuevos
        List<SimulationEvent> newEvents = simulationEngine.generateEvents(
                simulation, allFlights, warehouses, previousSeconds
        );

        for (SimulationEvent event : newEvents) {
            simulation.addEvent(event.getMessage(), event.getType());
        }

        // Actualizar métricas
        SimulationMetrics metrics = simulationEngine.calculateMetrics(solucionActual, allFlights);
        simulation.setMetrics(metrics);

        // Guardar último update
        lastUpdateSeconds.put(simulationId, currentSeconds);

        // Calcular tiempo simulado actual
        LocalDateTime currentSimulatedTime = simulation.getStartTime().plusSeconds(currentSeconds);
        int currentDay = currentSimulatedTime.getDayOfMonth();
        int currentHour = currentSimulatedTime.getHour();
        int currentMinute = currentSimulatedTime.getMinute();

        // Actualizar estado de los pedidos
        List<OrderSnapshot> orders = simulationOrders.get(simulationId);
        List<OrderSnapshot> delivered = deliveredOrders.get(simulationId);

        updateOrderStates(simulationId, simulation, orders, delivered, solucionActual, currentSimulatedTime);

        // Crear respuesta
        SimulationStatusResponse response = new SimulationStatusResponse();
        response.setElapsedSeconds(currentSeconds);
        response.setProgressPercentage(simulation.calculateProgress());
        response.setStatus(currentStatus);
        response.setCurrentDay(currentDay);
        response.setCurrentHour(currentHour);
        response.setCurrentMinute(currentMinute);
        response.setActiveFlights(activeFlights);
        response.setWarehouses(warehouses);
        response.setActiveOrders(orders);
        response.setRecentlyDeliveredOrders(delivered.subList(0, Math.min(10, delivered.size())));
        response.setMetrics(metrics);
        response.setRecentEvents(simulation.getRecentEvents(10));
        response.setCurrentDateTime(currentSimulatedTime.toString());

        return response;
    }

    /**
     * Controla una simulación (pause/resume/stop)
     */
    public Map<String, String> controlSimulation(String simulationId, String action) {
        Simulation simulation = activeSimulations.get(simulationId);
        if (simulation == null) {
            throw new RuntimeException("Simulación no encontrada: " + simulationId);
        }

        switch (action.toLowerCase()) {
            case "pause":
                simulation.pause();
                System.out.println("⏸️ Simulación pausada: " + simulationId);
                break;
            case "resume":
                simulation.resume();
                System.out.println("▶️ Simulación reanudada: " + simulationId);
                break;
            case "stop":
                simulation.stop();
                System.out.println("⏹️ Simulación detenida: " + simulationId);
                break;
            default:
                throw new RuntimeException("Acción no válida: " + action);
        }

        return Map.of(
                "simulationId", simulationId,
                "action", action,
                "newStatus", simulation.getStatus().name()
        );
    }

    /**
     * Obtiene todas las simulaciones activas
     */
    /**
     * Actualiza el estado de los pedidos basado en la solución y el tiempo actual
     */
    private void updateOrderStates(
            String simulationId,
            Simulation simulation,
            List<OrderSnapshot> orders,
            List<OrderSnapshot> delivered,
            Solucion solucion,
            LocalDateTime currentTime
    ) {
        // Iterar sobre cada pedido activo
        Iterator<OrderSnapshot> iterator = orders.iterator();
        
        while (iterator.hasNext()) {
            OrderSnapshot order = iterator.next();
            
            // Verificar si el pedido está programado para este momento
            LocalDateTime orderTime = currentTime.withDayOfMonth(order.getDay())
                    .withHour(order.getHour())
                    .withMinute(order.getMinute());
            
            if (currentTime.isBefore(orderTime)) {
                // El pedido aún no ha llegado
                order.setStatus("pending");
                order.setProgressPercentage(0.0);
                continue;
            }

            // Verificar si el pedido ha sido asignado a algún vuelo en la solución
            boolean isAssigned = solucion.getRutas().stream()
                    .anyMatch(ruta -> ruta.getPedido() != null && 
                             ruta.getPedido().getIdCliente().equals(order.getClientId()));

            if (!isAssigned) {
                // El pedido no ha sido asignado a ningún vuelo
                order.setStatus("pending");
                order.setProgressPercentage(25.0);
                continue;
            }

            // Si el pedido está asignado, está en tránsito
            order.setStatus("in_transit");
            order.setProgressPercentage(75.0);

            // Evento: llegada al almacén (primera vez que pasa a in_transit)
            if (order.getArrivalTime() == null || order.getArrivalTime().isEmpty()) {
                order.setArrivalTime(currentTime.toString());
                simulation.addEvent("El pedido " + order.getOrderId() + " acaba de llegar", "ORDER_RECEIVED");
            }

            // Verificar si el pedido ha sido entregado
            boolean isDelivered = solucion.getRutas().stream()
                    .filter(ruta -> ruta.getPedido() != null && 
                            ruta.getPedido().getIdCliente().equals(order.getClientId()))
                    .allMatch(ruta -> ruta.getPedido().getCantidadCumplida() >= ruta.getPedido().getCantidad());

            if (isDelivered) {
                // El pedido ha sido entregado completamente
                order.setStatus("delivered");
                order.setProgressPercentage(100.0);
                order.setDeliveryTime(currentTime.toString());

                // Evento: entrega al cliente (primera vez)
                simulation.addEvent("El pedido " + order.getOrderId() + " ya se entregó", "ORDER_DELIVERED");
                
                // Mover el pedido a la lista de entregados
                iterator.remove();
                delivered.add(order);
            }
        }
    }

    public List<Map<String, Object>> getAllSimulations() {
        List<Map<String, Object>> simulations = new ArrayList<>();

        for (Simulation sim : activeSimulations.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", sim.getId());
            info.put("type", sim.getType());
            info.put("status", sim.getStatus().name());
            info.put("progress", sim.calculateProgress());
            info.put("createdAt", sim.getCreatedAt());
            simulations.add(info);
        }

        return simulations;
    }


}