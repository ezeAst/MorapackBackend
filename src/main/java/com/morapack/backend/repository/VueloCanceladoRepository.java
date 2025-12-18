package com.morapack.backend.repository;

import com.morapack.backend.entity.VueloCancelado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VueloCanceladoRepository extends JpaRepository<VueloCancelado, Long> {

    /**
     * Busca todas las cancelaciones activas
     */
    List<VueloCancelado> findByActivoTrue();

    /**
     * Busca todas las cancelaciones para una fecha específica
     */
    List<VueloCancelado> findByFechaAndActivoTrue(LocalDate fecha);

    /**
     * Busca todas las cancelaciones desde una fecha en adelante
     */
    List<VueloCancelado> findByFechaGreaterThanEqualAndActivoTrue(LocalDate fecha);

    /**
     * Verifica si existe una cancelación para un vuelo específico
     */
    @Query("SELECT COUNT(v) > 0 FROM VueloCancelado v " +
            "WHERE v.origen = :origen " +
            "AND v.destino = :destino " +
            "AND v.fecha = :fecha " +
            "AND v.horaSalidaLocal = :horaSalida " +
            "AND v.activo = true")
    boolean existeCancelacion(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("fecha") LocalDate fecha,
            @Param("horaSalida") String horaSalida
    );

    /**
     * Obtiene una cancelación específica si existe
     */
    @Query("SELECT v FROM VueloCancelado v " +
            "WHERE v.origen = :origen " +
            "AND v.destino = :destino " +
            "AND v.fecha = :fecha " +
            "AND v.horaSalidaLocal = :horaSalida " +
            "AND v.activo = true")
    VueloCancelado findCancelacion(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("fecha") LocalDate fecha,
            @Param("horaSalida") String horaSalida
    );

    /**
     * Desactiva (soft-delete) cancelaciones antiguas
     */
    @Modifying
    @Transactional
    @Query("UPDATE VueloCancelado v SET v.activo = false " +
            "WHERE v.fecha < :fecha")
    int desactivarCancelacionesAntiguas(@Param("fecha") LocalDate fecha);

    /**
     * Elimina físicamente cancelaciones antiguas (cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM VueloCancelado v WHERE v.fecha < :fecha")
    int eliminarCancelacionesAntiguas(@Param("fecha") LocalDate fecha);

    /**
     * Cuenta cancelaciones activas
     */
    @Query("SELECT COUNT(v) FROM VueloCancelado v WHERE v.activo = true")
    long countActivas();
}