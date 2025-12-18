package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.algoritmologistica.algorithm.models.Ruta;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import com.morapack.algoritmologistica.algorithm.models.EstadoSistema; // ✅ NUEVO
import com.morapack.algoritmologistica.service.PlanificadorService;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.RutaAsignadaRepository;
import com.morapack.backend.repository.RutaTramoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;

import java.time.LocalTime;
import java.util.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Collectors;

@Service
public class PlanificadorPersistenciaService {

    private final PedidoRepository pedidoRepo;
    private final RutaAsignadaRepository rutaRepo;
    private final PlanificadorService algoritmo;
    private final RutaBatchService rutaBatchService;
    private final JdbcTemplate jdbcTemplate;
    private final TiempoSimuladoService tiempoSimuladoService;
    private final OperacionesDiaDiaService operacionesDiaDiaService;
    private final AlmacenOcupacionService almacenOcupacionService;
    private final EntityManager entityManager;
    private final EstadoSistemaService estadoSistemaService; // ✅ NUEVO
    private final AlmacenOcupacionValidatorAdapter almacenValidator; // ✅ NUEVO

    public PlanificadorPersistenciaService(PedidoRepository pedidoRepo,
                                           RutaAsignadaRepository rutaRepo,
                                           PlanificadorService algoritmo,
                                           RutaBatchService rutaBatchService,
                                           JdbcTemplate jdbcTemplate,
                                           TiempoSimuladoService tiempoSimuladoService,
                                           OperacionesDiaDiaService operacionesDiaDiaService,
                                           AlmacenOcupacionService almacenOcupacionService,
                                           EntityManager entityManager,
                                           EstadoSistemaService estadoSistemaService, // ✅ NUEVO
                                           AlmacenOcupacionValidatorAdapter almacenValidator) { // ✅ NUEVO
        this.pedidoRepo = pedidoRepo;
        this.rutaRepo = rutaRepo;
        this.algoritmo = algoritmo;
        this.rutaBatchService = rutaBatchService;
        this.jdbcTemplate = jdbcTemplate;
        this.tiempoSimuladoService = tiempoSimuladoService;
        this.operacionesDiaDiaService = operacionesDiaDiaService;
        this.almacenOcupacionService = almacenOcupacionService;
        this.entityManager = entityManager;
        this.estadoSistemaService = estadoSistemaService; // ✅ NUEVO
        this.almacenValidator = almacenValidator; // ✅ NUEVO
    }

