package com.morapack.backend.controller;


import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import com.morapack.backend.service.VueloService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vuelos")
@CrossOrigin(origins = "*")
public class VueloController {
    private final VueloService vueloService;

    public VueloController(VueloService vueloService) {
        this.vueloService = vueloService;
    }

    // 1) Subir archivo de vuelos (el boton del front)
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadVuelos(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("mensaje", "El archivo está vacío"));
        }

        int cantidad = vueloService.cargarDesdeArchivoVuelos(file);

        Map<String, Object> body = new HashMap<>();
        body.put("mensaje", "Archivo de vuelos procesado correctamente");
        body.put("registrosCargados", cantidad);

        return ResponseEntity.ok(body);
    }
/*
    // 2) Listar todos los vuelos (para tu página Vuelos)
    @GetMapping
    public List<Vuelo> listarVuelos() {
        return vueloService.listarTodos();
    }

    // 3) Obtener un vuelo por id (por si luego quieres ver detalle)
    @GetMapping("/{id}")
    public ResponseEntity<Vuelo> obtenerVuelo(@PathVariable Long id) {
        return vueloService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 4) (Opcional) Eliminar un vuelo
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVuelo(@PathVariable Long id) {
        boolean eliminado = vueloService.eliminarPorId(id);
        if (eliminado) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    */
}
