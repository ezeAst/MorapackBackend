package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.model.*;
import com.morapack.backend.repository.AeropuertoRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class SimulationEngine {

    private final AeropuertoRepository aeropuertoRepository;
    private final Map<String, double[]> coordinatesCache = new HashMap<>();

    public SimulationEngine(AeropuertoRepository aeropuertoRepository) {
        this.aeropuertoRepository = aeropuertoRepository;
    }

    /**
     * Inicializa el cache de coordenadas desde la BD
     */
    public void initializeCoordinatesCache(List<Aeropuerto> aeropuertos) {
        for (Aeropuerto aeropuerto : aeropuertos) {
            Optional<AeropuertoEntity> entity = aeropuertoRepository.findByCodigo(aeropuerto.getCodigo());
            if (entity.isPresent() && entity.get().getLat() != null && entity.get().getLon() != null) {
                coordinatesCache.put(
                        aeropuerto.getCodigo(),
                        new double[]{entity.get().getLon(), entity.get().getLat()}
                );
            } else {
                coordinatesCache.put(aeropuerto.getCodigo(), new double[]{0.0, 0.0});
                System.out.println("⚠️ Coordenadas no encontradas para: " + aeropuerto.getCodigo());
            }
        }
    }

    /**
     * Genera snapshots iniciales de todos los vuelos
     */
    public List<FlightSnapshot> generateInitialFlightSnapshots(Solucion solucion, LocalDateTime startTime) {
        List<FlightSnapshot> snapshots = new ArrayList<>();
        Set<Vuelo> vuelosUnicos = new HashSet<>();

        for (Ruta ruta : solucion.getRutas()) {
            vuelosUnicos.addAll(ruta.getVuelos());
        }

        int flightIdCounter = 1;
        for (Vuelo vuelo : vuelosUnicos) {
            FlightSnapshot snapshot = createFlightSnapshot(vuelo, flightIdCounter++, startTime);
            snapshots.add(snapshot);
        }

        return snapshots;
    }

    /**
     * Crea un snapshot de vuelo individual
     */
    private FlightSnapshot createFlightSnapshot(Vuelo vuelo, int id, LocalDateTime startTime) {
        FlightSnapshot snapshot = new FlightSnapshot();

        snapshot.setId("F" + id);
        snapshot.setFlightCode(generateFlightCode(vuelo));

        String codigoOrigen = vuelo.getAeropuertoOrigen().getCodigo();
        String codigoDestino = vuelo.getAeropuertoDestino().getCodigo();

        double[] coordOrigen = coordinatesCache.getOrDefault(codigoOrigen, new double[]{0, 0});
        double[] coordDestino = coordinatesCache.getOrDefault(codigoDestino, new double[]{0, 0});

        snapshot.setRoute(new double[][]{coordOrigen, coordDestino});
        snapshot.setOrigin(vuelo.getAeropuertoOrigen().getNombre());
        snapshot.setDestination(vuelo.getAeropuertoDestino().getNombre());

        // ===== CONVERTIR HORAS LOCALES A HORA DE LIMA (UTC-5) =====
        final int HUSO_LIMA = -5;  // Lima UTC-5

        int husoOrigen = vuelo.getAeropuertoOrigen().getHusoHorario();
        int husoDestino = vuelo.getAeropuertoDestino().getHusoHorario();

        // Las horas del vuelo están en hora LOCAL de cada aeropuerto
        LocalDateTime horaSalidaLocal = vuelo.getHoraSalida();
        LocalDateTime horaLlegadaLocal = vuelo.getHoraLlegada();

        // Paso 1: Convertir a UTC
        LocalDateTime horaSalidaUTC = convertirAUTC(horaSalidaLocal, husoOrigen);
        LocalDateTime horaLlegadaUTC = convertirAUTC(horaLlegadaLocal, husoDestino);

        // Paso 2: Convertir de UTC a hora de Lima
        LocalDateTime horaSalidaLima = convertirDeUTC(horaSalidaUTC, HUSO_LIMA);
        LocalDateTime horaLlegadaLima = convertirDeUTC(horaLlegadaUTC, HUSO_LIMA);

        // Ajustar si llega al día siguiente
        if (horaLlegadaLima.isBefore(horaSalidaLima)) {
            horaLlegadaLima = horaLlegadaLima.plusDays(1);
        }

        // Guardar horas en hora de Lima para la simulación
        snapshot.setDepartureTime(horaSalidaLima);
        snapshot.setArrivalTime(horaLlegadaLima);

        // Calcular duración real
        Duration duration = Duration.between(horaSalidaLima, horaLlegadaLima);
        snapshot.setDurationSeconds(duration.getSeconds());
        // ==========================================================

        snapshot.setElapsedSeconds(0);

        snapshot.setPackages(vuelo.getCapacidadActual());
        snapshot.setCapacity(vuelo.getCapacidadMaxima());

        snapshot.setStatus("scheduled");
        snapshot.setProgressPercentage(0.0);
        snapshot.setCurrentLat(coordOrigen[1]);
        snapshot.setCurrentLng(coordOrigen[0]);

        return snapshot;
    }

    /**
     * Convierte una hora local a UTC
     */
    private LocalDateTime convertirAUTC(LocalDateTime fechaLocal, int husoHorario) {
        return fechaLocal.minusHours(husoHorario);
    }
    /**
     * Convierte UTC a una zona horaria específica
     */
    private LocalDateTime convertirDeUTC(LocalDateTime fechaUTC, int husoHorario) {
        return fechaUTC.plusHours(husoHorario);
    }
    /**
     * Actualiza el estado de todos los vuelos según el tiempo simulado
     */
    public List<FlightSnapshot> updateFlightStates(List<FlightSnapshot> allFlights, long simulatedSeconds, LocalDateTime startTime) {
        List<FlightSnapshot> activeFlights = new ArrayList<>();
        LocalDateTime currentSimulatedTime = startTime.plusSeconds(simulatedSeconds);

        for (FlightSnapshot flight : allFlights) {
            boolean hasNotDeparted = currentSimulatedTime.isBefore(flight.getDepartureTime());
            boolean hasArrived = currentSimulatedTime.isAfter(flight.getArrivalTime());

            if (hasNotDeparted) {
                flight.setStatus("scheduled");
                flight.setProgressPercentage(0.0);
                flight.setCurrentLng(flight.getRoute()[0][0]);
                flight.setCurrentLat(flight.getRoute()[0][1]);
            } else if (hasArrived) {
                flight.setStatus("landed");
                flight.setProgressPercentage(100.0);
                flight.setCurrentLng(flight.getRoute()[1][0]);
                flight.setCurrentLat(flight.getRoute()[1][1]);
            } else {
                flight.setStatus("in_flight");

                long elapsedSinceDepart = Duration.between(
                        flight.getDepartureTime(),
                        currentSimulatedTime
                ).getSeconds();

                flight.setElapsedSeconds(elapsedSinceDepart);

                double progress = Math.min(1.0, (double) elapsedSinceDepart / flight.getDurationSeconds());
                flight.setProgressPercentage(progress * 100.0);

                double[] origin = flight.getRoute()[0];
                double[] destination = flight.getRoute()[1];

                flight.setCurrentLng(origin[0] + (destination[0] - origin[0]) * progress);
                flight.setCurrentLat(origin[1] + (destination[1] - origin[1]) * progress);

                activeFlights.add(flight);
            }
        }

        return activeFlights;
    }

    /**
     * Genera snapshots de almacenes
     */
    public List<WarehouseSnapshot> generateWarehouseSnapshots(List<Aeropuerto> aeropuertos, long simulatedSeconds, LocalDateTime startTime) {
        List<WarehouseSnapshot> snapshots = new ArrayList<>();
        LocalDateTime currentSimulatedTime = startTime.plusSeconds(simulatedSeconds);

        int warehouseId = 1;
        for (Aeropuerto aeropuerto : aeropuertos) {
            WarehouseSnapshot snapshot = new WarehouseSnapshot();

            snapshot.setId("W" + warehouseId++);
            snapshot.setName(aeropuerto.getNombre());
            snapshot.setCode(aeropuerto.getCodigo());

            double[] coords = coordinatesCache.getOrDefault(aeropuerto.getCodigo(), new double[]{0, 0});
            snapshot.setLng(coords[0]);
            snapshot.setLat(coords[1]);

            int ocupacionActual = aeropuerto.calcularOcupacionEnMomento(currentSimulatedTime);

            snapshot.setCapacity(aeropuerto.getCapacidad());
            snapshot.setCurrent(ocupacionActual);
            snapshot.setAvailable(aeropuerto.getCapacidad() - ocupacionActual);

            int enTransito = 0;
            int enDestino = 0;

            for (ProductoEnAlmacen producto : aeropuerto.getProductosActuales()) {
                if (producto.esDestinoFinal()) {
                    Duration tiempo = Duration.between(producto.getHoraLlegada(), currentSimulatedTime);
                    if (!tiempo.isNegative() && tiempo.toHours() < 2) {
                        enDestino += producto.getCantidad();
                    }
                } else {
                    LocalDateTime salida = producto.getSiguienteVuelo().getHoraSalida();
                    if (!currentSimulatedTime.isAfter(salida) && !currentSimulatedTime.isBefore(producto.getHoraLlegada())) {
                        enTransito += producto.getCantidad();
                    }
                }
            }

            snapshot.setProductsInTransit(enTransito);
            snapshot.setProductsAtDestination(enDestino);
            snapshot.updateStatus();

            snapshots.add(snapshot);
        }

        return snapshots;
    }

    /**
     * Genera eventos basados en el estado
     */
    public List<SimulationEvent> generateEvents(Simulation simulation, List<FlightSnapshot> allFlights,
                                                List<WarehouseSnapshot> warehouses, long previousSeconds) {
        List<SimulationEvent> newEvents = new ArrayList<>();
        long currentSeconds = simulation.calculateElapsedSimulatedSeconds();

        for (FlightSnapshot flight : allFlights) {
            // Ahora departureTime y arrivalTime ya están en UTC
            long departSeconds = Duration.between(
                    simulation.getStartTime(),
                    flight.getDepartureTime()  // ← Ya es UTC
            ).getSeconds();

            long arrivalSeconds = Duration.between(
                    simulation.getStartTime(),
                    flight.getArrivalTime()  // ← Ya es UTC
            ).getSeconds();

            // ✅ Ya funciona correctamente, no necesita cambios
            if (departSeconds > previousSeconds && departSeconds <= currentSeconds) {
                newEvents.add(new SimulationEvent(
                        "Vuelo " + flight.getFlightCode() + " despegó de " + flight.getOrigin(),
                        "FLIGHT_DEPARTED",
                        departSeconds
                ));
            }

            if (arrivalSeconds > previousSeconds && arrivalSeconds <= currentSeconds) {
                newEvents.add(new SimulationEvent(
                        "Vuelo " + flight.getFlightCode() + " aterrizó en " + flight.getDestination(),
                        "FLIGHT_ARRIVED",
                        arrivalSeconds
                ));
            }
        }


        for (WarehouseSnapshot warehouse : warehouses) {
            if ("critical".equals(warehouse.getStatus()) || "full".equals(warehouse.getStatus())) {
                newEvents.add(new SimulationEvent(
                        "Almacén " + warehouse.getName() + " al " +
                                String.format("%.0f", warehouse.getOccupancyPercentage()) + "% de capacidad",
                        "WAREHOUSE_WARNING",
                        currentSeconds
                ));
            }
        }

        return newEvents;
    }

    /**
     * Calcula métricas de la simulación
     */
    public SimulationMetrics calculateMetrics(Solucion solucion, List<FlightSnapshot> allFlights) {
        SimulationMetrics metrics = new SimulationMetrics();

        int flightsCompleted = (int) allFlights.stream()
                .filter(f -> "landed".equals(f.getStatus()))
                .count();

        metrics.setFlightsCompleted(flightsCompleted);
        metrics.setOrdersProcessed(solucion.getPedidosEntregadosATiempo());
        metrics.setWarehouseViolations(solucion.getViolacionesCapacidadAlmacenes());
        metrics.setFlightViolations(solucion.getViolacionesCapacidadVuelos());

        int totalPackages = allFlights.stream().mapToInt(FlightSnapshot::getPackages).sum();
        int deliveredPackages = allFlights.stream()
                .filter(f -> "landed".equals(f.getStatus()))
                .mapToInt(FlightSnapshot::getPackages)
                .sum();

        metrics.setPackagesDelivered(deliveredPackages);
        metrics.setPackagesPending(totalPackages - deliveredPackages);
        metrics.updateSuccessRate();

        return metrics;
    }

    private String generateFlightCode(Vuelo vuelo) {
        return vuelo.getAeropuertoOrigen().getCodigo().substring(0, 2) + "-" +
                vuelo.getAeropuertoDestino().getCodigo().substring(0, 2) +
                vuelo.getHoraSalida().getDayOfMonth();
    }
}