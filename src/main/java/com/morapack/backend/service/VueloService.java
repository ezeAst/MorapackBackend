package com.morapack.backend.service;

import com.morapack.backend.repository.VueloRepository;
import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@Service
public class VueloService {

    private final VueloRepository vueloRepository;
    private final JdbcTemplate jdbcTemplate;

    public VueloService(VueloRepository vueloRepository, JdbcTemplate jdbcTemplate) {
        this.vueloRepository = vueloRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int cargarDesdeArchivoVuelos(MultipartFile file) {
        long inicio = System.currentTimeMillis();
        List<Object[]> batchArgs = new ArrayList<>();
        int contador = 0;

        try (
                InputStream is = file.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader reader = new BufferedReader(isr)
        ) {
            String linea;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalDate fechaAncla = LocalDate.of(2025, 1, 1);

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                // Formato: ORIG-DEST-HH:MM-HH:MM-CAP
                String[] partes = linea.split("-");
                if (partes.length != 5) {
                    continue;
                }

                String origen = partes[0].trim();
                String destino = partes[1].trim();
                String horaSalidaStr = partes[2].trim();
                String horaLlegadaStr = partes[3].trim();

                int capacidad;
                try {
                    capacidad = Integer.parseInt(partes[4].trim());
                } catch (NumberFormatException ex) {
                    // capacidad mal formada → saltar línea
                    continue;
                }

                LocalTime salida = LocalTime.parse(horaSalidaStr, formatter);
                LocalTime llegada = LocalTime.parse(horaLlegadaStr, formatter);

                LocalDateTime horaSalida = fechaAncla.atTime(salida);
                LocalDateTime horaLlegada = fechaAncla.atTime(llegada);

                // Orden debe coincidir con el INSERT
                batchArgs.add(new Object[] {
                        origen,
                        destino,
                        horaSalida,
                        horaLlegada,
                        capacidad,
                        0 // capacidad_actual
                });

                contador++;
            }

        } catch (IOException e) {
            throw new RuntimeException("Error al leer archivo de vuelos", e);
        }

        if (batchArgs.isEmpty()) {
            return 0;
        }

        // INSERT batch (JDBC), mucho más rápido que 2000 saves JPA
        String sql = """
        INSERT INTO vuelo (origen, destino, hora_salida, hora_llegada, capacidad, capacidad_actual)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, batchArgs);


        long fin = System.currentTimeMillis();
        System.out.println("Cargados " + contador + " vuelos en " + (fin - inicio) + " ms");
        return contador;
    }









}
