package com.morapack.backend.controller;

import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.repository.AeropuertoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aeropuertos")
@CrossOrigin(origins = "*")
public class AeropuertoController {

    @Autowired
    private AeropuertoRepository aeropuertoRepository;

    /**
     * Obtener todos los aeropuertos desde la BD
     */
    @GetMapping
    public ResponseEntity<List<AeropuertoEntity>> obtenerTodos() {
        List<AeropuertoEntity> aeropuertos = aeropuertoRepository.findAll();
        return ResponseEntity.ok(aeropuertos);
    }

    /**
     * Buscar aeropuerto por código
     */
    @GetMapping("/{codigo}")
    public ResponseEntity<AeropuertoEntity> buscarPorCodigo(@PathVariable String codigo) {
        return aeropuertoRepository.findByCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Contar aeropuertos
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> contarAeropuertos() {
        long total = aeropuertoRepository.count();
        return ResponseEntity.ok(Map.of("total", total));
    }

}