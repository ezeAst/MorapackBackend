package com.morapack.backend.repository;

import com.morapack.algoritmologistica.algorithm.models.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Encuentra pedidos que caen dentro de una semana específica
     * @param mesInicio Mes de inicio
     * @param diaInicio Día de inicio
     * @param mesFin Mes de fin
     * @param diaFin Día de fin
     * @return Lista de pedidos en ese rango
     */
    @Query("SELECT p FROM Pedido p WHERE " +
            "(p.mes > :mesInicio OR (p.mes = :mesInicio AND p.dia >= :diaInicio)) AND " +
            "(p.mes < :mesFin OR (p.mes = :mesFin AND p.dia < :diaFin)) " +
            "ORDER BY p.mes, p.dia, p.hora, p.minuto")
    List<Pedido> findPedidosByWeek(
            @Param("mesInicio") int mesInicio,
            @Param("diaInicio") int diaInicio,
            @Param("mesFin") int mesFin,
            @Param("diaFin") int diaFin
    );
}