package com.morapack.algoritmologistica.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlanificadorService {

    @Value("${app.data.aeropuertos-path:data/aeropuertos.csv}")
    private String aeropuertosPath;

    @Value("${app.data.vuelos-path:data/vuelos.txt}")
    private String vuelosPath;

    @Value("${app.data.pedidos-path:data/pedidos_m.txt}")
    private String pedidosPath;

    private final PedidoRepository pedidoRepository;

    public PlanificadorService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

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

        List<Pedido> pedidos = pedidoRepository.findAll();

        System.out.println("\n=== DATOS CARGADOS CORRECTAMENTE ===\n");

        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        Solucion solucion = planificador.ejecutarPlanificacion(2025);  // ← Pasar año

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