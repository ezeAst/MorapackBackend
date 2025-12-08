package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.algoritmologistica.algorithm.models.Ruta;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import com.morapack.algoritmologistica.service.PlanificadorService;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.RutaAsignadaRepository;
import com.morapack.backend.repository.RutaTramoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Collectors;

/**
 * Toma SOLO pedidos NO_ASIGNADO, ejecuta el algoritmo y persiste:
 * - cabecera (rutas_asignadas)
 * - tramos   (rutas_tramo)
 * Luego marca el pedido como ASIGNADO.
 *
 * No reemplaza rutas existentes porque el job periódico solo trabaja con NO_ASIGNADO.
 */
@Service
public class PlanificadorPersistenciaService {

    private final PedidoRepository pedidoRepo;
    private final RutaAsignadaRepository rutaRepo;
    private final PlanificadorService algoritmo;
    private final RutaBatchService rutaBatchService;
    private final JdbcTemplate jdbcTemplate;
    private final TiempoSimuladoService tiempoSimuladoService;
    private final OperacionesDiaDiaService operacionesDiaDiaService;

    public PlanificadorPersistenciaService(PedidoRepository pedidoRepo,
                                           RutaAsignadaRepository rutaRepo,
                                           PlanificadorService algoritmo,
                                           RutaBatchService rutaBatchService,
                                           JdbcTemplate jdbcTemplate,
                                           TiempoSimuladoService tiempoSimuladoService,
                                           OperacionesDiaDiaService operacionesDiaDiaService) {
        this.pedidoRepo = pedidoRepo;
        this.rutaRepo = rutaRepo;
        this.algoritmo = algoritmo;
        this.rutaBatchService = rutaBatchService;
        this.jdbcTemplate = jdbcTemplate;
        this.tiempoSimuladoService = tiempoSimuladoService;
        this.operacionesDiaDiaService = operacionesDiaDiaService;
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

        // 2) Buscar pedidos NO_ASIGNADO en ese rango de 15 minutos
        List<Pedido> pendientesRango = pedidoRepo.findNoAsignadosEnRango(
                rangoInicio.getYear(),
                rangoInicio.getMonthValue(),
                rangoInicio.getDayOfMonth(),
                rangoFin.getYear(),
                rangoFin.getMonthValue(),
                rangoFin.getDayOfMonth()
        );
        if (pendientesRango.isEmpty()) {
            return "Sin pedidos NO_ASIGNADO en rango " + rangoInicio.toLocalTime() + " - " + rangoFin.toLocalTime();
        }

        System.out.println("📦 Pedidos a planificar: " + pendientesRango.size());

        // 3) Ejecutar algoritmo SOLO con los pedidos del rango
        Solucion solucion = algoritmo.ejecutarPlanificacion(pendientesRango);
        if (solucion == null || solucion.getRutas() == null || solucion.getRutas().isEmpty()) {
            return "Algoritmo no retornó rutas";
        }

        int asignados = 0;
        List<RutaAsignada> rutasParaGuardar = new ArrayList<>(); // ← AGREGAR ESTA LÍNEA

        // 4) Preparar rutas (sin guardar aún)
        for (Ruta rutaAlg : solucion.getRutas()) {
            if (rutaAlg == null || rutaAlg.getPedido() == null) continue;

            Long pedidoId = rutaAlg.getPedido().getId();
            if (pedidoId == null) continue;

            var pedidoOpt = pedidoRepo.findById(pedidoId);
            if (pedidoOpt.isEmpty()) continue;

            Pedido pedido = pedidoOpt.get();

            // Solo procesamos si el pedido sigue NO_ASIGNADO
            if (pedido.getEstado() != EstadoPedido.NO_ASIGNADO) continue;

            // --- Cabecera (rutas_asignadas) ---
            RutaAsignada cab = new RutaAsignada();
            cab.setPedidoId(pedidoId);
            cab.setCantidad(rutaAlg.getCantidad());

            // --- Calcular fecha de cada tramo ---
            LocalDateTime fechaPedido = pedido.getFechaPedido();
            LocalDate fechaActual = fechaPedido.toLocalDate();

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

                    // Las horas del vuelo están en hora LOCAL de cada aeropuerto
                    LocalDateTime horaSalidaLocal = v.getHoraSalida();
                    LocalDateTime horaLlegadaLocal = v.getHoraLlegada();

                    // Paso 1: Convertir a UTC
                    LocalDateTime horaSalidaUTC = convertirAUTC(horaSalidaLocal, husoOrigen);
                    LocalDateTime horaLlegadaUTC = convertirAUTC(horaLlegadaLocal, husoDestino);

                    // Paso 2: Convertir de UTC a hora de Lima
                    LocalDateTime horaSalidaLima = convertirAHoraLima(horaSalidaUTC);
                    LocalDateTime horaLlegadaLima = convertirAHoraLima(horaLlegadaUTC);
                    // ===========================================================

                    String horaSalidaStr = toHHmm(horaSalidaLima);
                    String horaLlegadaStr = toHHmm(horaLlegadaLima);

                    t.setHoraSalida(horaSalidaStr);
                    t.setHoraLlegada(horaLlegadaStr);


                    t.setFecha(horaSalidaLima.toLocalDate());

                    // Para el siguiente tramo, la fecha base es el día de llegada del vuelo actual
                    // (asumimos que puede conectar el mismo día o siguiente)

                    cab.addTramo(t);
                }
            }


