package com.morapack.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad que representa un vuelo cancelado
 *
 * Formato de entrada: SPIM-SKBO-04:35-08:51-0340
 * - origen: SPIM
 * - destino: SKBO
 * - horaSalidaLocal: 04:35 (en zona horaria del origen)
 * - horaLlegadaLocal: 08:51 (en zona horaria del destino)
 * - capacidadMaxima: 340
 */
@Entity
@Table(name = "vuelos_cancelados",
        indexes = {
                @Index(name = "idx_vuelo_cancelado", columnList = "origen,destino,fecha,hora_salida_local"),
                @Index(name = "idx_fecha_cancelacion", columnList = "fecha")
        })
public class VueloCancelado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origen", nullable = false, length = 10)
    private String origen;

    @Column(name = "destino", nullable = false, length = 10)
    private String destino;

    @Column(name = "hora_salida_local", nullable = false, length = 5)
    private String horaSalidaLocal; // HH:mm en zona horaria del origen

    @Column(name = "hora_llegada_local", nullable = false, length = 5)
    private String horaLlegadaLocal; // HH:mm en zona horaria del destino

    @Column(name = "capacidad_maxima", nullable = false)
    private Integer capacidadMaxima;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha; // Fecha del vuelo cancelado

    @Column(name = "cancelado_en", nullable = false)
    private LocalDateTime canceladoEn; // Timestamp de cuándo se registró la cancelación

    @Column(name = "activo", nullable = false)
    private Boolean activo = true; // Para soft-delete si es necesario

    // ============================================
    // CONSTRUCTORES
    // ============================================

    public VueloCancelado() {
        this.canceladoEn = LocalDateTime.now();
        this.activo = true;
    }

    public VueloCancelado(String origen, String destino, String horaSalidaLocal,
                          String horaLlegadaLocal, Integer capacidadMaxima, LocalDate fecha) {
        this.origen = origen;
        this.destino = destino;
        this.horaSalidaLocal = horaSalidaLocal;
        this.horaLlegadaLocal = horaLlegadaLocal;
        this.capacidadMaxima = capacidadMaxima;
        this.fecha = fecha;
        this.canceladoEn = LocalDateTime.now();
        this.activo = true;
    }

    // ============================================
    // MÉTODOS DE NEGOCIO
    // ============================================

    /**
     * Genera la clave única que identifica este vuelo
     * Formato: ORIGEN-DESTINO-FECHA-HH:mm
     * Ejemplo: SPIM-SKBO-2025-01-15-04:35
     */
    public String getClave() {
        return String.format("%s-%s-%s-%s",
                origen, destino, fecha.toString(), horaSalidaLocal);
    }

    /**
     * Verifica si este vuelo cancelado coincide con un vuelo dado
     * (comparación por origen, destino, fecha y hora de salida)
     */
    public boolean coincideCon(String origenVuelo, String destinoVuelo,
                               LocalDate fechaVuelo, String horaSalidaVuelo) {
        return this.origen.equals(origenVuelo) &&
                this.destino.equals(destinoVuelo) &&
                this.fecha.equals(fechaVuelo) &&
                this.horaSalidaLocal.equals(horaSalidaVuelo);
    }

    // ============================================
    // GETTERS Y SETTERS
    // ============================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getHoraSalidaLocal() {
        return horaSalidaLocal;
    }

    public void setHoraSalidaLocal(String horaSalidaLocal) {
        this.horaSalidaLocal = horaSalidaLocal;
    }

    public String getHoraLlegadaLocal() {
        return horaLlegadaLocal;
    }

    public void setHoraLlegadaLocal(String horaLlegadaLocal) {
        this.horaLlegadaLocal = horaLlegadaLocal;
    }

    public Integer getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(Integer capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalDateTime getCanceladoEn() {
        return canceladoEn;
    }

    public void setCanceladoEn(LocalDateTime canceladoEn) {
        this.canceladoEn = canceladoEn;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "VueloCancelado{" +
                "origen='" + origen + '\'' +
                ", destino='" + destino + '\'' +
                ", fecha=" + fecha +
                ", horaSalida='" + horaSalidaLocal + '\'' +
                ", canceladoEn=" + canceladoEn +
                '}';
    }
}