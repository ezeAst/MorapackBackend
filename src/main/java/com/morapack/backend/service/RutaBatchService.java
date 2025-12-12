package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.Ruta;
import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RutaBatchService {

    private final JdbcTemplate jdbcTemplate;

    public RutaBatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void guardarRutasEnLote(List<RutaAsignada> rutas) {
        if (rutas == null || rutas.isEmpty()) return;

        long t1 = System.currentTimeMillis();
        int batchSize = 500; // Procesar en lotes de 500

        for (int i = 0; i < rutas.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rutas.size());
            List<RutaAsignada> batch = rutas.subList(i, end);
            procesarBatch(batch);
        }

        long t2 = System.currentTimeMillis();
        System.out.println("⚡ " + rutas.size() + " rutas guardadas en " + (t2 - t1) + "ms");
    }

    private void procesarBatch(List<RutaAsignada> batch) {

        StringBuilder sqlRutas = new StringBuilder(
                "INSERT INTO rutas_asignadas (cantidad, creado_en, pedido_id) VALUES "
        );

        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());

        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sqlRutas.append(", ");
            RutaAsignada ruta = batch.get(i);
            sqlRutas.append(String.format("(%d, '%s', %d)",
                    ruta.getCantidad(),
                    ahora.toString(),
                    ruta.getPedidoId()
            ));
        }

        jdbcTemplate.update(sqlRutas.toString());


        StringBuilder sqlGetIds = new StringBuilder("SELECT id, pedido_id FROM rutas_asignadas WHERE pedido_id IN (");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sqlGetIds.append(", ");
            sqlGetIds.append(batch.get(i).getPedidoId());
        }
        sqlGetIds.append(") ORDER BY id DESC LIMIT ").append(batch.size());

        List<Long[]> idsGenerados = jdbcTemplate.query(sqlGetIds.toString(),
                (rs, rowNum) -> new Long[]{rs.getLong("id"), rs.getLong("pedido_id")}
        );

        // Mapear pedidoId -> rutaId
        java.util.Map<Long, Long> mapaPedidoRuta = new java.util.HashMap<>();
        for (Long[] pair : idsGenerados) {
            mapaPedidoRuta.put(pair[1], pair[0]); // pedidoId -> rutaId
        }


        StringBuilder sqlTramos = new StringBuilder(
                "INSERT INTO rutas_tramo (capacidad_usada, destino, fecha, hora_llegada, hora_salida, orden, origen, ruta_id) VALUES "
        );

        boolean firstTramo = true;
        for (RutaAsignada ruta : batch) {
            Long rutaId = mapaPedidoRuta.get(ruta.getPedidoId());
            if (rutaId == null) continue;

            for (RutaTramo tramo : ruta.getTramos()) {
                if (!firstTramo) sqlTramos.append(", ");
                firstTramo = false;

                sqlTramos.append(String.format("(%d, '%s', '%s', '%s', '%s', %d, '%s', %d)",
                        tramo.getCapacidadUsada() != null ? tramo.getCapacidadUsada() : 0,
                        tramo.getDestino(),
                        tramo.getFecha().toString(),
                        tramo.getHoraLlegada(),
                        tramo.getHoraSalida(),
                        tramo.getOrden(),
                        tramo.getOrigen(),
                        rutaId
                ));
            }
        }

        if (!firstTramo) {
            jdbcTemplate.update(sqlTramos.toString());
        }
    }

    private List<String> getOrdersInFlight(Vuelo vuelo, Solucion solucion) {
        List<String> orderIds = new ArrayList<>();

        if (vuelo == null || solucion == null) {
            return orderIds;
        }

        // Recorrer todas las rutas de la solución
        for (Ruta ruta : solucion.getRutas()) {
            if (ruta.getPedido() == null) continue;

            // Verificar si este vuelo está en la ruta
            for (Vuelo vueloEnRuta : ruta.getVuelos()) {
                // Comparar vuelos por origen, destino y hora de salida
                boolean mismoOrigen = vueloEnRuta.getAeropuertoOrigen().getCodigo()
                        .equals(vuelo.getAeropuertoOrigen().getCodigo());
                boolean mismoDestino = vueloEnRuta.getAeropuertoDestino().getCodigo()
                        .equals(vuelo.getAeropuertoDestino().getCodigo());
                boolean mismaHora = vueloEnRuta.getHoraSalida().equals(vuelo.getHoraSalida());

                if (mismoOrigen && mismoDestino && mismaHora) {
                    // Este pedido está en este vuelo
                    orderIds.add(ruta.getPedido().getIdCliente());
                    break; // Ya encontramos este pedido en este vuelo, pasar al siguiente
                }
            }
        }

        return orderIds;
    }
}