            rutasParaGuardar.add(cab);

            // Marcar pedido como ASIGNADO (ya no lo guardamos aquí)
            pedido.setEstado(EstadoPedido.ASIGNADO);
            pedido.setTramoActual(0);

            asignados++;
        }


        if (!rutasParaGuardar.isEmpty()) {
            rutaBatchService.guardarRutasEnLote(rutasParaGuardar);
        }


        List<Pedido> pedidosActualizar = new ArrayList<>();
        for (RutaAsignada ruta : rutasParaGuardar) {
            pedidoRepo.findById(ruta.getPedidoId()).ifPresent(pedido -> {
                pedido.setEstado(EstadoPedido.ASIGNADO);
                pedido.setTramoActual(0);
                pedidosActualizar.add(pedido);
            });
        }

        if (!rutasParaGuardar.isEmpty()) {

            StringBuilder sqlUpdate = new StringBuilder("UPDATE pedido SET estado = CASE id ");
            StringBuilder sqlTramo = new StringBuilder(", tramo_actual = CASE id ");
            StringBuilder sqlWhere = new StringBuilder(" WHERE id IN (");

            for (int i = 0; i < rutasParaGuardar.size(); i++) {
                RutaAsignada ruta = rutasParaGuardar.get(i);
                Long pedidoId = ruta.getPedidoId();

                sqlUpdate.append("WHEN ").append(pedidoId).append(" THEN 'ASIGNADO' ");
                sqlTramo.append("WHEN ").append(pedidoId).append(" THEN 0 ");

                if (i > 0) sqlWhere.append(", ");
                sqlWhere.append(pedidoId);
            }

            sqlUpdate.append("END");
            sqlTramo.append("END");
            sqlWhere.append(")");

            String updateFinal = sqlUpdate.toString() + sqlTramo.toString() + sqlWhere.toString();

            jdbcTemplate.update(updateFinal);

            System.out.println("✅ Actualizados " + rutasParaGuardar.size() + " pedidos a ASIGNADO");
        }
        return "✅ Asignados=" + asignados + " de " + pendientesRango.size() +
                " (Rango: " + rangoInicio.toLocalTime() + " - " + rangoFin.toLocalTime() + ")";
    }

    // --------- helpers ---------

    private static String sane(String s) {
        return (s == null) ? null : s.trim();
    }

    /** Convierte varios tipos a "HH:MM" o null (String/LocalTime/LocalDateTime/OffsetDateTime). */
    private static String toHHmm(Object timeObj) {
        if (timeObj == null) return null;

        if (timeObj instanceof String str) {
            String t = str.trim();
            if (t.matches("^\\d{1}:\\d{2}$")) t = "0" + t;          // "8:05" -> "08:05"
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

    /**
     * Convierte una hora local a UTC
     */
    private static LocalDateTime convertirAUTC(LocalDateTime fechaLocal, int husoHorario) {
        return fechaLocal.minusHours(husoHorario);
    }

    /**
     * Convierte UTC a hora de Lima (UTC-5)
     */
    private static LocalDateTime convertirAHoraLima(LocalDateTime fechaUTC) {
        final int HUSO_LIMA = -5;
        return fechaUTC.plusHours(HUSO_LIMA);
    }
}