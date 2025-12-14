package com.morapack.algoritmologistica.algorithm.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "dia")
    private int dia;                    // Día del mes (1-31)

    @Column(name = "mes")
    private int mes;                    // Mes del año (1-12)

    @Column(name = "hora")
    private int hora;

    @Column(name = "anho")
    private int anho;

    @Column(name = "minuto")
    private int minuto;

    @Column(name = "aeropuerto_destino")
    private String aeropuertoDestino;

    @Column(name = "cantidad")
    private int cantidad;

    @Column(name = "cantidad_cumplida")
    private int cantidadCumplida;

    @Column(name = "id_cliente")
    private String idCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPedido estado = EstadoPedido.NO_ASIGNADO;

    @Column(name = "tramo_actual")
    private Integer tramoActual = 0;

    @Column(name = "hora_entrega")
    private LocalDateTime horaEntrega;

    // === CONSTRUCTORES ===
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

    public Pedido(int dia, int mes, int hora, int minuto, String aeropuertoDestino,
                  int cantidad, int cantidadCumplida, String idCliente) {
        this.dia = dia;
        this.mes = mes;
        this.hora = hora;
        this.minuto = minuto;
        this.aeropuertoDestino = aeropuertoDestino;
        this.cantidad = cantidad;
        this.cantidadCumplida = cantidadCumplida;
        this.idCliente = idCliente;
    }

    public Pedido(int dia, int mes, int hora, int anho, int minuto, String aeropuertoDestino, int cantidad, String idCliente) {
        this.dia = dia;
        this.mes = mes;
        this.hora = hora;
        this.anho = anho;
        this.minuto = minuto;
        this.aeropuertoDestino = aeropuertoDestino;
        this.cantidad = cantidad;
        this.idCliente = idCliente;
    }

    // === MÉTODOS DE FECHA ===
    /**
     * Construye un LocalDateTime desde los campos separados
     *
     * @return Fecha completa del pedido
     */
    public LocalDateTime getFechaPedido() {
        if (anho > 0 && mes > 0 && dia > 0) {
            return LocalDateTime.of(anho, mes, dia, hora, minuto);
        }
        return null;
    }

    /**
     * Descompone un LocalDateTime en los campos separados (dia, mes, anho, hora, minuto)
     * para guardarlos en la BD
     *
     * @param fechaPedido Fecha completa a descomponer
     */
    public void setFechaPedido(LocalDateTime fechaPedido) {
        if (fechaPedido != null) {
            this.dia = fechaPedido.getDayOfMonth();
            this.mes = fechaPedido.getMonthValue();
            this.anho = fechaPedido.getYear();
            this.hora = fechaPedido.getHour();
            this.minuto = fechaPedido.getMinute();
        }
    }

    // === GETTERS Y SETTERS ===
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

    public int getMes() {
        return mes;
    }

    public void setMes(int mes) {
        this.mes = mes;
    }

    public int getHora() {
        return hora;
    }

    public void setHora(int hora) {
        this.hora = hora;
    }

    public int getAnho() {
        return anho;
    }

    public void setAnho(int anho) {
        this.anho = anho;
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

    public int getCantidadCumplida() {
        return cantidadCumplida;
    }

    public void setCantidadCumplida(int cantidadCumplida) {
        this.cantidadCumplida = cantidadCumplida;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public Integer getTramoActual() {
        return tramoActual;
    }

    public void setTramoActual(Integer tramoActual) {
        this.tramoActual = tramoActual;
    }

    public LocalDateTime getHoraEntrega() {
        return horaEntrega;
    }

    public void setHoraEntrega(LocalDateTime horaEntrega) {
        this.horaEntrega = horaEntrega;
    }

    @Override
    public String toString() {
        return "Pedido{" +
                "dia=" + dia +
                ", mes=" + mes +
                ", hora=" + hora +
                ", minuto=" + minuto +
                ", aeropuertoDestino='" + aeropuertoDestino + '\'' +
                ", cantidad=" + cantidad +
                ", idCliente='" + idCliente + '\'' +
                '}';
    }
}