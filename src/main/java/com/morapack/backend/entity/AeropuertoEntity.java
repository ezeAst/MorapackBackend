package com.morapack.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aeropuerto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AeropuertoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", unique = true, nullable = false, length = 12)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 160)
    private String nombre;

    @Column(name = "pais", nullable = false, length = 80)
    private String pais;

    @Column(name = "continente", nullable = false, length = 40)
    private String continente;

    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;

    @Column(name = "capacidad_actual", nullable = false)
    private Integer capacidadActual = 0;

    @Column(name = "huso_horario", nullable = false)
    private Integer husoHorario;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    public AeropuertoEntity(String nombre, String codigo, Integer husoHorario) {
        this.nombre = nombre;
        this.codigo = codigo;
        this.husoHorario = husoHorario;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getContinente() {
        return continente;
    }

    public void setContinente(String continente) {
        this.continente = continente;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(Integer capacidad) {
        this.capacidad = capacidad;
    }

    public Integer getCapacidadActual() {
        return capacidadActual;
    }

    public void setCapacidadActual(Integer capacidadActual) {
        this.capacidadActual = capacidadActual;
    }

    public Integer getHusoHorario() {
        return husoHorario;
    }

    public void setHusoHorario(Integer husoHorario) {
        this.husoHorario = husoHorario;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}