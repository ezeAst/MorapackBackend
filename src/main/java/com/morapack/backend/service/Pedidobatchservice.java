package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio optimizado para inserción masiva de pedidos usando JDBC batch.
 * Mucho más rápido que JPA saveAll() para grandes volúmenes.
 */
@Service
public class Pedidobatchservice {

    private final JdbcTemplate jdbcTemplate;

    public Pedidobatchservice(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserta múltiples pedidos en la BD usando JDBC batch insert.
     *
     * @param pedidos Lista de pedidos a insertar
     * @param idInicial ID inicial desde donde empezar a numerar
     * @return Cantidad de filas insertadas
     */
    @Transactional
    public int insertarPedidosEnLote(List<Pedido> pedidos, Long idInicial) {
        if (pedidos == null || pedidos.isEmpty()) {
            return 0;
        }

        long t1 = System.currentTimeMillis();

        // ✅ CONSTRUIR UN ÚNICO INSERT MASIVO
        StringBuilder sql = new StringBuilder("INSERT INTO pedido ");
        sql.append("(id, dia, mes, hora, minuto, anho, aeropuerto_destino, ");
        sql.append("id_cliente, cantidad, cantidad_cumplida, estado, tramo_actual, hora_entrega) VALUES ");

        for (int i = 0; i < pedidos.size(); i++) {
            Pedido p = pedidos.get(i);
            long nuevoId = idInicial + i + 1;

            if (i > 0) sql.append(", ");

            sql.append(String.format("(%d, %d, %d, %d, %d, %d, '%s', '%s', %d, 0, 'NO_ASIGNADO', 0, NULL)",
                    nuevoId,
                    p.getDia(),
                    p.getMes(),
                    p.getHora(),
                    p.getMinuto(),
                    p.getAnho(),
                    p.getAeropuertoDestino(),
                    p.getIdCliente(),
                    p.getCantidad()
            ));
        }

        long t2 = System.currentTimeMillis();
        System.out.println("⏱️ Tiempo preparando SQL: " + (t2 - t1) + "ms");

        int rows = jdbcTemplate.update(sql.toString());

        long t3 = System.currentTimeMillis();
        System.out.println("⏱️ Tiempo ejecutando SQL: " + (t3 - t2) + "ms");
        System.out.println("⏱️ Tiempo TOTAL: " + (t3 - t1) + "ms");

        return rows;
    }
}