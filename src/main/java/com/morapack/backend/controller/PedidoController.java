package com.morapack.backend.controller;


import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

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
    public ResponseEntity<Map<String, Object>> importar(@RequestBody List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        int insertados = 0;
        int duplicados = 0;
        int errores = 0;

        for (Pedido p : pedidos) {
            try {
                // 1) debe venir con ID (del archivo)
                if (p.getId() == null) {
                    errores++;
                    continue;
                }

                // 2) si ya existe ese ID, lo saltamos
                if (pedidoRepository.existsById(p.getId())) {
                    duplicados++;
                    continue;
                }

                // 3) estado inicial
                p.setEstado(EstadoPedido.NO_ASIGNADO);

                // 4) guardar con ese ID
                pedidoRepository.save(p);
                insertados++;

            } catch (Exception e) {
                errores++;
                // log.warn("Error al importar pedido {}: {}", p.getId(), e.getMessage());
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("insertados", insertados);
        body.put("duplicados", duplicados);
        body.put("errores", errores);

        return ResponseEntity.ok(body);
    }

}
