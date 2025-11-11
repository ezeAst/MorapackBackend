package com.morapack.backend.controller;

import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.repository.AeropuertoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aeropuertos")
@CrossOrigin(origins = "*")
public class AeropuertoController {

    @Autowired
    private AeropuertoRepository aeropuertoRepository;

    /**
     * Listar todos los aeropuertos (JPA)
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AeropuertoEntity>> listarTodos() {
        return ResponseEntity.ok(aeropuertoRepository.findAll());
    }

    /**
     * Buscar aeropuerto por código
     */
    @GetMapping("/{codigo}")
    @Transactional(readOnly = true)
    public ResponseEntity<AeropuertoEntity> buscarPorCodigo(@PathVariable String codigo) {
        return aeropuertoRepository.findByCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Contar aeropuertos
     */
    @GetMapping("/count")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> contarAeropuertos() {
        long total = aeropuertoRepository.count();
        return ResponseEntity.ok(Map.of("total", total));
    }

    /**
     * Crear aeropuerto (JPA)
     */
    @PostMapping
    @Transactional
    public ResponseEntity<AeropuertoEntity> crear(@RequestBody AeropuertoEntity body) {
        AeropuertoEntity creado = aeropuertoRepository.save(body);
        return ResponseEntity.ok(creado);
    }

    /**
     * Actualizar aeropuerto (JPA)
     */
    @PutMapping("/id/{id}")
    @Transactional
    public ResponseEntity<AeropuertoEntity> actualizar(@PathVariable Long id, @RequestBody AeropuertoEntity body) {
        return aeropuertoRepository.findById(id)
                .map(existente -> {
                    body.setId(id);
                    AeropuertoEntity actualizado = aeropuertoRepository.save(body);
                    return ResponseEntity.ok(actualizado);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Eliminar aeropuerto (JPA)
     */
    @DeleteMapping("/id/{id}")
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!aeropuertoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        aeropuertoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filtrar aeropuertos por codigo / pais / continente (en memoria por simplicidad)
     */
    @GetMapping("/filtrar")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AeropuertoEntity>> filtrar(
        @RequestParam(required = false) String codigo,
        @RequestParam(required = false) String pais,
        @RequestParam(required = false) String continente
    ) {
    List<AeropuertoEntity> lista = aeropuertoRepository.findAll().stream()
        .filter(a -> codigo == null || a.getCodigo().equalsIgnoreCase(codigo))
        .filter(a -> pais == null || a.getPais().equalsIgnoreCase(pais))
        .filter(a -> continente == null || a.getContinente().equalsIgnoreCase(continente))
        .toList();
    return ResponseEntity.ok(lista);
    }

}