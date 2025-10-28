package com.morapack.backend.controller;


import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

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

    @PostMapping("/importar")
    public ResponseEntity<List<Pedido>> importar(@RequestBody List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // nos aseguramos de que no se envíe id (para evitar conflictos)
        List<Pedido> normalizados = pedidos.stream().peek(p -> p.setId(null)).collect(Collectors.toList());
        List<Pedido> guardados = pedidoRepository.saveAll(normalizados);
        return ResponseEntity.ok(guardados);
    }

}
