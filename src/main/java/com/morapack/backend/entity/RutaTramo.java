package com.morapack.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "rutas_tramo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ruta_id","orden"}))
public class RutaTramo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private RutaAsignada ruta;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "origen", nullable = false, length = 8)
    private String origen;

    @Column(name = "destino", nullable = false, length = 8)
    private String destino;

    @Column(name = "hora_salida", length = 5)
    private String horaSalida;   // "HH:MM"

    @Column(name = "hora_llegada", length = 5)
    private String horaLlegada;  // "HH:MM"

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "capacidad_usada")
    private Integer capacidadUsada; // opcional

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RutaAsignada getRuta() {
        return ruta;
    }

    public void setRuta(RutaAsignada ruta) {
        this.ruta = ruta;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
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

    public String getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(String horaSalida) {
        this.horaSalida = horaSalida;
    }

    public String getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(String horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public Integer getCapacidadUsada() {
        return capacidadUsada;
    }

    public void setCapacidadUsada(Integer capacidadUsada) {
        this.capacidadUsada = capacidadUsada;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }
}
