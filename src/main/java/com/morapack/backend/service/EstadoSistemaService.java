package com.morapack.backend.service;

import com.morapack.backend.repository.RutaAsignadaRepository;
import com.morapack.backend.repository.RutaTramoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para obtener el estado actual del sistema:
 * - Capacidad ocupada de vuelos
 * - Ocupación temporal de almacenes
 *
 * Este estado es necesario para que el algoritmo considere
 * las rutas ya asignadas y no sobrecargue vuelos/almacenes
 */
@Service
public class EstadoSistemaService {

    private final RutaTramoRepository tramoRepo;
    private final AlmacenOcupacionService almacenOcupacionService;

    public EstadoSistemaService(RutaTramoRepository tramoRepo,
                                AlmacenOcupacionService almacenOcupacionService) {
        this.tramoRepo = tramoRepo;
        this.almacenOcupacionService = almacenOcupacionService;
    }

    /**
     * Obtiene la capacidad ocupada de todos los vuelos desde una fecha
     *
     * @param desdeFecha fecha desde la cual buscar
     * @return Map con clave "ORIGEN-DESTINO-FECHA-HORA" y valor = cantidad ocupada
     */
    public Map<String, Integer> obtenerVuelosOcupados(LocalDate desdeFecha) {
        Map<String, Integer> vuelosOcupados = new HashMap<>();

        // Obtener todos los tramos desde la fecha indicada
        List<Object[]> tramos = tramoRepo.findCapacidadOcupadaPorVuelo(desdeFecha);

        for (Object[] row : tramos) {
            String origen = (String) row[0];
            String destino = (String) row[1];
            LocalDate fecha = (LocalDate) row[2];
            String horaSalida = (String) row[3];
            Integer cantidadTotal = ((Number) row[4]).intValue();

            // Crear clave única para el vuelo
            String claveVuelo = generarClaveVuelo(origen, destino, fecha, horaSalida);
            vuelosOcupados.put(claveVuelo, cantidadTotal);
        }

        System.out.println("📊 Vuelos ocupados cargados: " + vuelosOcupados.size());

        // ✅ NUEVO: Mostrar los vuelos cargados con más ocupación
        if (!vuelosOcupados.isEmpty()) {
            System.out.println("   Top vuelos más ocupados:");
            vuelosOcupados.entrySet().stream()
                    .filter(e -> e.getValue() > 100)  // Solo mostrar significativos
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .forEach(entry -> System.out.println(
                            "      " + entry.getKey() + " → " + entry.getValue() + " paquetes"));
        }

        return vuelosOcupados;
    }

    /**
     * Obtiene la ocupación temporal de un almacén en un momento específico
     */
    public int obtenerOcupacionTemporal(String aeropuertoCodigo, LocalDateTime momento) {
        return almacenOcupacionService.calcularOcupacionEn(aeropuertoCodigo, momento);
    }

    /**
     * Obtiene la ocupación máxima temporal en un periodo
     */
    public int obtenerOcupacionMaximaTemporal(String aeropuertoCodigo,
                                              LocalDateTime inicio,
                                              LocalDateTime fin) {
        return almacenOcupacionService.calcularOcupacionMaximaEnPeriodo(
                aeropuertoCodigo, inicio, fin);
    }

    /**
     * Verifica si hay espacio en un almacén durante un periodo
     * considerando la ocupación temporal
     */
    public boolean hayEspacioEnAlmacen(String aeropuertoCodigo,
                                       int capacidadMaxima,
                                       int capacidadFisicaActual,
                                       int cantidadNueva,
                                       LocalDateTime inicio,
                                       LocalDateTime fin) {
        return almacenOcupacionService.hayEspacioEnPeriodo(
                aeropuertoCodigo,
                capacidadMaxima,
                capacidadFisicaActual,
                cantidadNueva,
                inicio,
                fin);
    }

    /**
     * Genera una clave única para identificar un vuelo específico
     */
    public static String generarClaveVuelo(String origen, String destino,
                                           LocalDate fecha, String hora) {
        // Normalizar hora a HH:mm
        String horaNormalizada = normalizarHora(hora);
        return String.format("%s-%s-%s-%s", origen, destino, fecha, horaNormalizada);
    }

    /**
     * Normaliza una hora a formato HH:mm
     */
    private static String normalizarHora(String hora) {
        if (hora == null) return "00:00";
        hora = hora.trim();

        // Si ya está en formato HH:mm
        if (hora.matches("^\\d{2}:\\d{2}$")) {
            return hora;
        }

        // Si está en formato H:mm (ej: "8:05")
        if (hora.matches("^\\d{1}:\\d{2}$")) {
            return "0" + hora;
        }

        // Intentar parsear como LocalTime
        try {
            LocalTime time = LocalTime.parse(hora);
            return String.format("%02d:%02d", time.getHour(), time.getMinute());
        } catch (Exception e) {
            System.err.println("⚠️ Error normalizando hora: " + hora);
            return "00:00";
        }
    }

    /**
     * Imprime un resumen del estado actual del sistema
     */
    public void imprimirEstadoSistema(LocalDate desdeFecha, LocalDateTime momento) {
        System.out.println("📊 === ESTADO DEL SISTEMA ===");

        Map<String, Integer> vuelosOcupados = obtenerVuelosOcupados(desdeFecha);
        System.out.println("   Vuelos con ocupación: " + vuelosOcupados.size());

        if (!vuelosOcupados.isEmpty()) {
            System.out.println("   Ejemplos de vuelos ocupados:");
            vuelosOcupados.entrySet().stream()
                    .limit(5)
                    .forEach(entry -> System.out.println(
                            "      " + entry.getKey() + " → " + entry.getValue() + " paquetes"));
        }

        System.out.println("   Momento consulta almacenes: " + momento);
        almacenOcupacionService.imprimirResumenOcupaciones(momento);
    }
}