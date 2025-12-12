package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }


    @Transactional
    public Pedido crearPedidoDesdePayload(Map<String, Object> payload) {

        String idCliente = (String) payload.get("id_cliente");
        String aeropuertoDestino = (String) payload.get("aeropuerto_destino");

        // cantidad puede llegar como Integer o Double dependiendo del JSON parser
        int cantidad = ((Number) payload.get("cantidad")).intValue();

        String createdAtStr = (String) payload.get("created_at"); // "YYYY-MM-DDTHH:mm:ss"

        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
        // LocalDateTime.parse acepta "2025-12-12T18:15:00"

        // generar id manual (max + 1)
        Long nextId = pedidoRepository.findMaxId().orElse(0L) + 1;

        Pedido p = new Pedido();
        p.setId(nextId);

        p.setIdCliente(idCliente);
        p.setAeropuertoDestino(aeropuertoDestino);
        p.setCantidad(cantidad);
        p.setCantidadCumplida(0);

        p.setAnho(createdAt.getYear());
        p.setMes(createdAt.getMonthValue());
        p.setDia(createdAt.getDayOfMonth());
        p.setHora(createdAt.getHour());
        p.setMinuto(createdAt.getMinute());

        p.setTramoActual(0);

        // estado inicial (elige el que exista en tu enum SQL)
        p.setEstado(EstadoPedido.NO_ASIGNADO); // o NO_ASIGNADO si existe en tu DB

        p.setHoraEntrega(null);

        return pedidoRepository.save(p);
    }
}