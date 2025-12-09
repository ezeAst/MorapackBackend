package com.morapack.algoritmologistica.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.morapack.backend.repository.AeropuertoRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlanificadorService {

    @Value("${app.data.aeropuertos-path:data/aeropuertos.csv}")
    private String aeropuertosPath;

    @Value("${app.data.vuelos-path:data/vuelos.txt}")
    private String vuelosPath;

    @Value("${app.data.pedidos-path:data/pedidos_m.txt}")
    private String pedidosPath;

    private final PedidoRepository pedidoRepository;
    private final AeropuertoRepository aeropuertoRepository;  // ← AGREGAR

    public PlanificadorService(
            PedidoRepository pedidoRepository,
            AeropuertoRepository aeropuertoRepository  // ← AGREGAR
    ) {
        this.pedidoRepository = pedidoRepository;
        this.aeropuertoRepository = aeropuertoRepository;  // ← AGREGAR
    }

    /**
     * Ejecuta la planificación completa usando GRASP
     */
    /**
     * Ejecuta la planificación completa usando GRASP
     */
    public Solucion ejecutarPlanificacion() {
        System.out.println("=== INICIANDO PLANIFICACIÓN DESDE SERVICE ===\n");

        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // ✅ USAR FECHA POR DEFECTO (enero 2025)
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 1, 0, 0);
        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos, startTime);

        List<Pedido> pedidos = pedidoRepository.findPendientes();

        System.out.println("\n=== DATOS CARGADOS CORRECTAMENTE ===\n");

        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        Solucion solucion = planificador.ejecutarPlanificacion(2025);

        System.out.println("\n=== SOLUCIÓN GENERADA ===");
        System.out.println("Fitness: " + solucion.getFitness());
        System.out.println("Rutas: " + solucion.getNumeroDeRutas());

        return solucion;
    }

    public Solucion ejecutarPlanificacion(List<Pedido> pendientes) {
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertosDesdeDB(
                aeropuertoRepository,
                pedidoRepository
        );
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // ✅ CALCULAR FECHA DESDE EL PRIMER PEDIDO
        LocalDateTime startTime;
        int year;

        if (pendientes != null && !pendientes.isEmpty()) {
            // Usar la fecha del primer pedido
            Pedido primerPedido = pendientes.get(0);
            year = LocalDateTime.now().getYear(); // Año actual
            startTime = primerPedido.getFechaPedido();

            // Ajustar al inicio del día
            startTime = startTime.withHour(0).withMinute(0).withSecond(0);

            System.out.println("📅 Fecha calculada desde pedidos: " + startTime);
        } else {
            // Fallback: usar fecha actual
            startTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            year = startTime.getYear();
        }

        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos, startTime);


        LocalDateTime tiempoLimite = startTime; // Ya está en hora de Lima (UTC-5)

        List<Vuelo> vuelosDisponibles = vuelos.stream()
                .filter(v -> {
                    // Convertir hora de salida (local del aeropuerto) a hora de Lima
                    int husoOrigen = v.getAeropuertoOrigen().getHusoHorario();
                    LocalDateTime horaSalidaLocal = v.getHoraSalida();

                    // Paso 1: Convertir a UTC
                    LocalDateTime horaSalidaUTC = horaSalidaLocal.minusHours(husoOrigen);

                    // Paso 2: Convertir de UTC a Lima (UTC-5)
                    LocalDateTime horaSalidaLima = horaSalidaUTC.plusHours(-5);

                    // Comparar en la misma zona horaria
                    return horaSalidaLima.isAfter(tiempoLimite);
                })
                .collect(Collectors.toList());

        System.out.println("✈️ Vuelos totales: " + vuelos.size());
        System.out.println("✈️ Vuelos disponibles (futuros): " + vuelosDisponibles.size());

        List<Pedido> pedidos = pendientes;

        Planificador planificador = new Planificador(pedidos, vuelosDisponibles, aeropuertos, sedesPrincipales);
        Solucion solucion = planificador.ejecutarPlanificacion(year);

        System.out.println("\n=== SOLUCIÓN GENERADA ===");
        System.out.println("Fitness: " + solucion.getFitness());
        System.out.println("Rutas: " + solucion.getNumeroDeRutas());

        return solucion;
    }

    /**
     * Ejecuta planificación con parámetros personalizados
     */
    public Solucion ejecutarPlanificacionConParametros(
            String rutaVuelos,
            String rutaPedidos,
            List<String> codigosSedes,
            double alphaGRASP,
            int tamanoRCL) {

        // Cargar datos
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);
        List<Vuelo> vuelos = new ArrayList<>();
        List<Pedido> pedidos = LectorCSV.leerPedidos(rutaPedidos);

        // Crear y configurar planificador
        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        planificador.setParametrosGRASP(alphaGRASP, tamanoRCL);

        return planificador.ejecutarPlanificacion(2025);
    }

    /**
     * Obtiene todos los aeropuertos disponibles
     */
    public List<Aeropuerto> obtenerAeropuertos() {
        return LectorCSV.leerAeropuertos(aeropuertosPath);
    }

    /**
     * Obtiene todos los vuelos
     */
    public List<Vuelo> obtenerVuelos() {
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        return new ArrayList<>();
    }

    /**
     * Obtiene todos los pedidos
     */
    public List<Pedido> obtenerPedidos() {
        return LectorCSV.leerPedidos(pedidosPath);
    }
}