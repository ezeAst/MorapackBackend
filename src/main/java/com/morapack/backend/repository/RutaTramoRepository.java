package com.morapack.backend.repository;

import com.morapack.backend.entity.RutaTramo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RutaTramoRepository extends JpaRepository<RutaTramo, Long> {


    /**
     * Obtiene la capacidad ocupada por cada vuelo desde una fecha
     *
     * Agrupa todos los tramos por (origen, destino, fecha, hora_salida)
     * y suma la cantidad total de paquetes asignados
     *
     * @param desdeFecha fecha desde la cual buscar
     * @return Lista de arrays [origen, destino, fecha, hora_salida, cantidad_total]
     */
    @Query("SELECT t.origen, t.destino, t.fecha, t.horaSalida, SUM(r.cantidad) " +
            "FROM RutaTramo t " +
            "JOIN t.ruta r " +
            "WHERE t.fecha >= :desdeFecha " +
            "GROUP BY t.origen, t.destino, t.fecha, t.horaSalida")
    List<Object[]> findCapacidadOcupadaPorVuelo(@Param("desdeFecha") LocalDate desdeFecha);

    /**
     * Encuentra pedidos que usan un vuelo específico cancelado
     *
     * Retorna: [pedidoId, orden_tramo]
     */
    @Query("SELECT r.pedidoId, t.orden " +
            "FROM RutaTramo t " +
            "JOIN t.ruta r " +
            "WHERE t.origen = :origen " +
            "AND t.destino = :destino " +
            "AND t.fecha = :fecha " +
            "AND t.horaSalida = :horaSalida")
    List<Object[]> findTramosAfectadosPorCancelacion(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("fecha") LocalDate fecha,
            @Param("horaSalida") String horaSalida
    );

}