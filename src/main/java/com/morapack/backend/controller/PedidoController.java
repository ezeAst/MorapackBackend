package com.morapack.backend.controller;


import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.service.Pedidobatchservice;

import com.morapack.backend.service.Pedidobatchservice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private Pedidobatchservice pedidobatchservice;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(@RequestBody Pedido body) {
        // ignoramos cualquier id que llegue del front
        body.setId(null);
        Pedido guardado = pedidoRepository.save(body);
        return ResponseEntity.ok(guardado);
    }

    @PostMapping("/importarTxt")
    @Transactional
    public ResponseEntity<Map<String, Object>> importar(@RequestBody List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        long startTime = System.currentTimeMillis();

        // Obtener el último ID de una vez
        Long ultimoId = pedidoRepository.findMaxId().orElse(0L);

        // ✅ USAR JDBC BATCH INSERT (mucho más rápido que JPA)
        int insertados = pedidobatchservice.insertarPedidosEnLote(pedidos, ultimoId);

        long endTime = System.currentTimeMillis();
        System.out.println("⚡ Tiempo de inserción: " + (endTime - startTime) + "ms para " + insertados + " pedidos");

        Map<String, Object> body = new HashMap<>();
        body.put("insertados", insertados);
        body.put("duplicados", 0);
        body.put("errores", 0);

        return ResponseEntity.ok(body);
    }

}