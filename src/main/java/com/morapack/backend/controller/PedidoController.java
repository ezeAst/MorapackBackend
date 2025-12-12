package com.morapack.backend.controller;


import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.service.PedidoService;
import com.morapack.backend.service.Pedidobatchservice;

import com.morapack.backend.service.Pedidobatchservice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    private final PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private Pedidobatchservice pedidobatchservice;

    @PersistenceContext
    private EntityManager entityManager;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    /**
     * GET /api/pedidos/almacen/{codigo}
     * Lista pedidos que están actualmente en un almacén específico
     */
    @GetMapping("/almacen/{codigo}")
    public ResponseEntity<List<Map<String, Object>>> listarPorAlmacen(@PathVariable String codigo) {
        List<Pedido> pedidos = pedidoRepository.findPedidosEnAlmacen(codigo);
        
        List<Map<String, Object>> response = pedidos.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("aeropuertoDestino", p.getAeropuertoDestino());
                    map.put("cantidad", p.getCantidad());
                    map.put("estado", p.getEstado().toString());
                    map.put("tramoActual", p.getTramoActual());
                    map.put("fecha", String.format("%04d-%02d-%02d", p.getAnho(), p.getMes(), p.getDia()));
                    return map;
                })
                .toList();
        
        return ResponseEntity.ok(response);
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


        int insertados = pedidobatchservice.insertarPedidosEnLote(pedidos, ultimoId);

        long endTime = System.currentTimeMillis();
        System.out.println("⚡ Tiempo de inserción: " + (endTime - startTime) + "ms para " + insertados + " pedidos");

        Map<String, Object> body = new HashMap<>();
        body.put("insertados", insertados);
        body.put("duplicados", 0);
        body.put("errores", 0);

        return ResponseEntity.ok(body);
    }

    @PostMapping("/insertar")
    public ResponseEntity<?> insertarPedido(@RequestBody Map<String, Object> payload) {

        if (!payload.containsKey("id_cliente") ||
                !payload.containsKey("cantidad") ||
                !payload.containsKey("aeropuerto_destino") ||
                !payload.containsKey("created_at")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "Faltan campos: id_cliente, cantidad, aeropuerto_destino, created_at"
            ));
        }

        Pedido creado = pedidoService.crearPedidoDesdePayload(payload);

        return ResponseEntity.ok(Map.of(
                "id", creado.getId(),
                "mensaje", "Pedido creado correctamente"
        ));
    }

    @GetMapping("/recientes")
    public ResponseEntity<?> recientes(@RequestParam(defaultValue = "5") int limit) {
        if (limit <= 0) limit = 5;
        if (limit > 50) limit = 50;

        List<Object[]> rows = pedidoRepository.findRecientesSimple(limit);

        List<Map<String, Object>> resp = rows.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", ((Number) r[0]).longValue());
                    m.put("id_cliente", (String) r[1]);
                    m.put("aeropuerto_destino", (String) r[2]);
                    m.put("cantidad", ((Number) r[3]).intValue());
                    return m;
                })
                .collect(Collectors.toList()); // ✅ Java 8/11 compatible

        return ResponseEntity.ok(resp);
    }

}