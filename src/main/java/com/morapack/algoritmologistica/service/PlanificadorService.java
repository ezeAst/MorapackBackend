package com.morapack.algoritmologistica.service;

import com.morapack.algoritmologistica.algorithm.models.*;
import com.morapack.algoritmologistica.algorithm.solver.Planificador;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.util.LectorCSV;
import com.morapack.backend.entity.VueloCancelado;
import com.morapack.backend.repository.AlmacenOcupacionTemporalRepository;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.repository.VueloCanceladoRepository;
import com.morapack.backend.service.TiempoSimuladoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.morapack.backend.repository.AeropuertoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final AeropuertoRepository aeropuertoRepository;
    private final AlmacenOcupacionTemporalRepository ocupacionRepository;
    private final TiempoSimuladoService tiempoSimuladoService;
    private final VueloCanceladoRepository vueloCanceladoRepository; // ✅ NUEVO

    public PlanificadorService(
            PedidoRepository pedidoRepository,
            AeropuertoRepository aeropuertoRepository,
            AlmacenOcupacionTemporalRepository ocupacionRepository,
            TiempoSimuladoService tiempoSimuladoService,
            VueloCanceladoRepository vueloCanceladoRepository) { // ✅ NUEVO
        this.pedidoRepository = pedidoRepository;
        this.aeropuertoRepository = aeropuertoRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.tiempoSimuladoService = tiempoSimuladoService;
        this.vueloCanceladoRepository = vueloCanceladoRepository; // ✅ NUEVO
    }

    /**
     * Ejecuta la planificación completa usando GRASP
     */
    public Solucion ejecutarPlanificacion() {
        System.out.println("=== INICIANDO PLANIFICACIÓN DESDE SERVICE ===\n");

        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertos(aeropuertosPath);
        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // Usar fecha por defecto (enero 2025)
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 1, 0, 0);
        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos, startTime);

        // ✅ FILTRAR VUELOS CANCELADOS
        vuelos = filtrarVuelosCancelados(vuelos);

        List<Pedido> pedidos = pedidoRepository.findPendientes();

        System.out.println("\n=== DATOS CARGADOS CORRECTAMENTE ===\n");

        Planificador planificador = new Planificador(pedidos, vuelos, aeropuertos, sedesPrincipales);
        Solucion solucion = planificador.ejecutarPlanificacion(2025);

        System.out.println("\n=== SOLUCIÓN GENERADA ===");
        System.out.println("Fitness: " + solucion.getFitness());
        System.out.println("Rutas: " + solucion.getNumeroDeRutas());

        return solucion;
    }

    /**
     * ✅ NUEVO - Ejecuta planificación CON EstadoSistema
     * Esta es la versión que se debe usar desde PlanificadorPersistenciaService
     */
    public Solucion ejecutarPlanificacion(List<Pedido> pendientes, EstadoSistema estadoSistema) {
        System.out.println("📊 === EJECUTANDO PLANIFICACIÓN CON ESTADO DEL SISTEMA ===");

        if (estadoSistema != null) {
            System.out.println("✅ EstadoSistema recibido correctamente");
            estadoSistema.imprimirEstadisticas();
        } else {
            System.out.println("⚠️ EstadoSistema es null - creando estado vacío");
            estadoSistema = new EstadoSistema();
        }

        // Obtener tiempo actual simulado
        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();

        // Cargar aeropuertos con ocupación temporal
        List<Aeropuerto> aeropuertos = LectorCSV.leerAeropuertosDesdeDB(
                aeropuertoRepository,
                ocupacionRepository,
                ahora
        );

        List<String> codigosSedes = List.of("SPIM", "EBCI", "UBBB");
        List<Aeropuerto> sedesPrincipales = LectorCSV.identificarSedesPrincipales(aeropuertos, codigosSedes);

        // Calcular fecha desde el primer pedido
        LocalDateTime startTime;
        int year;

        if (pendientes != null && !pendientes.isEmpty()) {
            Pedido primerPedido = pendientes.get(0);
            year = LocalDateTime.now().getYear();
            startTime = primerPedido.getFechaPedido();
            System.out.println("📅 Fecha calculada desde pedidos: " + startTime);
        } else {
            startTime = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            year = startTime.getYear();
        }

        List<Vuelo> vuelos = LectorCSV.leerVuelos(vuelosPath, aeropuertos, startTime);

        // ✅ FILTRAR VUELOS CANCELADOS ANTES DE PLANIFICAR
        int vuelosTotales = vuelos.size();
        vuelos = filtrarVuelosCancelados(vuelos);
        int vuelosFiltrados = vuelosTotales - vuelos.size();

        if (vuelosFiltrados > 0) {
            System.out.println("🚫 Vuelos cancelados filtrados: " + vuelosFiltrados);
        }

        LocalDateTime tiempoLimite = startTime;

        List<Vuelo> vuelosDisponibles = vuelos.stream()
                .filter(v -> {
                    int husoOrigen = v.getAeropuertoOrigen().getHusoHorario();
                    LocalDateTime horaSalidaLocal = v.getHoraSalida();
                    LocalDateTime horaSalidaUTC = horaSalidaLocal.minusHours(husoOrigen);
                    LocalDateTime horaSalidaLima = horaSalidaUTC.plusHours(-5);
                    return horaSalidaLima.isAfter(tiempoLimite);
                })
                .collect(Collectors.toList());

        System.out.println("✈️ Vuelos totales (después de filtrar cancelados): " + vuelos.size());
        System.out.println("✈️ Vuelos disponibles (futuros): " + vuelosDisponibles.size());

        // Crear planificador
        Planificador planificador = new Planificador(pendientes, vuelosDisponibles, aeropuertos, sedesPrincipales);

        // ✅ PASAR EL ESTADO DEL SISTEMA AL PLANIFICADOR
        Solucion solucion = planificador.ejecutarPlanificacion(year, estadoSistema);

        System.out.println("\n=== SOLUCIÓN GENERADA CON ESTADO ===");
        System.out.println("Fitness: " + solucion.getFitness());
        System.out.println("Rutas: " + solucion.getNumeroDeRutas());

        return solucion;
    }

    /**
     * ✅ NUEVO - Filtra vuelos que han sido cancelados
     *
     * Compara cada vuelo con la tabla de vuelos_cancelados
     * y elimina los que coincidan
     */
    private List<Vuelo> filtrarVuelosCancelados(List<Vuelo> vuelos) {
        // Obtener todas las cancelaciones activas
        List<VueloCancelado> cancelaciones = vueloCanceladoRepository.findByActivoTrue();

        if (cancelaciones.isEmpty()) {
            System.out.println("✅ No hay cancelaciones activas - usando todos los vuelos");
            return vuelos;
        }

        System.out.println("\n🚫 === FILTRANDO VUELOS CANCELADOS ===");
        System.out.println("📋 Cancelaciones activas: " + cancelaciones.size());

        // Crear un mapa para búsqueda rápida
        // Key: "ORIGEN-DESTINO-FECHA-HH:mm"
        Map<String, VueloCancelado> mapaCancelaciones = new HashMap<>();
        for (VueloCancelado cancelacion : cancelaciones) {
            String clave = cancelacion.getClave();
            mapaCancelaciones.put(clave, cancelacion);
        }

        // Filtrar vuelos
        List<Vuelo> vuelosFiltrados = new ArrayList<>();
        int vuelosEliminados = 0;

        for (Vuelo vuelo : vuelos) {
            // Extraer datos del vuelo para comparar
            String origen = vuelo.getAeropuertoOrigen().getCodigo();
            String destino = vuelo.getAeropuertoDestino().getCodigo();
            LocalDateTime horaSalida = vuelo.getHoraSalida();
            LocalDate fecha = horaSalida.toLocalDate();
            String horaSalidaStr = String.format("%02d:%02d",
                    horaSalida.getHour(), horaSalida.getMinute());

            // Crear clave del vuelo
            String claveVuelo = String.format("%s-%s-%s-%s",
                    origen, destino, fecha.toString(), horaSalidaStr);

            // Verificar si está cancelado
            if (mapaCancelaciones.containsKey(claveVuelo)) {
                vuelosEliminados++;
                System.out.println("   ❌ Vuelo cancelado: " + claveVuelo);
            } else {
                vuelosFiltrados.add(vuelo);
            }
        }

        System.out.println("✅ Vuelos originales: " + vuelos.size());
        System.out.println("❌ Vuelos eliminados: " + vuelosEliminados);
        System.out.println("✅ Vuelos disponibles: " + vuelosFiltrados.size());
        System.out.println();

        return vuelosFiltrados;
    }

    /**
     * Ejecuta planificación SIN EstadoSistema (compatibilidad con código anterior)
     */
    public Solucion ejecutarPlanificacion(List<Pedido> pendientes) {
        System.out.println("⚠️ Ejecutando planificación SIN EstadoSistema - usando estado vacío");
        return ejecutarPlanificacion(pendientes, new EstadoSistema());
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