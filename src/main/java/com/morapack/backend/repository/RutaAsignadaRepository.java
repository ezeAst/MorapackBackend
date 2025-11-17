package com.morapack.backend.repository;

import com.morapack.backend.entity.RutaAsignada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RutaAsignadaRepository extends JpaRepository<RutaAsignada, Long> {
    RutaAsignada findByPedidoId(Long pedidoId);

    void deleteByPedidoId(Long pedidoId);

    @Query("SELECT ra FROM RutaAsignada ra LEFT JOIN FETCH ra.tramos WHERE ra.pedidoId IN :pedidoIds")
    List<RutaAsignada> findByPedidoIdIn(@Param("pedidoIds") List<Long> pedidoIds);
}
