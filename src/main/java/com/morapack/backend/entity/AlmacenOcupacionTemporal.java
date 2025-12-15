package com.morapack.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad que representa la ocupación temporal de un almacén
 *
 * Modelo de negocio:
 * - Cuando un paquete llega a un almacén, ocupa espacio físico
 * - Después de 2 horas, el paquete "desaparece" (cliente lo recoge)
 * - Esta tabla registra esas ocupaciones temporales para que GRASP
 *   pueda planificar sin conflictos entre ejecuciones
 */
@Entity
@Table(name = "almacen_ocupacion_temporal",
        indexes = {
                @Index(name = "idx_aeropuerto_tiempo", columnList = "aeropuerto_codigo,hora_inicio,hora_fin"),
                @Index(name = "idx_pedido", columnList = "pedido_id"),
                @Index(name = "idx_hora_fin", columnList = "hora_fin")
        })
public class AlmacenOcupacionTemporal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aeropuerto_codigo", nullable = false, length = 10)
    private String aeropuertoCodigo;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "hora_inicio", nullable = false)
    private LocalDateTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalDateTime horaFin;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    // ============================================
    // CONSTRUCTORES
    // ============================================

    public AlmacenOcupacionTemporal() {
        this.creadoEn = LocalDateTime.now();
    }

    public AlmacenOcupacionTemporal(String aeropuertoCodigo, Long pedidoId, Integer cantidad,
                                    LocalDateTime horaInicio, LocalDateTime horaFin) {
        this.aeropuertoCodigo = aeropuertoCodigo;
        this.pedidoId = pedidoId;
        this.cantidad = cantidad;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.creadoEn = LocalDateTime.now();
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

    public String getAeropuertoCodigo() {
        return aeropuertoCodigo;
    }

    public void setAeropuertoCodigo(String aeropuertoCodigo) {
        this.aeropuertoCodigo = aeropuertoCodigo;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDateTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalDateTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalDateTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalDateTime horaFin) {
        this.horaFin = horaFin;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    // ============================================
    // MÉTODOS ÚTILES
    // ============================================

    /**
     * Verifica si esta ocupación está activa en un momento dado
     */
    public boolean estaActivaEn(LocalDateTime momento) {
        return !momento.isBefore(horaInicio) && momento.isBefore(horaFin);
    }

    /**
     * Verifica si esta ocupación ya venció (pasaron las 2 horas)
     */
    public boolean haVencido(LocalDateTime ahora) {
        return !ahora.isBefore(horaFin);
    }

    @Override
    public String toString() {
        return "AlmacenOcupacionTemporal{" +
                "id=" + id +
                ", aeropuerto='" + aeropuertoCodigo + '\'' +
                ", pedido=" + pedidoId +
                ", cantidad=" + cantidad +
                ", periodo=[" + horaInicio + " - " + horaFin + "]" +
                '}';
    }
}