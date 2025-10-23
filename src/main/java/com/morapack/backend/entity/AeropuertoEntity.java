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
}