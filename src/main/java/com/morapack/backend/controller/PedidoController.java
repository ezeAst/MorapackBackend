package com.morapack.backend.controller;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.service.Pedidobatchservice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    // Cache del último ID para evitar SELECT MAX() en cada request
    private AtomicLong cachedMaxId = new AtomicLong(-1);

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
        body.setId(null);
        Pedido guardado = pedidoRepository.save(body);
        return ResponseEntity.ok(guardado);
    }

    /**
     * ENDPOINT OPTIMIZADO - Evita SELECT MAX() en cada llamada
     */
    @PostMapping("/importarTxt")
    @Transactional
    public ResponseEntity<Map<String, Object>> importar(@RequestBody List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        long startTime = System.currentTimeMillis();

        // Obtener último ID solo si no está cacheado
        long ultimoId;
        if (cachedMaxId.get() == -1) {
            ultimoId = pedidoRepository.findMaxId().orElse(0L);
            System.out.println("🔍 SELECT MAX(id) inicial: " + ultimoId);
        } else {
            ultimoId = cachedMaxId.get();
            System.out.println("⚡ Usando ID cacheado: " + ultimoId);
        }

        int insertados = pedidobatchservice.insertarPedidosEnLote(pedidos, ultimoId);

        // Actualizar el cache con el nuevo último ID
        cachedMaxId.set(ultimoId + insertados);

        long endTime = System.currentTimeMillis();
        System.out.println("⚡ Tiempo de inserción: " + (endTime - startTime) + "ms para " + insertados + " pedidos");

        Map<String, Object> body = new HashMap<>();
        body.put("insertados", insertados);
        body.put("duplicados", 0);
        body.put("errores", 0);

        return ResponseEntity.ok(body);
    }

    /**
     * NUEVO ENDPOINT - Para resetear el cache cuando limpias la tabla
     */
    @PostMapping("/resetCache")
    public ResponseEntity<Map<String, String>> resetCache() {
        cachedMaxId.set(-1);
        System.out.println("🔄 Cache de ID reseteado");
        return ResponseEntity.ok(Map.of("status", "Cache reseteado"));
    }

    /**
     * NUEVO ENDPOINT - Para importar TODO de una vez (sin chunks desde frontend)
     */
    @PostMapping("/importarTxtCompleto")
    @Transactional
    public ResponseEntity<Map<String, Object>> importarCompleto(@RequestBody List<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        System.out.println("📦 Importación completa iniciada: " + pedidos.size() + " pedidos");
        long startTime = System.currentTimeMillis();

        // Obtener último ID
        Long ultimoId = pedidoRepository.findMaxId().orElse(0L);
        System.out.println("🔍 Último ID en BD: " + ultimoId);

        // Insertar todo de una vez
        int insertados = pedidobatchservice.insertarPedidosEnLote(pedidos, ultimoId);

        long endTime = System.currentTimeMillis();
        long tiempoTotal = endTime - startTime;
        double pedidosPorSegundo = (insertados * 1000.0) / tiempoTotal;

        System.out.println("✅ IMPORTACIÓN COMPLETA:");
        System.out.println("   - Pedidos insertados: " + insertados);
        System.out.println("   - Tiempo total: " + tiempoTotal + "ms");
        System.out.println("   - Velocidad: " + String.format("%.0f", pedidosPorSegundo) + " pedidos/seg");

        Map<String, Object> response = new HashMap<>();
        response.put("insertados", insertados);
        response.put("tiempoMs", tiempoTotal);
        response.put("pedidosPorSegundo", (int) pedidosPorSegundo);
        response.put("duplicados", 0);
        response.put("errores", 0);

        return ResponseEntity.ok(response);
    }
}