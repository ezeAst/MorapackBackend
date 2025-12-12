package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.model.*;
import com.morapack.backend.repository.AeropuertoRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationEngine {

    private final AeropuertoRepository aeropuertoRepository;
    private final Map<String, double[]> coordinatesCache = new HashMap<>();
    private Solucion currentSolucion; // Guardar referencia a la solución actual


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
        return generateInitialFlightSnapshots(solucion, startTime, 1);
    }

    public List<FlightSnapshot> generateInitialFlightSnapshots(Solucion solucion, LocalDateTime startTime, int startId) {
        this.currentSolucion = solucion; // Guardar referencia a la solución
        List<FlightSnapshot> snapshots = new ArrayList<>();
        Set<Vuelo> vuelosUnicos = new HashSet<>();

        for (Ruta ruta : solucion.getRutas()) {
            vuelosUnicos.addAll(ruta.getVuelos());
        }

        // Códigos de los 3 almacenes principales
        final Set<String> ALMACENES_PRINCIPALES = Set.of("SPIM", "EBCI", "UBBB"); // Lima, Brussels, Baku
        final long HORAS_RESTRICCION = 12;

        int flightIdCounter = startId;
        for (Vuelo vuelo : vuelosUnicos) {
            FlightSnapshot snapshot = createFlightSnapshot(vuelo, flightIdCounter++, startTime);

            // Solo agregar si sale después del startTime
            if (!snapshot.getDepartureTime().isBefore(startTime)) {
                // Calcular cuántas horas han pasado desde el inicio de la simulación
                Duration tiempoDesdeInicio = Duration.between(startTime, snapshot.getDepartureTime());
                long horasDesdeInicio = tiempoDesdeInicio.toHours();

                // Si estamos en las primeras 12 horas
                if (horasDesdeInicio < HORAS_RESTRICCION) {
                    String codigoOrigen = vuelo.getAeropuertoOrigen().getCodigo();

                    // Solo agregar si sale de un almacén principal
                    if (ALMACENES_PRINCIPALES.contains(codigoOrigen)) {
                        snapshots.add(snapshot);
                    }
                    // Si NO sale de almacén principal, simplemente no lo agregamos (se ignora)
                } else {
                    // Después de 12 horas, agregar todos los vuelos
                    snapshots.add(snapshot);
                }
            }
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

        // Agregar los pedidos que lleva este vuelo
        List<String> orderIds = getOrdersInFlight(vuelo);
        snapshot.setOrderIds(orderIds);

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

                // Interpolar en proyección Mercator
                double[] originMercator = latLngToMercator(origin[0], origin[1]);
                double[] destMercator = latLngToMercator(destination[0], destination[1]);

                double mercatorX = originMercator[0] + (destMercator[0] - originMercator[0]) * progress;
                double mercatorY = originMercator[1] + (destMercator[1] - originMercator[1]) * progress; // ← FIX AQUÍ

                double[] currentLatLng = mercatorToLatLng(mercatorX, mercatorY);

                flight.setCurrentLng(currentLatLng[0]);
                flight.setCurrentLat(currentLatLng[1]);
            }
        }

        return allFlights;
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
            int ocupacionMostrada = Math.min(ocupacionActual, aeropuerto.getCapacidad());
            int disponibleMostrado = Math.max(0, aeropuerto.getCapacidad() - ocupacionActual);

            snapshot.setCapacity(aeropuerto.getCapacidad());
            snapshot.setCurrent(ocupacionMostrada); // ← Mostrar máximo la capacidad
            snapshot.setAvailable(disponibleMostrado);

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

    /**
     * Obtiene los IDs de los pedidos que están en un vuelo específico
     */
    private List<String> getOrdersInFlight(Vuelo vuelo) {
        List<String> orderIds = new ArrayList<>();

        if (vuelo == null || currentSolucion == null) {
            return orderIds;
        }

        // Recorrer todas las rutas de la solución
        for (Ruta ruta : currentSolucion.getRutas()) {
            if (ruta.getPedido() == null) continue;

            // Verificar si este vuelo está en la ruta
            for (Vuelo vueloEnRuta : ruta.getVuelos()) {
                // Comparar vuelos por origen, destino y hora de salida
                boolean mismoOrigen = vueloEnRuta.getAeropuertoOrigen().getCodigo()
                        .equals(vuelo.getAeropuertoOrigen().getCodigo());
                boolean mismoDestino = vueloEnRuta.getAeropuertoDestino().getCodigo()
                        .equals(vuelo.getAeropuertoDestino().getCodigo());
                boolean mismaHora = vueloEnRuta.getHoraSalida().equals(vuelo.getHoraSalida());

                if (mismoOrigen && mismoDestino && mismaHora) {
                    // Este pedido está en este vuelo
                    orderIds.add(ruta.getPedido().getIdCliente());
                    break; // Ya encontramos este pedido en este vuelo, pasar al siguiente
                }
            }
        }

        return orderIds;
    }

    /**
     * Convierte coordenadas lat/lng a proyección Web Mercator (EPSG:3857)
     */
    private double[] latLngToMercator(double lng, double lat) {
        double x = lng;
        double y = Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)) * 180 / Math.PI;
        return new double[]{x, y};
    }

    /**
     * Convierte coordenadas Web Mercator a lat/lng
     */
    private double[] mercatorToLatLng(double x, double y) {
        double lng = x;
        double lat = Math.toDegrees(2 * Math.atan(Math.exp(Math.toRadians(y))) - Math.PI / 2);
        return new double[]{lng, lat};
    }

    public List<WarehouseSnapshot> generateWarehouseSnapshots(List<Aeropuerto> aeropuertos, long simulatedSeconds, LocalDateTime startTime, List<FlightSnapshot> allFlights, Solucion solucion) {
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

            // ✅ CALCULAR OCUPACIÓN BASÁNDOSE EN VUELOS EN TIEMPO REAL
            int ocupacionActual = calcularOcupacionPorVuelos(aeropuerto, allFlights, currentSimulatedTime, solucion);

            snapshot.setCapacity(aeropuerto.getCapacidad());
            snapshot.setCurrent(Math.min(ocupacionActual, aeropuerto.getCapacidad())); // Limitar a capacidad
            snapshot.setAvailable(Math.max(0, aeropuerto.getCapacidad() - ocupacionActual));

            snapshot.setProductsInTransit(0);
            snapshot.setProductsAtDestination(0);

            // ✨ Agregar vuelos salientes
            List<Map<String, Object>> outgoingFlights = getOutgoingFlights(
                    aeropuerto.getCodigo(),
                    aeropuerto.getNombre(),
                    allFlights,
                    currentSimulatedTime
            );
            snapshot.setOutgoingFlights(outgoingFlights);

            // ✨ Agregar pedidos próximos a salir
            List<Map<String, Object>> outgoingOrders = getOutgoingOrders(
                    aeropuerto.getCodigo(),
                    aeropuerto.getNombre(),
                    allFlights,
                    solucion,
                    currentSimulatedTime
            );
            snapshot.setOutgoingOrders(outgoingOrders);

            snapshot.updateStatus();

            snapshots.add(snapshot);
        }

        return snapshots;
    }

    /**
     * Obtiene los vuelos que salen de un aeropuerto específico
     * Solo muestra vuelos programados (scheduled) que aún no han despegado
     */
    private List<Map<String, Object>> getOutgoingFlights(
            String airportCode,
            String airportName,
            List<FlightSnapshot> allFlights,
            LocalDateTime currentTime
    ) {
        List<Map<String, Object>> outgoingFlights = new ArrayList<>();

        for (FlightSnapshot flight : allFlights) {
            // Verificar si el vuelo sale de este aeropuerto
            boolean matchesOrigin = flight.getOrigin().equals(airportName) ||
                    flight.getOrigin().contains(airportCode);

            if (matchesOrigin) {
                // Solo incluir vuelos programados que aún no han despegado
                String status = flight.getStatus();
                boolean isScheduled = status.equals("scheduled");
                boolean hasNotDeparted = flight.getDepartureTime().isAfter(currentTime);

                if (isScheduled && hasNotDeparted) {
                    Map<String, Object> flightInfo = new HashMap<>();
                    flightInfo.put("id", flight.getId());
                    flightInfo.put("flightCode", flight.getFlightCode());
                    flightInfo.put("destination", flight.getDestination());
                    flightInfo.put("departureTime", flight.getDepartureTime().toString());
                    flightInfo.put("arrivalTime", flight.getArrivalTime().toString());
                    flightInfo.put("status", status);
                    flightInfo.put("packages", flight.getPackages());
                    flightInfo.put("capacity", flight.getCapacity());
                    flightInfo.put("occupancyPercentage",
                            flight.getCapacity() > 0 ? (flight.getPackages() * 100.0 / flight.getCapacity()) : 0.0
                    );

                    outgoingFlights.add(flightInfo);
                }
            }
        }

        // Ordenar por hora de salida (más próximos primero)
        outgoingFlights.sort((f1, f2) -> {
            String time1 = (String) f1.get("departureTime");
            String time2 = (String) f2.get("departureTime");
            return time1.compareTo(time2);
        });

        // Limitar a 20 para tener margen (frontend mostrará 3)
        if (outgoingFlights.size() > 20) {
            outgoingFlights = outgoingFlights.subList(0, 20);
        }

        return outgoingFlights;
    }

    /**
     * Obtiene los pedidos que están esperando salir de un aeropuerto específico
     * Muestra pedidos cuyos vuelos están programados pero no han despegado
     */
    private List<Map<String, Object>> getOutgoingOrders(
            String airportCode,
            String airportName,
            List<FlightSnapshot> allFlights,
            Solucion solucion,
            LocalDateTime currentTime
    ) {
        List<Map<String, Object>> outgoingOrders = new ArrayList<>();

        if (solucion == null) {
            return outgoingOrders;
        }

        // Recorrer todas las rutas de la solución
        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.getVuelos().isEmpty() || ruta.getPedido() == null) continue;

            Vuelo primerVuelo = ruta.getVuelos().get(0);
            String origenVuelo = primerVuelo.getAeropuertoOrigen().getNombre();
            String codigoOrigen = primerVuelo.getAeropuertoOrigen().getCodigo();

            // Verificar si el primer vuelo sale de este aeropuerto
            boolean matchesOrigin = origenVuelo.equals(airportName) || codigoOrigen.equals(airportCode);

            if (matchesOrigin) {
                Pedido pedido = ruta.getPedido();
                LocalDateTime departureTime = primerVuelo.getHoraSalida();

                // CLAVE: Solo mostrar si el vuelo aún no ha despegado (está en el futuro)
                // No importa cuándo se registró el pedido, solo importa si el vuelo está por salir
                if (currentTime.isBefore(departureTime)) {
                    Map<String, Object> orderInfo = new HashMap<>();
                    orderInfo.put("orderId", pedido.getIdCliente());
                    orderInfo.put("destination", ruta.getDestinoFinal().getNombre());
                    orderInfo.put("flightCode", generateFlightCode(primerVuelo));
                    orderInfo.put("departureTime", departureTime.toString());
                    orderInfo.put("weight", pedido.getCantidad());

                    outgoingOrders.add(orderInfo);
                }
            }
        }

        // Ordenar por hora de salida del vuelo (más próximos primero)
        outgoingOrders.sort((o1, o2) -> {
            String time1 = (String) o1.get("departureTime");
            String time2 = (String) o2.get("departureTime");
            return time1.compareTo(time2);
        });

        // Limitar a 20 para tener margen (frontend mostrará 3)
        if (outgoingOrders.size() > 20) {
            outgoingOrders = outgoingOrders.subList(0, 20);
        }

        return outgoingOrders;
    }

    /**
     * Calcula ocupación del almacén basándose en vuelos que han aterizado
     */
    private int calcularOcupacionPorVuelos(Aeropuerto aeropuerto, List<FlightSnapshot> allFlights, LocalDateTime currentTime, Solucion solucion) {
        int ocupacion = 0;

        // 1. SUMAR: Paquetes que llegaron (landed) hace menos de 2 horas
        for (FlightSnapshot flight : allFlights) {
            if (!flight.getDestination().equals(aeropuerto.getNombre())) {
                continue;
            }

            if ("landed".equals(flight.getStatus())) {
                Duration tiempoDesdeAterrizaje = Duration.between(flight.getArrivalTime(), currentTime);

                if (!tiempoDesdeAterrizaje.isNegative() && tiempoDesdeAterrizaje.toHours() < 2) {
                    ocupacion += flight.getPackages();
                }
            }
        }

        // 2. RESTAR: Paquetes que ya salieron en vuelos desde este aeropuerto
        for (FlightSnapshot flight : allFlights) {
            if (!flight.getOrigin().equals(aeropuerto.getNombre())) {
                continue;
            }

            // Si el vuelo ya despegó (in_flight o landed), sus paquetes ya no están en el almacén
            if ("in_flight".equals(flight.getStatus()) || "landed".equals(flight.getStatus())) {
                Duration tiempoDesdeDespegue = Duration.between(flight.getDepartureTime(), currentTime);

                // Solo restar si despegó recientemente (hace menos de 2 horas)
                // Esto evita doble conteo con vuelos muy antiguos
                if (!tiempoDesdeDespegue.isNegative() && tiempoDesdeDespegue.toHours() < 2) {
                    ocupacion -= flight.getPackages();
                }
            }
        }

        return Math.max(0, ocupacion); // No permitir negativos
    }

    private LocalDateTime convertirAHoraLima(LocalDateTime horaLocal, int husoLocal) {
        final int HUSO_LIMA = -5;
        LocalDateTime utc = horaLocal.minusHours(husoLocal);
        return utc.plusHours(HUSO_LIMA);
    }
}