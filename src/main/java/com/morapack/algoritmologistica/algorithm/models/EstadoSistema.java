package com.morapack.algoritmologistica.algorithm.models;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO que contiene el estado actual del sistema para que el algoritmo
 * considere las restricciones de capacidad ya ocupadas
 */
public class EstadoSistema {

    /**
     * Mapa de vuelos ocupados
     * Clave: "ORIGEN-DESTINO-FECHA-HORA" (ej: "LIM-AMS-2024-12-15-14:30")
     * Valor: cantidad de paquetes ya asignados a ese vuelo
     */
    private Map<String, Integer> vuelosOcupados;

    /**
     * Referencia al servicio de ocupación de almacenes
     * El algoritmo puede consultar ocupación temporal en tiempo real
     */
    private transient AlmacenOcupacionValidator almacenValidator;

    public EstadoSistema() {
        this.vuelosOcupados = new HashMap<>();
    }

    public EstadoSistema(Map<String, Integer> vuelosOcupados) {
        this.vuelosOcupados = vuelosOcupados != null ? vuelosOcupados : new HashMap<>();
    }

    /**
     * Obtiene la capacidad ocupada de un vuelo específico
     *
     * @param claveVuelo clave del vuelo (formato: "ORIGEN-DESTINO-FECHA-HORA")
     * @return cantidad ocupada, 0 si no hay información
     */
    public int getCapacidadOcupada(String claveVuelo) {
        return vuelosOcupados.getOrDefault(claveVuelo, 0);
    }

    /**
     * Verifica si un vuelo tiene capacidad disponible
     *
     * @param claveVuelo clave del vuelo
     * @param capacidadTotal capacidad total del vuelo
     * @param cantidadNueva cantidad que se quiere agregar
     * @return true si hay espacio disponible
     */
    public boolean hayCapacidadDisponible(String claveVuelo, int capacidadTotal, int cantidadNueva) {
        int ocupada = getCapacidadOcupada(claveVuelo);
        int disponible = capacidadTotal - ocupada;
        return disponible >= cantidadNueva;
    }

    /**
     * Reserva capacidad en un vuelo (actualiza el estado local)
     *
     * IMPORTANTE: Esto solo actualiza el estado en memoria durante la ejecución
     * del algoritmo. NO persiste en BD.
     */
    public void reservarCapacidad(String claveVuelo, int cantidad) {
        int actual = vuelosOcupados.getOrDefault(claveVuelo, 0);
        vuelosOcupados.put(claveVuelo, actual + cantidad);
    }

    /**
     * Libera capacidad de un vuelo (útil para backtracking)
     */
    public void liberarCapacidad(String claveVuelo, int cantidad) {
        int actual = vuelosOcupados.getOrDefault(claveVuelo, 0);
        int nuevo = Math.max(0, actual - cantidad);

        if (nuevo == 0) {
            vuelosOcupados.remove(claveVuelo);
        } else {
            vuelosOcupados.put(claveVuelo, nuevo);
        }
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
        if (almacenValidator == null) {
            // Si no hay validator, solo verificar capacidad física
            return (capacidadFisicaActual + cantidadNueva) <= capacidadMaxima;
        }

        return almacenValidator.hayEspacioEnPeriodo(
                aeropuertoCodigo,
                capacidadMaxima,
                capacidadFisicaActual,
                cantidadNueva,
                inicio,
                fin
        );
    }

    // Getters y setters
    public Map<String, Integer> getVuelosOcupados() {
        return vuelosOcupados;
    }

    public void setVuelosOcupados(Map<String, Integer> vuelosOcupados) {
        this.vuelosOcupados = vuelosOcupados;
    }

    public AlmacenOcupacionValidator getAlmacenValidator() {
        return almacenValidator;
    }

    public void setAlmacenValidator(AlmacenOcupacionValidator almacenValidator) {
        this.almacenValidator = almacenValidator;
    }

    /**
     * Crea una copia del estado para uso en algoritmos paralelos
     */
    public EstadoSistema clonar() {
        EstadoSistema copia = new EstadoSistema();
        copia.vuelosOcupados = new HashMap<>(this.vuelosOcupados);
        copia.almacenValidator = this.almacenValidator; // Compartido (thread-safe read)
        return copia;
    }

    /**
     * Imprime estadísticas del estado
     */
    public void imprimirEstadisticas() {
        System.out.println("📊 Estado del Sistema:");
        System.out.println("   Vuelos con ocupación: " + vuelosOcupados.size());

        if (!vuelosOcupados.isEmpty()) {
            int totalPaquetes = vuelosOcupados.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            System.out.println("   Total paquetes en vuelos: " + totalPaquetes);

            System.out.println("   Top 5 vuelos más ocupados:");
            vuelosOcupados.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(5)
                    .forEach(entry -> System.out.println(
                            "      " + entry.getKey() + " → " + entry.getValue() + " paquetes"));
        }

        System.out.println("   Validator de almacenes: " +
                (almacenValidator != null ? "✅ Activo" : "❌ No disponible"));
    }
}

