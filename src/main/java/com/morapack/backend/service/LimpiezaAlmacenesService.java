package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LimpiezaAlmacenesService {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaAlmacenesService.class);

    private final PedidoRepository pedidoRepository;
    private final AeropuertoRepository aeropuertoRepository;

    public LimpiezaAlmacenesService(
            PedidoRepository pedidoRepository,
            AeropuertoRepository aeropuertoRepository
    ) {
        this.pedidoRepository = pedidoRepository;
        this.aeropuertoRepository = aeropuertoRepository;
    }

    /**
     * Limpia productos entregados hace más de 2 horas
     * Libera espacio en almacenes
     * Corre cada 2 horas
     */
    @Scheduled(fixedDelay = 3600000) // 2 horas = 7,200,000 milisegundos
    @Transactional
    public void limpiarProductosEntregados() {
        log.info("🧹 Iniciando limpieza de almacenes...");

        // Buscar pedidos ENTREGADOS
        List<Pedido> pedidosEntregados = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.ENTREGADO)
        );

        int limpiados = 0;
        int totalLiberado = 0;

        for (Pedido pedido : pedidosEntregados) {
            // Verificar si tiene hora_entrega registrada


            if (pedido.getHoraEntrega() == null) {
                continue; // Saltar si no tiene hora de entrega
            }

            LocalDateTime horaEntrega = pedido.getHoraEntrega();
            LocalDateTime dosHorasDespuesDeEntrega = horaEntrega.plusHours(2);

            // Verificar si han pasado más de 2 horas desde la entrega
            if (pedido.getHoraEntrega().isBefore(dosHorasDespuesDeEntrega)) {
                // Liberar espacio del almacén destino
                AeropuertoEntity aeropuerto = aeropuertoRepository
                        .findByCodigo(pedido.getAeropuertoDestino())
                        .orElse(null);

                if (aeropuerto != null) {
                    int capacidadAntes = aeropuerto.getCapacidadActual();
                    int nuevaCapacidad = Math.max(0, capacidadAntes - pedido.getCantidad());

                    aeropuerto.setCapacidadActual(nuevaCapacidad);
                    aeropuertoRepository.save(aeropuerto);

                    // ✅ CAMBIAR ESTADO A RECOGIDO (para no volver a procesarlo)
                    pedido.setEstado(EstadoPedido.RECOGIDO);
                    pedidoRepository.save(pedido);

                    limpiados++;
                    totalLiberado += pedido.getCantidad();

                    log.info("  ✅ Liberado espacio en {}: {} paquetes (pedido #{})",
                            aeropuerto.getCodigo(),
                            pedido.getCantidad(),
                            pedido.getId());
                    log.info("     Capacidad: {}/{} → {}/{}",
                            capacidadAntes, aeropuerto.getCapacidad(),
                            nuevaCapacidad, aeropuerto.getCapacidad());
                }
            }
        }

        log.info("🧹 Limpieza completada: {} pedidos procesados, {} paquetes liberados",
                limpiados, totalLiberado);
    }
}