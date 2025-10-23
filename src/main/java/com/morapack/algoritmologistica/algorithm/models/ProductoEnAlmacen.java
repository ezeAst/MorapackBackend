package com.morapack.algoritmologistica.algorithm.models;

import java.time.LocalDateTime;

public class ProductoEnAlmacen {

    private Ruta ruta;                      // Ruta a la que pertenece
    private int cantidad;                   // Cantidad de productos
    private LocalDateTime horaLlegada;      // Cuándo llegaron al almacén
    private Vuelo siguienteVuelo;           // Vuelo de conexión (null si es destino final)

    public ProductoEnAlmacen(Ruta ruta, int cantidad, LocalDateTime horaLlegada, Vuelo siguienteVuelo) {
        this.ruta = ruta;
        this.cantidad = cantidad;
        this.horaLlegada = horaLlegada;
        this.siguienteVuelo = siguienteVuelo;
    }

    // === Getters y Setters ===
    public Ruta getRuta() {
        return ruta;
    }

    public void setRuta(Ruta ruta) {
        this.ruta = ruta;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalDateTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public Vuelo getSiguienteVuelo() {
        return siguienteVuelo;
    }

    public void setSiguienteVuelo(Vuelo siguienteVuelo) {
        this.siguienteVuelo = siguienteVuelo;
    }

    /**
     * Verifica si es un producto en destino final (no tiene siguiente vuelo)
     */
    public boolean esDestinoFinal() {
        return siguienteVuelo == null;
    }

    @Override
    public String toString() {
        return "ProductoEnAlmacen{" +
                "cantidad=" + cantidad +
                ", horaLlegada=" + horaLlegada +
                ", esDestinoFinal=" + esDestinoFinal() +
                '}';
    }
}
