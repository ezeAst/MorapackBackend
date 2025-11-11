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
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
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

    public PlanificadorPersistenciaService(PedidoRepository pedidoRepo,
                                           RutaAsignadaRepository rutaRepo,
                                           PlanificadorService algoritmo) {
        this.pedidoRepo = pedidoRepo;
        this.rutaRepo = rutaRepo;
        this.algoritmo = algoritmo;
    }

    @Transactional
    public String ejecutarYGuardar() {

        // 1) Calcular ventana de búsqueda: 72 horas atrás → ahora
        LocalDateTime ahora = LocalDateTime.now();

        // ✅ Inicio: hace 72 horas (3 días - plazo máximo de entrega)
        LocalDateTime rangoInicio = ahora.minusHours(72);

        // ✅ Fin: ahora (hora actual)
        LocalDateTime rangoFin = ahora;

        System.out.println("🕐 Rango de planificación: " + rangoInicio + " a " + rangoFin);
        System.out.println("   (Buscando pedidos atrasados y actuales)");

        System.out.println("🕐 Rango de planificación: " + rangoInicio + " a " + rangoFin);

        // 2) Buscar pedidos NO_ASIGNADO en ese rango de 15 minutos
        List<Pedido> todosPendientes = pedidoRepo.findPendientes();

        int year = ahora.getYear();

        List<Pedido> pendientesRango = todosPendientes.stream()
                .filter(p -> {
                    LocalDateTime fechaPedido = p.getFechaPedido(year);
                    // Comparar: fechaPedido >= rangoInicio && fechaPedido <= rangoFin
                    return (fechaPedido.isAfter(rangoInicio) || fechaPedido.isEqual(rangoInicio))
                            && (fechaPedido.isBefore(rangoFin) || fechaPedido.isEqual(rangoFin));
                })
                .collect(Collectors.toList());

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

        // 4) Persistir cada ruta propuesta
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

                    String horaSalidaStr = toHHmm(v.getHoraSalida());
                    String horaLlegadaStr = toHHmm(v.getHoraLlegada());

                    t.setHoraSalida(horaSalidaStr);
                    t.setHoraLlegada(horaLlegadaStr);

                    // ✅ CALCULAR FECHA DEL VUELO
                    LocalTime horaSalida = LocalTime.parse(horaSalidaStr);
                    LocalDateTime fechaHoraPedido = LocalDateTime.of(fechaActual, LocalTime.of(pedido.getHora(), pedido.getMinuto()));
                    LocalDateTime fechaHoraSalida = LocalDateTime.of(fechaActual, horaSalida);

                    // Si el vuelo sale antes que la hora del pedido, es al día siguiente
                    if (fechaHoraSalida.isBefore(fechaHoraPedido)) {
                        fechaActual = fechaActual.plusDays(1);
                    }

                    t.setFecha(fechaActual);

                    // Para el siguiente tramo, la fecha base es el día de llegada del vuelo actual
                    // (asumimos que puede conectar el mismo día o siguiente)

                    cab.addTramo(t);
                }
            }

            // Guardar cabecera + tramos
            rutaRepo.save(cab);

            // ✅ Marcar pedido como ASIGNADO e inicializar tramoActual
            pedido.setEstado(EstadoPedido.ASIGNADO);
            pedido.setTramoActual(0);
            pedidoRepo.save(pedido);

            asignados++;
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
}
