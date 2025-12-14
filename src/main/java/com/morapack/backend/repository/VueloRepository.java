package com.morapack.backend.repository;

import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VueloRepository extends JpaRepository<Vuelo, Long> {

    List<Vuelo> findByOrigen(String origen);

    List<Vuelo> findByDestino(String destino);

    /**
     * ✅ NUEVO: Busca un vuelo por origen, destino y hora de salida
     * Para obtener la capacidad real del vuelo
     */
    @Query("SELECT v FROM Vuelo v WHERE v.origen = :origen AND v.destino = :destino " +
            "AND v.horaSalida = :horaSalida")
    Optional<Vuelo> findByOrigenDestinoHoraSalida(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("horaSalida") LocalDateTime horaSalida
    );

    /**
     * ✅ ALTERNATIVO: Busca vuelos por origen y destino (si hay múltiples vuelos por día)
     */
    List<Vuelo> findByOrigenAndDestino(String origen, String destino);
}