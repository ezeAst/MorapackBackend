package com.morapack.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rutas_asignadas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pedido_id"}))
public class RutaAsignada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "pedido_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long pedidoId;

    @Column(name = "cantidad")
    private Integer cantidad; // entero como pediste

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now(ZoneOffset.UTC);

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    private List<RutaTramo> tramos = new ArrayList<>();

    // helpers
    public void addTramo(RutaTramo t) {
        t.setRuta(this);
        this.tramos.add(t);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public List<RutaTramo> getTramos() {
        return tramos;
    }

    public void setTramos(List<RutaTramo> tramos) {
        this.tramos = tramos;
    }
}
