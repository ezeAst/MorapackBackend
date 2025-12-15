package com.morapack.backend.repository;

import com.morapack.backend.entity.AlmacenOcupacionTemporal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlmacenOcupacionTemporalRepository extends JpaRepository<AlmacenOcupacionTemporal, Long> {

    /**
     * Obtiene todas las ocupaciones de un aeropuerto específico
     */
    List<AlmacenOcupacionTemporal> findByAeropuertoCodigo(String aeropuertoCodigo);

    /**
     * Calcula la ocupación total de un aeropuerto en un momento específico
     *
     * Suma todos los paquetes que están en el almacén en ese momento
     * (donde momento está entre hora_inicio y hora_fin)
     */
    @Query("SELECT COALESCE(SUM(o.cantidad), 0) " +
            "FROM AlmacenOcupacionTemporal o " +
            "WHERE o.aeropuertoCodigo = :codigo " +
            "AND o.horaInicio <= :momento " +
            "AND o.horaFin > :momento")
    Integer calcularOcupacionEn(
            @Param("codigo") String aeropuertoCodigo,
            @Param("momento") LocalDateTime momento
    );

    /**
     * Obtiene todas las ocupaciones activas en un momento dado
     * (útil para debugging)
     */
    @Query("SELECT o FROM AlmacenOcupacionTemporal o " +
            "WHERE o.horaInicio <= :momento " +
            "AND o.horaFin > :momento")
    List<AlmacenOcupacionTemporal> findOcupacionesActivasEn(@Param("momento") LocalDateTime momento);

    /**
     * Obtiene ocupaciones de un aeropuerto que se cruzan con un periodo
     *
     * Útil para verificar si hay conflictos al planificar
     */
    @Query("SELECT o FROM AlmacenOcupacionTemporal o " +
            "WHERE o.aeropuertoCodigo = :codigo " +
            "AND o.horaInicio < :fin " +
            "AND o.horaFin > :inicio")
    List<AlmacenOcupacionTemporal> findOcupacionesEnPeriodo(
            @Param("codigo") String aeropuertoCodigo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    /**
     * Calcula la ocupación máxima en un periodo
     *
     * Encuentra el momento de máxima ocupación en el rango dado
     */
    @Query("SELECT COALESCE(MAX(ocupacion), 0) FROM (" +
            "  SELECT SUM(o.cantidad) as ocupacion " +
            "  FROM AlmacenOcupacionTemporal o " +
            "  WHERE o.aeropuertoCodigo = :codigo " +
            "  AND (o.horaInicio BETWEEN :inicio AND :fin " +
            "       OR o.horaFin BETWEEN :inicio AND :fin " +
            "       OR (o.horaInicio < :inicio AND o.horaFin > :fin)) " +
            "  GROUP BY o.horaInicio" +
            ") AS ocupaciones")
    Integer calcularOcupacionMaximaEnPeriodo(
            @Param("codigo") String aeropuertoCodigo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );

    /**
     * Elimina todas las ocupaciones de un pedido específico
     * (cuando el pedido cambia de estado o se cancela)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AlmacenOcupacionTemporal o WHERE o.pedidoId = :pedidoId")
    void deleteByPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Elimina la ocupación de un pedido en un aeropuerto específico
     * (cuando el paquete sale de ese almacén)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AlmacenOcupacionTemporal o " +
            "WHERE o.pedidoId = :pedidoId AND o.aeropuertoCodigo = :codigo")
    void deleteByPedidoIdAndAeropuerto(
            @Param("pedidoId") Long pedidoId,
            @Param("codigo") String aeropuertoCodigo
    );

    /**
     * Limpia todas las ocupaciones que ya vencieron
     * (hora_fin < ahora)
     *
     * Debe ejecutarse periódicamente para mantener la tabla limpia
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AlmacenOcupacionTemporal o WHERE o.horaFin < :ahora")
    int deleteOcupacionesVencidas(@Param("ahora") LocalDateTime ahora);

    /**
     * Cuenta cuántas ocupaciones vencidas hay
     * (útil para logs antes de limpiar)
     */
    @Query("SELECT COUNT(o) FROM AlmacenOcupacionTemporal o WHERE o.horaFin < :ahora")
    long countOcupacionesVencidas(@Param("ahora") LocalDateTime ahora);

    /**
     * Obtiene todas las ocupaciones de un pedido
     * (útil para debugging y validación)
     */
    List<AlmacenOcupacionTemporal> findByPedidoId(Long pedidoId);

    /**
     * Agrupa ocupaciones por aeropuerto
     * (útil para generar reportes)
     */
    @Query("SELECT o.aeropuertoCodigo, SUM(o.cantidad) " +
            "FROM AlmacenOcupacionTemporal o " +
            "GROUP BY o.aeropuertoCodigo")
    List<Object[]> calcularOcupacionPorAeropuerto();
}