package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.dto.VueloCancelacionRequest;
import com.morapack.backend.dto.VueloCancelacionResponse;
import com.morapack.backend.entity.VueloCancelado;
import com.morapack.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar cancelaciones de vuelos y reasignación de pedidos
 */
@Service
public class VueloCancelacionService {

    private final VueloCanceladoRepository vueloCanceladoRepo;
    private final RutaTramoRepository rutaTramoRepo;
    private final RutaAsignadaRepository rutaAsignadaRepo;
    private final AlmacenOcupacionTemporalRepository ocupacionRepo;
    private final PedidoRepository pedidoRepo;
    private final TiempoSimuladoService tiempoSimuladoService;

    public VueloCancelacionService(
            VueloCanceladoRepository vueloCanceladoRepo,
            RutaTramoRepository rutaTramoRepo,
            RutaAsignadaRepository rutaAsignadaRepo,
            AlmacenOcupacionTemporalRepository ocupacionRepo,
            PedidoRepository pedidoRepo,
            TiempoSimuladoService tiempoSimuladoService) {
        this.vueloCanceladoRepo = vueloCanceladoRepo;
        this.rutaTramoRepo = rutaTramoRepo;
        this.rutaAsignadaRepo = rutaAsignadaRepo;
        this.ocupacionRepo = ocupacionRepo;
        this.pedidoRepo = pedidoRepo;
        this.tiempoSimuladoService = tiempoSimuladoService;
    }

    /**
     * Procesa cancelaciones de vuelos y reasigna pedidos afectados
     */
    @Transactional
    public VueloCancelacionResponse procesarCancelaciones(VueloCancelacionRequest request) {
        System.out.println("\n🚫 === PROCESANDO CANCELACIONES DE VUELOS ===");
        System.out.println("📅 Fecha: " + request.getFecha());
        System.out.println("✈️ Vuelos a cancelar: " + request.getVuelosCancelados().size());

        // 1) Validar request
        if (!request.esValida()) {
            return VueloCancelacionResponse.error("Request inválida - verificar formato");
        }

        int vuelosRegistrados = 0;
        int vuelosDuplicados = 0;
        List<VueloCancelado> cancelacionesNuevas = new ArrayList<>();

        // 2) Parsear y registrar cancelaciones
        for (String lineaVuelo : request.getVuelosCancelados()) {
            try {
                VueloCancelado cancelacion = parsearVueloCancelado(lineaVuelo, request.getFecha());

                // Verificar si ya existe
                boolean existe = vueloCanceladoRepo.existeCancelacion(
                        cancelacion.getOrigen(),
                        cancelacion.getDestino(),
                        cancelacion.getFecha(),
                        cancelacion.getHoraSalidaLocal()
                );

                if (existe) {
                    vuelosDuplicados++;
                    System.out.println("   ⚠️ Cancelación duplicada: " + cancelacion.getClave());
                } else {
                    vueloCanceladoRepo.save(cancelacion);
                    cancelacionesNuevas.add(cancelacion);
                    vuelosRegistrados++;
                    System.out.println("   ✅ Registrado: " + cancelacion.getClave());
                }

            } catch (Exception e) {
                System.err.println("   ❌ Error parseando: " + lineaVuelo + " - " + e.getMessage());
            }
        }

        System.out.println("\n📊 Cancelaciones registradas: " + vuelosRegistrados);
        System.out.println("📊 Duplicados omitidos: " + vuelosDuplicados);

        // 3) Identificar pedidos afectados por las NUEVAS cancelaciones
        Set<Long> pedidosAfectados = identificarPedidosAfectados(cancelacionesNuevas);

        System.out.println("\n🔍 Pedidos afectados por cancelaciones: " + pedidosAfectados.size());

        // 4) Limpiar y resetear pedidos afectados
        int rutasEliminadas = 0;
        List<Long> pedidosReasignados = new ArrayList<>();

        for (Long pedidoId : pedidosAfectados) {
            try {
                boolean reseteado = resetearPedido(pedidoId);
                if (reseteado) {
                    pedidosReasignados.add(pedidoId);
                    rutasEliminadas++;
                }
            } catch (Exception e) {
                System.err.println("   ❌ Error reseteando pedido " + pedidoId + ": " + e.getMessage());
            }
        }

        System.out.println("✅ Pedidos reseteados para reasignación: " + pedidosReasignados.size());
        System.out.println("✅ Rutas eliminadas: " + rutasEliminadas);

        return VueloCancelacionResponse.exito(
                vuelosRegistrados,
                vuelosDuplicados,
                pedidosAfectados.size(),
                rutasEliminadas,
                pedidosReasignados
        );
    }

