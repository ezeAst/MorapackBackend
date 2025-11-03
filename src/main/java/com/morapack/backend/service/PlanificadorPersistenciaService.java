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
        // 1) Pedidos pendientes
        List<Pedido> pendientes = pedidoRepo.findPendientes();
        if (pendientes == null || pendientes.isEmpty()) {
            return "Sin pedidos NO_ASIGNADO";
        }

        // 2) Ejecutar algoritmo SOLO con los NO_ASIGNADO
        Solucion solucion = algoritmo.ejecutarPlanificacion(pendientes);
        if (solucion == null || solucion.getRutas() == null || solucion.getRutas().isEmpty()) {
            return "Algoritmo no retornó rutas";
        }

        int asignados = 0;

        // 3) Persistir cada ruta propuesta
        for (Ruta rutaAlg : solucion.getRutas()) {
            if (rutaAlg == null || rutaAlg.getPedido() == null) continue;

            Long pedidoId = rutaAlg.getPedido().getId();   // debe ser ID real de BD
            if (pedidoId == null) continue;

            var pedidoOpt = pedidoRepo.findById(pedidoId);
            if (pedidoOpt.isEmpty()) continue;

            Pedido pedido = pedidoOpt.get();

            // Solo procesamos si el pedido sigue NO_ASIGNADO
            if (pedido.getEstado() != EstadoPedido.NO_ASIGNADO) continue;

            // --- Cabecera (rutas_asignadas) ---
            RutaAsignada cab = new RutaAsignada();
            cab.setPedidoId(pedidoId);
            cab.setCantidad(rutaAlg.getCantidad()); // entero, puede ser null

            // --- Tramos (rutas_tramo) ---
            int orden = 0;
            List<Vuelo> vuelos = rutaAlg.getVuelos();
            if (vuelos != null) {
                for (Vuelo v : vuelos) {
                    if (v == null) continue;
                    RutaTramo t = new RutaTramo();
                    t.setOrden(orden++);
                    t.setOrigen(sane(v.getAeropuertoOrigen().toString()));         // p.ej. "LIM"
                    t.setDestino(sane(v.getAeropuertoDestino().toString()));       // p.ej. "SCL"
                    t.setHoraSalida(toHHmm(v.getHoraSalida()));
                    t.setHoraLlegada(toHHmm(v.getHoraLlegada()));
                    cab.addTramo(t);                          // set ruta y agrega a la lista
                }
            }

            // Guardar cabecera + tramos (Cascade.ALL en la entity)
            rutaRepo.save(cab);

            // Marcar pedido como ASIGNADO
            pedido.setEstado(EstadoPedido.ASIGNADO);
            pedidoRepo.save(pedido);

            asignados++;
        }

        return "Asignados=" + asignados + " de " + pendientes.size();
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
