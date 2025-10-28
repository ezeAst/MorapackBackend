package com.morapack.algoritmologistica.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        // 1. Leer aeropuertos
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);

        // 2. Identificar sedes principales (Lima, Bruselas, Baku)
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // 3. Leer vuelos
        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos);

        // 4. Leer pedidos
        //List<Pedido> pedidos = LectorCSV.leerPedidos(pedidosPath);
        //4. Leer Pedidos de la BD
        List<Pedido> pedidos = pedidoRepository.findAll();


        System.out.println("\n=== DATOS CARGADOS CORRECTAMENTE ===\n");

        // 5. Crear planificador y ejecutar
        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        Solucion solucion = planificador.ejecutarPlanificacion();

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
        List<Vuelo> vuelos = LectorCSV.leerVuelos(rutaVuelos, aeropuertos);
        List<Pedido> pedidos = LectorCSV.leerPedidos(rutaPedidos);

        // Crear y configurar planificador
        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        planificador.setParametrosGRASP(alphaGRASP, tamanoRCL);

        return planificador.ejecutarPlanificacion();
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
        return LectorCSV.leerVuelos(vuelosPath, aeropuertos);
    }

    /**
     * Obtiene todos los pedidos
     */
    public List<Pedido> obtenerPedidos() {
        return LectorCSV.leerPedidos(pedidosPath);
    }
}