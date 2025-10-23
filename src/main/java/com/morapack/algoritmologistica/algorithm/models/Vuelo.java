package com.morapack.algoritmologistica.algorithm.models;

import java.time.LocalDateTime;

public class Vuelo {

    // === Atributos ===
    private Aeropuerto aeropuertoOrigen;   // Aeropuerto de salida
    private Aeropuerto aeropuertoDestino;  // Aeropuerto de llegada
    private LocalDateTime horaSalida;          // Hora de salida (zona horaria de origen)
    private LocalDateTime horaLlegada;         // Hora de llegada (zona horaria de destino)
    private int capacidadMaxima;           // Capacidad máxima del avión
    private int capacidadActual;           // Capacidad ocupada actualmente

    // === Constructores ===
    public Vuelo() {
    }

    public Vuelo(Aeropuerto aeropuertoOrigen, Aeropuerto aeropuertoDestino,
                 LocalDateTime horaSalida, LocalDateTime horaLlegada,
                 int capacidadMaxima) {
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.capacidadMaxima = capacidadMaxima;
        this.capacidadActual = 0;
    }

    // === Getters y Setters ===
    public Aeropuerto getAeropuertoOrigen() {
        return aeropuertoOrigen;
    }

    public void setAeropuertoOrigen(Aeropuerto aeropuertoOrigen) {
        this.aeropuertoOrigen = aeropuertoOrigen;
    }

    public Aeropuerto getAeropuertoDestino() {
        return aeropuertoDestino;
    }

    public void setAeropuertoDestino(Aeropuerto aeropuertoDestino) {
        this.aeropuertoDestino = aeropuertoDestino;
    }

    public LocalDateTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalDateTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalDateTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public int getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(int capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public int getCapacidadActual() {
        return capacidadActual;
    }

    public void setCapacidadActual(int capacidadActual) {
        this.capacidadActual = capacidadActual;
    }

    // === Métodos funcionales ===
    public boolean cargarProductos(int cantidad) {
        if (capacidadActual + cantidad <= capacidadMaxima) {
            capacidadActual += cantidad;
            return true;
        }
        return false;
    }

    public boolean descargarProductos(int cantidad) {
        if (capacidadActual - cantidad >= 0) {
            capacidadActual -= cantidad;
            return true;
        }
        return false;
    }

    public boolean estaLleno() {
        return capacidadActual >= capacidadMaxima;
    }

    @Override
    public String toString() {
        return "Vuelo{" +
                "origen=" + (aeropuertoOrigen != null ? aeropuertoOrigen.getCodigo() : "N/A") +
                ", destino=" + (aeropuertoDestino != null ? aeropuertoDestino.getCodigo() : "N/A") +
                ", salida=" + horaSalida +
                ", llegada=" + horaLlegada +
                ", capacidadMaxima=" + capacidadMaxima +
                ", capacidadActual=" + capacidadActual +
                '}';
    }
}

