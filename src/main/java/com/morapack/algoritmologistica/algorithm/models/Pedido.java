package com.morapack.algoritmologistica.algorithm.models;

import jakarta.persistence.*;

@Entity
@Table(name = "pedido")
public class Pedido {

    // === Atributos ===
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "id")
    private Long id;
    @Column (name = "dia")
    private int dia;                   // Día de registro
    private int mes;
    private int hora;                  // Hora de registro
    private int minuto;                // Minuto de registro
    private String aeropuertoDestino;  // Código del aeropuerto destino (ej: "SKBO")
    private int cantidad;              // Cantidad de productos (1-999)
    private int cantidadCumplida;      //cantidad asignada del pedido
    private String idCliente;          // Identificador del cliente


    public int getCantidadCumplida() {
        return cantidadCumplida;
    }

    public void setCantidadCumplida(int cantidadCumplida) {
        this.cantidadCumplida = cantidadCumplida;
    }



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

    public Pedido(int dia, int mes, int hora, int minuto, String aeropuertoDestino, int cantidad, int cantidadCumplida, String idCliente) {
        this.dia = dia;
        this.mes = mes;
        this.hora = hora;
        this.minuto = minuto;
        this.aeropuertoDestino = aeropuertoDestino;
        this.cantidad = cantidad;
        this.cantidadCumplida = cantidadCumplida;
        this.idCliente = idCliente;
    }

    // === Getters y Setters ===


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public int getMes() {
        return mes;
    }

    public void setMes(int mes) {
        this.mes = mes;
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

