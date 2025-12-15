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

        // ✅ FIX: Obtener los IDs generados EN ORDEN de inserción
        StringBuilder sqlGetIds = new StringBuilder(
                "SELECT id FROM rutas_asignadas WHERE creado_en = '" + ahora.toString() + "' ORDER BY id ASC"
        );

        List<Long> idsGenerados = jdbcTemplate.query(sqlGetIds.toString(),
                (rs, rowNum) -> rs.getLong("id")
        );

        System.out.println("🔍 IDs generados: " + idsGenerados.size() + " para " + batch.size() + " rutas");

        // ✅ FIX: Mapear por índice, no por pedido_id
        if (idsGenerados.size() != batch.size()) {
            System.err.println("❌ ERROR: Número de IDs generados (" + idsGenerados.size() +
                    ") no coincide con batch (" + batch.size() + ")");
            return;
        }

        StringBuilder sqlTramos = new StringBuilder(
                "INSERT INTO rutas_tramo (destino, fecha, hora_llegada, hora_salida, orden, origen, ruta_asignada_id) VALUES "
        );

        boolean firstTramo = true;
        for (int i = 0; i < batch.size(); i++) {
            RutaAsignada ruta = batch.get(i);
            Long rutaId = idsGenerados.get(i); // ✅ Usar el índice correspondiente

            System.out.println("📝 Guardando " + ruta.getTramos().size() + " tramos para ruta ID " + rutaId +
                    " (pedido " + ruta.getPedidoId() + ", cantidad " + ruta.getCantidad() + ")");

            for (RutaTramo tramo : ruta.getTramos()) {
                if (!firstTramo) sqlTramos.append(", ");
                firstTramo = false;

                sqlTramos.append(String.format("('%s', '%s', '%s', '%s', %d, '%s', %d)",
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
            System.out.println("✅ Tramos guardados exitosamente");
        } else {
            System.out.println("⚠️ No hay tramos para guardar");
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