    /**
     * Parsea una línea de vuelo cancelado
     * Formato: SPIM-SKBO-04:35-08:51-0340
     */
    private VueloCancelado parsearVueloCancelado(String linea, LocalDate fecha) {
        String[] partes = linea.trim().split("-");

        if (partes.length != 5) {
            throw new IllegalArgumentException("Formato inválido: " + linea);
        }

        String origen = partes[0].trim();
        String destino = partes[1].trim();
        String horaSalida = partes[2].trim();
        String horaLlegada = partes[3].trim();
        int capacidad = Integer.parseInt(partes[4]);

        return new VueloCancelado(origen, destino, horaSalida, horaLlegada, capacidad, fecha);
    }

    /**
     * Identifica pedidos que usan vuelos cancelados
     *
     * Busca en rutas_tramo los tramos que coinciden con vuelos cancelados
     */
    private Set<Long> identificarPedidosAfectados(List<VueloCancelado> cancelaciones) {
        Set<Long> pedidosAfectados = new HashSet<>();

        for (VueloCancelado cancelacion : cancelaciones) {
            // Query personalizada para encontrar tramos afectados
            List<Object[]> tramosAfectados = rutaTramoRepo.findTramosAfectadosPorCancelacion(
                    cancelacion.getOrigen(),
                    cancelacion.getDestino(),
                    cancelacion.getFecha(),
                    cancelacion.getHoraSalidaLocal()
            );

            for (Object[] resultado : tramosAfectados) {
                Long pedidoId = (Long) resultado[0];
                pedidosAfectados.add(pedidoId);

                System.out.println("   🔍 Pedido afectado: " + pedidoId +
                        " (vuelo " + cancelacion.getClave() + ")");
            }
        }

        return pedidosAfectados;
    }

    /**
     * Resetea un pedido para que sea reasignado
     *
     * - Elimina rutas asignadas
     * - Elimina tramos de ruta
     * - Elimina ocupaciones temporales
     * - Resetea estado del pedido a NO_ASIGNADO
     * - Resetea cantidadCumplida a 0
     * - Resetea tramoActual a 0
     */
    @Transactional
    public boolean resetearPedido(Long pedidoId) {
        System.out.println("   🔄 Reseteando pedido: " + pedidoId);

        // Obtener pedido
        Optional<Pedido> pedidoOpt = pedidoRepo.findById(pedidoId);
        if (pedidoOpt.isEmpty()) {
            System.err.println("   ❌ Pedido no encontrado: " + pedidoId);
            return false;
        }

        Pedido pedido = pedidoOpt.get();

        // Solo resetear si NO está EN_RUTA ni ENTREGADO
        if (pedido.getEstado() == EstadoPedido.EN_TRANSITO ||
                pedido.getEstado() == EstadoPedido.ENTREGADO) {
            System.out.println("   ⚠️ Pedido " + pedidoId + " en estado " +
                    pedido.getEstado() + " - NO se resetea");
            return false;
        }

        // 1) Eliminar ocupaciones temporales
        try {
            ocupacionRepo.deleteByPedidoId(pedidoId);
            System.out.println("      ✅ Ocupaciones temporales eliminadas");
        } catch (Exception e) {
            System.err.println("      ⚠️ Error eliminando ocupaciones: " + e.getMessage());
        }

        // 2) Eliminar rutas asignadas (esto eliminará los tramos en cascada)
        try {
            rutaAsignadaRepo.deleteByPedidoId(pedidoId);
            System.out.println("      ✅ Rutas asignadas eliminadas");
        } catch (Exception e) {
            System.err.println("      ⚠️ Error eliminando rutas: " + e.getMessage());
        }

        // 3) Resetear estado del pedido
        pedido.setEstado(EstadoPedido.NO_ASIGNADO);
        pedido.setTramoActual(0);
        pedido.setCantidadCumplida(0);
        pedido.setHoraEntrega(null);

        pedidoRepo.save(pedido);
        System.out.println("      ✅ Pedido reseteado a NO_ASIGNADO");

        return true;
    }

    /**
     * Obtiene todas las cancelaciones activas
     */
    public List<VueloCancelado> obtenerCancelacionesActivas() {
        return vueloCanceladoRepo.findByActivoTrue();
    }

    /**
     * Obtiene cancelaciones para una fecha específica
     */
    public List<VueloCancelado> obtenerCancelacionesPorFecha(LocalDate fecha) {
        return vueloCanceladoRepo.findByFechaAndActivoTrue(fecha);
    }

    /**
     * Limpia cancelaciones antiguas (más de X días)
     */
    @Transactional
    public int limpiarCancelacionesAntiguas(int diasAtras) {
        LocalDate fechaLimite = LocalDate.now().minusDays(diasAtras);
        return vueloCanceladoRepo.eliminarCancelacionesAntiguas(fechaLimite);
    }

    /**
     * Verifica si un vuelo específico está cancelado
     */
    public boolean estaCancelado(String origen, String destino, LocalDate fecha, String horaSalida) {
        return vueloCanceladoRepo.existeCancelacion(origen, destino, fecha, horaSalida);
    }
}