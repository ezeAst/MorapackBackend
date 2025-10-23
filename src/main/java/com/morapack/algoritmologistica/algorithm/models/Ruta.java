package com.morapack.algoritmologistica.algorithm.models;

import java.util.ArrayList;
import java.util.List;

public class Ruta {

    // === Atributos ===
    private Pedido pedido;                 // Pedido al que pertenece esta ruta
    private Aeropuerto sedeOrigen;         // Aeropuerto o sede de origen (Lima, Bruselas, Bakú, etc.)
    private List<Vuelo> vuelos;            // Secuencia ordenada de vuelos
    private int cantidad;                  // Cantidad de productos que siguen esta ruta
    private boolean cumplePlazo;

    public boolean isCumplePlazo() {
        return cumplePlazo;
    }

    public void setCumplePlazo(boolean cumplePlazo) {
        this.cumplePlazo = cumplePlazo;
    }

    // === Constructores ===
    public Ruta() {
        this.vuelos = new ArrayList<>();
    }

    public Ruta(Pedido pedido, Aeropuerto sedeOrigen, List<Vuelo> vuelos, int cantidad) {
        this.pedido = pedido;
        this.sedeOrigen = sedeOrigen;
        this.vuelos = (vuelos != null) ? vuelos : new ArrayList<>();
        this.cantidad = cantidad;
    }

    // === Getters y Setters ===
    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Aeropuerto getSedeOrigen() {
        return sedeOrigen;
    }

    public void setSedeOrigen(Aeropuerto sedeOrigen) {
        this.sedeOrigen = sedeOrigen;
    }

    public List<Vuelo> getVuelos() {
        return vuelos;
    }

    public void setVuelos(List<Vuelo> vuelos) {
        this.vuelos = vuelos;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    // === Métodos funcionales ===

    /**
     * Agrega un vuelo al final de la ruta.
     */
    public void agregarVuelo(Vuelo vuelo) {
        if (vuelo != null) {
            vuelos.add(vuelo);
        }
    }

    /**
     * Devuelve el aeropuerto destino final de la ruta.
     */
    public Aeropuerto getDestinoFinal() {
        if (vuelos.isEmpty()) {
            return null; // o manejar de otra forma
        }
        return vuelos.get(vuelos.size() - 1).getAeropuertoDestino();
    }

    /**
     * Calcula la cantidad total de vuelos de la ruta.
     */
    public int getNumeroDeVuelos() {
        return vuelos.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ruta{");
        sb.append("pedido=").append(pedido != null ? pedido.getIdCliente() : "N/A");
        sb.append(", origen=").append(sedeOrigen != null ? sedeOrigen.getCodigo() : "N/A");
        sb.append(", destinoFinal=").append(getDestinoFinal() != null ? getDestinoFinal().getCodigo() : "N/A");
        sb.append(", cantidad=").append(cantidad);
        sb.append(", vuelos=[");
        for (Vuelo v : vuelos) {
            sb.append(v.getAeropuertoOrigen().getCodigo())
                    .append("→")
                    .append(v.getAeropuertoDestino().getCodigo())
                    .append(" ");
        }
        sb.append("]}");
        return sb.toString();
    }
}
