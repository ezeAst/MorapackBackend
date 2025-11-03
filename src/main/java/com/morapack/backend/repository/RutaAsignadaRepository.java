package com.morapack.backend.repository;

import com.morapack.backend.entity.RutaAsignada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RutaAsignadaRepository extends JpaRepository<RutaAsignada, Long> {
    Optional<RutaAsignada> findByPedidoId(Long pedidoId);
    void deleteByPedidoId(Long pedidoId);
}
