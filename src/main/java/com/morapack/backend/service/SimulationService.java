package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.dto.request.CreateSimulationRequest;
import com.morapack.backend.dto.response.SimulationResponse;
import com.morapack.backend.dto.response.SimulationStatusResponse;
import com.morapack.backend.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationService {

    private final SimulationEngine simulationEngine;

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
    private final Map<String, Long> lastUpdateSeconds = new ConcurrentHashMap<>();

    public SimulationService(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    /**
     * Crea y ejecuta una nueva simulación
     */
    public SimulationResponse createSimulation(CreateSimulationRequest request) {
        System.out.println("\n=== 🎮 INICIANDO NUEVA SIMULACIÓN ===");
        System.out.println("Tipo: " + request.getType());
        System.out.println("Fecha inicio (Lima): " + request.getStartTime());

        // NO CONVERTIR - usar directamente la hora de Lima
        LocalDateTime startTimeLima = request.getStartTime();

        // 1. Cargar datos
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);
        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos);
        List<Pedido> pedidos = LectorCSV.leerPedidos(pedidosPath);

        // 2. Ejecutar GRASP
        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);

        if (request.getAlphaGrasp() != null && request.getTamanoRcl() != null) {
            planificador.setParametrosGRASP(request.getAlphaGrasp(), request.getTamanoRcl());
        }

        Solucion solucion = planificador.ejecutarPlanificacion();

        System.out.println("✅ Solución GRASP generada - Fitness: " + solucion.getFitness());

        // 3. Crear simulación con startTime en hora de Lima
        Simulation simulation = new Simulation();
        simulation.setType(request.getType());
        simulation.setStartTime(startTimeLima);  // ← Hora de Lima directamente
        simulation.setSolucion(solucion);

        // 4. Inicializar engine
        simulationEngine.initializeCoordinatesCache(aeropuertos);

        // 5. Generar snapshots (los vuelos se convertirán a hora Lima internamente)
        List<FlightSnapshot> flights = simulationEngine.generateInitialFlightSnapshots(
                solucion,
                startTimeLima
        );
        List<WarehouseSnapshot> warehouses = simulationEngine.generateWarehouseSnapshots(
                aeropuertos,
                0,
                startTimeLima
        );

        // 6. Guardar en memoria
        activeSimulations.put(simulation.getId(), simulation);
        simulationFlights.put(simulation.getId(), flights);
        simulationAeropuertos.put(simulation.getId(), aeropuertos);
        lastUpdateSeconds.put(simulation.getId(), 0L);

        System.out.println("✅ Simulación creada con ID: " + simulation.getId());
        System.out.println("🛫 Vuelos totales: " + flights.size());
        System.out.println("🏢 Almacenes: " + warehouses.size());

        // 7. Crear respuesta
        SimulationResponse response = new SimulationResponse();
        response.setSimulationId(simulation.getId());
        response.setStatus("running");
        response.setMessage("Simulación iniciada correctamente");
        response.setFlights(flights);
        response.setWarehouses(warehouses);
        response.setTotalOrders(pedidos.size());
        response.setTotalFlights(flights.size());
        response.setTotalPackages(flights.stream().mapToInt(FlightSnapshot::getPackages).sum());
        response.setEstimatedDurationSeconds(90 * 60); // 90 minutos

        return response;
    }

    /**
     * Obtiene el estado actual de una simulación
     */
    public SimulationStatusResponse getSimulationStatus(String simulationId) {
        Simulation simulation = activeSimulations.get(simulationId);
        if (simulation == null) {
            throw new RuntimeException("Simulación no encontrada: " + simulationId);
        }

        // Verificar si completó
        if (simulation.isCompleted() && simulation.getStatus() != SimulationStatus.COMPLETED) {
            simulation.complete();
        }

        long currentSeconds = simulation.calculateElapsedSimulatedSeconds();
        long previousSeconds = lastUpdateSeconds.get(simulationId);

        // Actualizar estados
        List<FlightSnapshot> allFlights = simulationFlights.get(simulationId);
        List<Aeropuerto> aeropuertos = simulationAeropuertos.get(simulationId);

        List<FlightSnapshot> activeFlights = simulationEngine.updateFlightStates(
                allFlights, currentSeconds, simulation.getStartTime()
        );

        List<WarehouseSnapshot> warehouses = simulationEngine.generateWarehouseSnapshots(
                aeropuertos, currentSeconds, simulation.getStartTime()
        );

        // Generar eventos nuevos
        List<SimulationEvent> newEvents = simulationEngine.generateEvents(
                simulation, allFlights, warehouses, previousSeconds
        );

        for (SimulationEvent event : newEvents) {
            simulation.addEvent(event.getMessage(), event.getType());
        }

        // Actualizar métricas
        SimulationMetrics metrics = simulationEngine.calculateMetrics(simulation.getSolucion(), allFlights);
        simulation.setMetrics(metrics);

        // Guardar último update
        lastUpdateSeconds.put(simulationId, currentSeconds);

        // Calcular tiempo simulado actual
        LocalDateTime currentSimulatedTime = simulation.getStartTime().plusSeconds(currentSeconds);
        int currentDay = currentSimulatedTime.getDayOfMonth();
        int currentHour = currentSimulatedTime.getHour();
        int currentMinute = currentSimulatedTime.getMinute();

        // Crear respuesta
        SimulationStatusResponse response = new SimulationStatusResponse();
        response.setElapsedSeconds(currentSeconds);
        response.setProgressPercentage(simulation.calculateProgress());
        response.setStatus(simulation.getStatus().name());
        response.setCurrentDay(currentDay);
        response.setCurrentHour(currentHour);
        response.setCurrentMinute(currentMinute);
        response.setActiveFlights(activeFlights);
        response.setWarehouses(warehouses);
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