package com.morapack.backend.controller;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.service.PedidoOperacionesCargaService;
import com.morapack.backend.service.PedidoService;
import com.morapack.backend.service.Pedidobatchservice;

import com.morapack.backend.service.TiempoSimuladoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    public PedidoController(PedidoService pedidoService, TiempoSimuladoService tiempoSimuladoService) {
        this.pedidoService = pedidoService;
        this.tiempoSimuladoService = tiempoSimuladoService;
    }
    // Cache del último ID para evitar SELECT MAX() en cada request
    private AtomicLong cachedMaxId = new AtomicLong(-1);

    private final TiempoSimuladoService tiempoSimuladoService;

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

    @PostMapping("/insertar")
    public ResponseEntity<?> insertarPedido(@RequestBody Map<String, Object> payload) {

        if (!payload.containsKey("id_cliente") ||
                !payload.containsKey("cantidad") ||
                !payload.containsKey("aeropuerto_destino")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "Faltan campos: id_cliente, cantidad, aeropuerto_destino"
            ));
        }

        // ✅ NUEVO: Usar tiempo simulado en lugar de created_at del frontend
        LocalDateTime tiempoSimulado = tiempoSimuladoService.obtenerTiempoActual();

        // Sobrescribir created_at con tiempo simulado
        payload.put("created_at", tiempoSimulado.toString());

        // También establecer los campos de fecha individuales
        payload.put("dia", tiempoSimulado.getDayOfMonth());
        payload.put("mes", tiempoSimulado.getMonthValue());
        payload.put("anho", tiempoSimulado.getYear());
        payload.put("hora", tiempoSimulado.getHour());
        payload.put("minuto", tiempoSimulado.getMinute());

        System.out.println("📦 Insertando pedido manual con tiempo simulado: " + tiempoSimulado);

        Pedido creado = pedidoService.crearPedidoDesdePayload(payload);

        return ResponseEntity.ok(Map.of(
                "id", creado.getId(),
                "mensaje", "Pedido creado con fecha simulada: " + tiempoSimulado,
                "fechaCreacion", tiempoSimulado.toString()
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

    /**
     * ✅ NUEVO ENDPOINT - Importar pedidos para operaciones día a día
     *
     * Los pedidos con ## en día/hora/minuto usan el tiempo actual de la simulación
     *
     * POST /api/pedidos/importarOperaciones
     * Body: Lista de PedidoOperacionDTO
     */
    @Autowired
    private PedidoOperacionesCargaService pedidoOperacionesCargaService;

    @PostMapping("/importarOperaciones")
    @Transactional
    public ResponseEntity<Map<String, Object>> importarPedidosOperaciones(
            @RequestBody List<PedidoOperacionesCargaService.PedidoOperacionDTO> pedidos) {

        if (pedidos == null || pedidos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se recibieron pedidos"
            ));
        }

        try {
            System.out.println("📦 Importando " + pedidos.size() + " pedidos para operaciones día a día");

            PedidoOperacionesCargaService.ImportResult resultado =
                    pedidoOperacionesCargaService.importarPedidosOperaciones(pedidos);

            Map<String, Object> response = new HashMap<>();
            response.put("pedidosInsertados", resultado.getPedidosInsertados());
            response.put("tiempoMs", resultado.getTiempoMs());
            response.put("conTiempoSimulado", resultado.getConTiempoSimulado());
            response.put("conTiempoArchivo", resultado.getConTiempoArchivo());
            response.put("tiempoSimuladoUsado", resultado.getTiempoSimuladoUsado());
            response.put("mensaje", "✅ Pedidos importados exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error importando pedidos: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}