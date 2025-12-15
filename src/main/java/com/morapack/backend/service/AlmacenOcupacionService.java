package com.morapack.backend.service;

import com.morapack.backend.entity.AlmacenOcupacionTemporal;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.AlmacenOcupacionTemporalRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para gestionar la ocupación temporal de almacenes
 *
 * Implementa el modelo de negocio:
 * - Los paquetes ocupan espacio en el almacén por 2 horas
 * - Después de 2 horas, "desaparecen" (cliente los recoge)
 */
@Service
public class AlmacenOcupacionService {

    private final AlmacenOcupacionTemporalRepository repository;

    public AlmacenOcupacionService(AlmacenOcupacionTemporalRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra las ocupaciones temporales de una ruta completa
     *
     * Para cada tramo de la ruta, calcula cuándo llegará el paquete
     * y registra que ocupará espacio por 2 horas
     *
     * @param ruta Ruta asignada al pedido
     */
    @Transactional
    public void registrarOcupacionesDeRuta(RutaAsignada ruta) {
        if (ruta == null || ruta.getTramos() == null || ruta.getTramos().isEmpty()) {
            return;
        }

        Long pedidoId = ruta.getPedidoId();
        Integer cantidad = ruta.getCantidad();

        List<AlmacenOcupacionTemporal> ocupaciones = new ArrayList<>();

        // Para cada tramo, registrar ocupación en el aeropuerto de destino
        for (RutaTramo tramo : ruta.getTramos()) {
            try {
                // Calcular hora de llegada
                LocalDateTime horaLlegada = LocalDateTime.of(
                        tramo.getFecha(),
                        LocalTime.parse(tramo.getHoraLlegada())
                );

                // Hora de fin = llegada + 2 horas (modelo de negocio)
                LocalDateTime horaFin = horaLlegada.plusHours(2);

                // Crear registro de ocupación
                AlmacenOcupacionTemporal ocupacion = new AlmacenOcupacionTemporal(
                        tramo.getDestino(),
                        pedidoId,
                        cantidad,
                        horaLlegada,
                        horaFin
                );

                ocupaciones.add(ocupacion);

            } catch (Exception e) {
                System.err.println("❌ Error al registrar ocupación del tramo: " + e.getMessage());
                System.err.println("   Tramo: " + tramo.getOrigen() + " → " + tramo.getDestino());
            }
        }

        // Guardar todas las ocupaciones de una vez
        if (!ocupaciones.isEmpty()) {
            repository.saveAll(ocupaciones);
            System.out.println("📊 Registradas " + ocupaciones.size() +
                    " ocupaciones temporales para pedido " + pedidoId);
        }
    }

    /**
     * Registra ocupaciones para múltiples rutas
     * (optimizado para batch)
     */
    @Transactional
    public void registrarOcupacionesDeRutas(List<RutaAsignada> rutas) {
        if (rutas == null || rutas.isEmpty()) {
            return;
        }

        int totalOcupaciones = 0;

        for (RutaAsignada ruta : rutas) {
            registrarOcupacionesDeRuta(ruta);
            totalOcupaciones += (ruta.getTramos() != null ? ruta.getTramos().size() : 0);
        }

        System.out.println("✅ Registradas " + totalOcupaciones +
                " ocupaciones temporales para " + rutas.size() + " rutas");
    }

    /**
     * Elimina la ocupación de un pedido en un aeropuerto específico
     *
     * Se llama cuando el paquete sale del almacén (despega)
     */
    @Transactional
    public void eliminarOcupacion(Long pedidoId, String aeropuertoCodigo) {
        repository.deleteByPedidoIdAndAeropuerto(pedidoId, aeropuertoCodigo);
        System.out.println("🗑️ Eliminada ocupación del pedido " + pedidoId +
                " en " + aeropuertoCodigo);
    }

    /**
     * Elimina todas las ocupaciones de un pedido
     *
     * Se llama cuando el pedido se cancela o cambia de estado
     */
    @Transactional
    public void eliminarOcupacionesDePedido(Long pedidoId) {
        repository.deleteByPedidoId(pedidoId);
        System.out.println("🗑️ Eliminadas todas las ocupaciones del pedido " + pedidoId);
    }

    /**
     * Limpia todas las ocupaciones que ya vencieron
     *
     * Debe ejecutarse periódicamente (ej: cada vez que corre OperacionesDiaDia)
     * para mantener la tabla limpia
     */
    @Transactional
    public int limpiarOcupacionesVencidas(LocalDateTime ahora) {
        // Contar primero para log
        long vencidas = repository.countOcupacionesVencidas(ahora);

        if (vencidas > 0) {
            int eliminadas = repository.deleteOcupacionesVencidas(ahora);
            System.out.println("🧹 Limpiadas " + eliminadas + " ocupaciones vencidas " +
                    "(anteriores a " + ahora + ")");
            return eliminadas;
        }

        return 0;
    }

    /**
     * Calcula la ocupación de un aeropuerto en un momento específico
     */
    public int calcularOcupacionEn(String aeropuertoCodigo, LocalDateTime momento) {
        Integer ocupacion = repository.calcularOcupacionEn(aeropuertoCodigo, momento);
        return (ocupacion != null) ? ocupacion : 0;
    }

    /**
     * Calcula la ocupación máxima de un aeropuerto en un periodo
     *
     * Útil para validar si hay espacio durante todo el periodo de 2h
     */
    public int calcularOcupacionMaximaEnPeriodo(String aeropuertoCodigo,
                                                LocalDateTime inicio,
                                                LocalDateTime fin) {
        Integer ocupacion = repository.calcularOcupacionMaximaEnPeriodo(
                aeropuertoCodigo, inicio, fin
        );
        return (ocupacion != null) ? ocupacion : 0;
    }

    /**
     * Verifica si hay espacio disponible en un aeropuerto durante un periodo
     *
     * @param aeropuertoCodigo Código del aeropuerto
     * @param capacidadMaxima Capacidad máxima del aeropuerto
     * @param capacidadActual Capacidad actualmente ocupada (física)
     * @param cantidadNueva Cantidad de paquetes que se quiere agregar
     * @param inicio Inicio del periodo (hora de llegada)
     * @param fin Fin del periodo (inicio + 2h)
     * @return true si hay espacio disponible durante todo el periodo
     */
    public boolean hayEspacioEnPeriodo(String aeropuertoCodigo,
                                       int capacidadMaxima,
                                       int capacidadActual,
                                       int cantidadNueva,
                                       LocalDateTime inicio,
                                       LocalDateTime fin) {
        // Calcular ocupación temporal máxima en el periodo
        int ocupacionTemporal = calcularOcupacionMaximaEnPeriodo(aeropuertoCodigo, inicio, fin);

        // Total = física + temporal + nueva
        int ocupacionTotal = capacidadActual + ocupacionTemporal + cantidadNueva;

        boolean hayEspacio = ocupacionTotal <= capacidadMaxima;

        if (!hayEspacio) {
            System.out.println("⚠️ No hay espacio en " + aeropuertoCodigo +
                    " durante " + inicio + " - " + fin);
            System.out.println("   Capacidad: " + capacidadMaxima);
            System.out.println("   Ocupado físico: " + capacidadActual);
            System.out.println("   Ocupado temporal: " + ocupacionTemporal);
            System.out.println("   Nueva cantidad: " + cantidadNueva);
            System.out.println("   Total: " + ocupacionTotal);
        }

        return hayEspacio;
    }

    /**
     * Obtiene un resumen de las ocupaciones actuales
     * (útil para debugging)
     */
    public void imprimirResumenOcupaciones(LocalDateTime momento) {
        List<AlmacenOcupacionTemporal> activas = repository.findOcupacionesActivasEn(momento);

        System.out.println("📊 === OCUPACIONES TEMPORALES ACTIVAS ===");
        System.out.println("   Momento: " + momento);
        System.out.println("   Total: " + activas.size() + " registros");

        // Agrupar por aeropuerto
        activas.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AlmacenOcupacionTemporal::getAeropuertoCodigo,
                        java.util.stream.Collectors.summingInt(AlmacenOcupacionTemporal::getCantidad)
                ))
                .forEach((aeropuerto, cantidad) ->
                        System.out.println("   " + aeropuerto + ": " + cantidad + " paquetes")
                );
    }
}