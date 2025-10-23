package com.morapack.algoritmologistica.algorithm.models;

public class Pedido {

    // === Atributos ===
    private int dia;                   // Día de registro
    private int hora;                  // Hora de registro
    private int minuto;                // Minuto de registro
    private String aeropuertoDestino;  // Código del aeropuerto destino (ej: "SKBO")
    private int cantidad;              // Cantidad de productos (1-999)

    public int getCantidadCumplida() {
        return cantidadCumplida;
    }

    public void setCantidadCumplida(int cantidadCumplida) {
        this.cantidadCumplida = cantidadCumplida;
    }

    private int cantidadCumplida;      //cantidad asignada del pedido
    private String idCliente;          // Identificador del cliente

    // === Constructores ===
    public Pedido() {
    }

    public Pedido(int dia, int hora, int minuto, String aeropuertoDestino, int cantidad, String idCliente) {
        this.dia = dia;
        this.hora = hora;
        this.minuto = minuto;
        this.aeropuertoDestino = aeropuertoDestino;
        this.cantidad = cantidad;
        this.cantidadCumplida = 0;
        this.idCliente = idCliente;
    }

    // === Getters y Setters ===
    public int getDia() {
        return dia;
    }

    public void setDia(int dia) {
        this.dia = dia;
    }

    public int getHora() {
        return hora;
    }

    public void setHora(int hora) {
        this.hora = hora;
    }

    public int getMinuto() {
        return minuto;
    }

    public void setMinuto(int minuto) {
        this.minuto = minuto;
    }

    public String getAeropuertoDestino() {
        return aeropuertoDestino;
    }

    public void setAeropuertoDestino(String aeropuertoDestino) {
        this.aeropuertoDestino = aeropuertoDestino;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public String getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(String idCliente) {
        this.idCliente = idCliente;
    }

    @Override
    public String toString() {
        return "Pedido{" +
                "dia=" + dia +
                ", hora=" + hora +
                ", minuto=" + minuto +
                ", aeropuertoDestino='" + aeropuertoDestino + '\'' +
                ", cantidad=" + cantidad +
                ", idCliente='" + idCliente + '\'' +
                '}';
    }
}

