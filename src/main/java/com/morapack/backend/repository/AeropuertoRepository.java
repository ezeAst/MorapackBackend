package com.morapack.backend.repository;

import com.morapack.backend.entity.AeropuertoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AeropuertoRepository extends JpaRepository<AeropuertoEntity, Long> {

    Optional<AeropuertoEntity> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}