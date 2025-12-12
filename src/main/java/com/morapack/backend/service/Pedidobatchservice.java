package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.Pedido;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio ULTRA OPTIMIZADO para inserción masiva de pedidos.
 *
 * Usa batchUpdate() con chunks para máxima velocidad.
 * NO usa LOAD DATA INFILE para evitar problemas de configuración.
 */
@Service
public class Pedidobatchservice {

    private final JdbcTemplate jdbcTemplate;

    // Tamaño del chunk - optimizado para MySQL
    private static final int CHUNK_SIZE = 5000;

    public Pedidobatchservice(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserta múltiples pedidos usando batch processing optimizado en chunks.
     *
     * OPTIMIZACIONES:
     * 1. Usa batchUpdate() real con prepared statements
     * 2. Procesa en chunks de 5000 para evitar timeouts
     * 3. NO requiere LOAD DATA INFILE habilitado
     */
    @Transactional
    public int insertarPedidosEnLote(List<Pedido> pedidos, Long idInicial) {
        if (pedidos == null || pedidos.isEmpty()) {
            return 0;
        }

        long t1 = System.currentTimeMillis();
        int totalInsertados = 0;

        String sql = "INSERT INTO pedido " +
                "(id, dia, mes, hora, minuto, anho, aeropuerto_destino, " +
                "id_cliente, cantidad, cantidad_cumplida, estado, tramo_actual, hora_entrega) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'NO_ASIGNADO', 0, NULL)";

        // Procesar en chunks
        int totalPedidos = pedidos.size();
        int chunks = (int) Math.ceil((double) totalPedidos / CHUNK_SIZE);

        System.out.println("📦 Procesando " + totalPedidos + " pedidos en " + chunks + " chunks de hasta " + CHUNK_SIZE);

        for (int chunkIndex = 0; chunkIndex < chunks; chunkIndex++) {
            long chunkStart = System.currentTimeMillis();

            int startIdx = chunkIndex * CHUNK_SIZE;
            int endIdx = Math.min(startIdx + CHUNK_SIZE, totalPedidos);

            List<Object[]> batchArgs = new ArrayList<>(endIdx - startIdx);

            for (int i = startIdx; i < endIdx; i++) {
                Pedido p = pedidos.get(i);
                long nuevoId = idInicial + i + 1;

                batchArgs.add(new Object[]{
                        nuevoId,
                        p.getDia(),
                        p.getMes(),
                        p.getHora(),
                        p.getMinuto(),
                        p.getAnho(),
                        p.getAeropuertoDestino(),
                        p.getIdCliente(),
                        p.getCantidad()
                });
            }

            // Ejecutar batch para este chunk
            int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
            totalInsertados += updateCounts.length;

            long chunkEnd = System.currentTimeMillis();
            System.out.println("  ✅ Chunk " + (chunkIndex + 1) + "/" + chunks +
                    ": " + updateCounts.length + " pedidos en " +
                    (chunkEnd - chunkStart) + "ms");
        }

        long t2 = System.currentTimeMillis();
        long tiempoTotal = t2 - t1;
        double pedidosPorSegundo = (totalInsertados * 1000.0) / tiempoTotal;

        System.out.println("⚡ TOTAL: " + totalInsertados + " pedidos insertados en " +
                tiempoTotal + "ms (" + String.format("%.0f", pedidosPorSegundo) + " pedidos/seg)");

        return totalInsertados;
    }
}