    @Transactional
    public String ejecutarYGuardar() {
        // Solo ejecutar si las operaciones día a día están activas
        if (!operacionesDiaDiaService.isActivo()) {
            return "Scheduler desactivado - operaciones no iniciadas";
        }

        // 1) Calcular ventana de búsqueda: 15 minutos atrás → ahora (con tiempo simulado)
        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();
        LocalDateTime rangoInicio = ahora.minusMinutes(15);
        LocalDateTime rangoFin = ahora;

        System.out.println("🕐 Rango de planificación: " + rangoInicio + " a " + rangoFin);
        System.out.println("   (Buscando pedidos atrasados y actuales)");

        // 2) Buscar pedidos NO_ASIGNADO en ese rango PRECISO
        List<Pedido> pendientesRango = pedidoRepo.findNoAsignadosEnRangoPreciso(
                rangoInicio.getYear(),
                rangoInicio.getMonthValue(),
                rangoInicio.getDayOfMonth(),
                rangoInicio.getHour(),
                rangoInicio.getMinute(),
                rangoFin.getYear(),
                rangoFin.getMonthValue(),
                rangoFin.getDayOfMonth(),
                rangoFin.getHour(),
                rangoFin.getMinute()
        );

        if (pendientesRango.isEmpty()) {
            return "Sin pedidos NO_ASIGNADO en rango " + rangoInicio.toLocalTime() + " - " + rangoFin.toLocalTime();
        }

        System.out.println("📦 Pedidos a planificar: " + pendientesRango.size());

        // ✅ 3) CREAR ESTADO DEL SISTEMA ACTUAL
        System.out.println("\n🔍 === OBTENIENDO ESTADO DEL SISTEMA ===");

        // Obtener estado de vuelos ocupados (desde el día actual)
        LocalDate fechaActual = ahora.toLocalDate();
        Map<String, Integer> vuelosOcupados = estadoSistemaService.obtenerVuelosOcupados(fechaActual);

        // Crear objeto EstadoSistema
        EstadoSistema estadoActual = new EstadoSistema(vuelosOcupados);
        estadoActual.setAlmacenValidator(almacenValidator);

        // Imprimir estadísticas
        estadoActual.imprimirEstadisticas();
        estadoSistemaService.imprimirEstadoSistema(fechaActual, ahora);

        System.out.println("✅ Estado del sistema cargado\n");

        // ✅ 4) Ejecutar algoritmo CON el estado del sistema
        Solucion solucion = algoritmo.ejecutarPlanificacion(pendientesRango, estadoActual);

        if (solucion == null || solucion.getRutas() == null || solucion.getRutas().isEmpty()) {
            return "Algoritmo no retornó rutas";
        }

        // 5) Agrupar rutas por pedido
        Map<Long, List<Ruta>> rutasPorPedido = new HashMap<>();
        for (Ruta rutaAlg : solucion.getRutas()) {
            if (rutaAlg == null || rutaAlg.getPedido() == null) continue;
            Long pedidoId = rutaAlg.getPedido().getId();
            if (pedidoId == null) continue;

            rutasPorPedido.computeIfAbsent(pedidoId, k -> new ArrayList<>()).add(rutaAlg);
        }

        int asignados = 0;
        List<RutaAsignada> rutasParaGuardar = new ArrayList<>();

        // 6) Procesar todas las rutas de cada pedido
        for (Map.Entry<Long, List<Ruta>> entry : rutasPorPedido.entrySet()) {
            Long pedidoId = entry.getKey();
            List<Ruta> rutasDelPedido = entry.getValue();

            var pedidoOpt = pedidoRepo.findById(pedidoId);
            if (pedidoOpt.isEmpty()) continue;

            Pedido pedido = pedidoOpt.get();

            // Solo procesamos si el pedido sigue NO_ASIGNADO
            if (pedido.getEstado() != EstadoPedido.NO_ASIGNADO) continue;

            LocalDateTime fechaPedido = pedido.getFechaPedido();

            // Procesar TODAS las rutas de este pedido
            for (Ruta rutaAlg : rutasDelPedido) {
                // --- Cabecera (rutas_asignadas) ---
                RutaAsignada cab = new RutaAsignada();
                cab.setPedidoId(pedidoId);
                cab.setCantidad(rutaAlg.getCantidad());

                int orden = 0;
                List<Vuelo> vuelos = rutaAlg.getVuelos();
                if (vuelos != null) {
                    for (Vuelo v : vuelos) {
                        if (v == null) continue;

                        RutaTramo t = new RutaTramo();
                        t.setOrden(orden++);
                        t.setOrigen(sane(v.getAeropuertoOrigen().getCodigo()));
                        t.setDestino(sane(v.getAeropuertoDestino().getCodigo()));

                        // ===== CONVERTIR HORAS LOCALES A HORA DE LIMA (UTC-5) =====
                        int husoOrigen = v.getAeropuertoOrigen().getHusoHorario();
                        int husoDestino = v.getAeropuertoDestino().getHusoHorario();

                        LocalDateTime horaSalidaLocal = v.getHoraSalida();
                        LocalDateTime horaLlegadaLocal = v.getHoraLlegada();

                        LocalDateTime horaSalidaUTC = convertirAUTC(horaSalidaLocal, husoOrigen);
                        LocalDateTime horaLlegadaUTC = convertirAUTC(horaLlegadaLocal, husoDestino);

                        LocalDateTime horaSalidaLima = convertirAHoraLima(horaSalidaUTC);
                        LocalDateTime horaLlegadaLima = convertirAHoraLima(horaLlegadaUTC);
                        // ===========================================================

                        String horaSalidaStr = toHHmm(horaSalidaLima);
                        String horaLlegadaStr = toHHmm(horaLlegadaLima);

                        t.setHoraSalida(horaSalidaStr);
                        t.setHoraLlegada(horaLlegadaStr);
                        t.setFecha(horaSalidaLima.toLocalDate());

                        cab.addTramo(t);
                    }
                }

                rutasParaGuardar.add(cab);
            }

            asignados++;
        }

        if (!rutasParaGuardar.isEmpty()) {
            rutaBatchService.guardarRutasEnLote(rutasParaGuardar);

            // Registrar ocupaciones temporales
            try {
                almacenOcupacionService.registrarOcupacionesDeRutas(rutasParaGuardar);
            } catch (Exception e) {
                System.err.println("❌ Error registrando ocupaciones temporales: " + e.getMessage());
                e.printStackTrace();
            }

            // 7) Actualizar estados de pedidos
            Set<Long> pedidosUnicos = new HashSet<>();
            for (RutaAsignada ruta : rutasParaGuardar) {
                pedidosUnicos.add(ruta.getPedidoId());
            }

            System.out.println("🔍 DEBUG: Rutas totales guardadas: " + rutasParaGuardar.size());
            System.out.println("🔍 DEBUG: Pedidos únicos a actualizar: " + pedidosUnicos);

            actualizarEstadoPedidos(pedidosUnicos);

            System.out.println("🚨 CHECKPOINT: Después de actualizar estados");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("🚨 CHECKPOINT: Antes de return");
        }

        return "✅ Asignados=" + asignados + " de " + pendientesRango.size() +
                " (Rango: " + rangoInicio.toLocalTime() + " - " + rangoFin.toLocalTime() + ")";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void actualizarEstadoPedidos(Set<Long> pedidosUnicos) {
        if (pedidosUnicos.isEmpty()) return;

        System.out.println("🔥 INICIANDO ACTUALIZACIÓN FORZADA DE PEDIDOS: " + pedidosUnicos);

        for (Long pedidoId : pedidosUnicos) {
            Pedido pedido = pedidoRepo.findById(pedidoId).orElse(null);
            if (pedido != null) {
                pedido.setEstado(EstadoPedido.ASIGNADO);
                pedido.setTramoActual(0);
                pedidoRepo.save(pedido);
                System.out.println("📝 Pedido " + pedidoId + " guardado con estado ASIGNADO");
            }
        }

        entityManager.flush();
        System.out.println("✅ Flush completado");

        for (Long pedidoId : pedidosUnicos) {
            Pedido verificado = pedidoRepo.findById(pedidoId).orElse(null);
            if (verificado != null) {
                System.out.println("🔍 VERIFICACIÓN: Pedido " + pedidoId +
                        " - Estado: " + verificado.getEstado() +
                        " - Tramo: " + verificado.getTramoActual());
            }
        }
    }

    // --------- helpers ---------

    private static String sane(String s) {
        return (s == null) ? null : s.trim();
    }

    private static String toHHmm(Object timeObj) {
        if (timeObj == null) return null;

        if (timeObj instanceof String str) {
            String t = str.trim();
            if (t.matches("^\\d{1}:\\d{2}$")) t = "0" + t;
            return t.matches("^\\d{2}:\\d{2}$") ? t : null;
        }
        if (timeObj instanceof LocalTime lt) {
            return String.format("%02d:%02d", lt.getHour(), lt.getMinute());
        }
        try {
            var cls = timeObj.getClass();
            var getHour = cls.getMethod("getHour");
            var getMinute = cls.getMethod("getMinute");
            int h = (int) Objects.requireNonNull(getHour.invoke(timeObj));
            int m = (int) Objects.requireNonNull(getMinute.invoke(timeObj));
            return String.format("%02d:%02d", h, m);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static LocalDateTime convertirAUTC(LocalDateTime fechaLocal, int husoHorario) {
        return fechaLocal.minusHours(husoHorario);
    }

    private static LocalDateTime convertirAHoraLima(LocalDateTime fechaUTC) {
        final int HUSO_LIMA = -5;
        return fechaUTC.plusHours(HUSO_LIMA);
    }
}