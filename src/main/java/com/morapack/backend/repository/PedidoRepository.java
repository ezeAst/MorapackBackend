package com.morapack.backend.repository;

import com.morapack.algoritmologistica.algorithm.models.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

}
