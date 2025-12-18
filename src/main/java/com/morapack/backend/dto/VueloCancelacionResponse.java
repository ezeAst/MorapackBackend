package com.morapack.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de respuesta al registrar cancelaciones
 */
public class VueloCancelacionResponse {

    private boolean exito;
    private String mensaje;
    private int vuelosRegistrados;
    private int vuelosDuplicados;
    private int pedidosAfectados;
    private int rutasEliminadas;
    private List<Long> pedidosReasignados;
    private List<String> errores;

    public VueloCancelacionResponse() {
        this.pedidosReasignados = new ArrayList<>();
        this.errores = new ArrayList<>();
    }

    // ============================================
    // FACTORY METHODS
    // ============================================

    public static VueloCancelacionResponse exito(int vuelosRegistrados, int vuelosDuplicados,
                                                 int pedidosAfectados, int rutasEliminadas,
                                                 List<Long> pedidosReasignados) {
        VueloCancelacionResponse response = new VueloCancelacionResponse();
        response.exito = true;
        response.mensaje = "Cancelaciones procesadas exitosamente";
        response.vuelosRegistrados = vuelosRegistrados;
        response.vuelosDuplicados = vuelosDuplicados;
        response.pedidosAfectados = pedidosAfectados;
        response.rutasEliminadas = rutasEliminadas;
        response.pedidosReasignados = pedidosReasignados;
        return response;
    }

    public static VueloCancelacionResponse error(String mensaje) {
        VueloCancelacionResponse response = new VueloCancelacionResponse();
        response.exito = false;
        response.mensaje = mensaje;
        return response;
    }

    public void agregarError(String error) {
        if (this.errores == null) {
            this.errores = new ArrayList<>();
        }
        this.errores.add(error);
    }

    // ============================================
    // GETTERS Y SETTERS
    // ============================================

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public int getVuelosRegistrados() {
        return vuelosRegistrados;
    }

    public void setVuelosRegistrados(int vuelosRegistrados) {
        this.vuelosRegistrados = vuelosRegistrados;
    }

    public int getVuelosDuplicados() {
        return vuelosDuplicados;
    }

    public void setVuelosDuplicados(int vuelosDuplicados) {
        this.vuelosDuplicados = vuelosDuplicados;
    }

    public int getPedidosAfectados() {
        return pedidosAfectados;
    }

    public void setPedidosAfectados(int pedidosAfectados) {
        this.pedidosAfectados = pedidosAfectados;
    }

    public int getRutasEliminadas() {
        return rutasEliminadas;
    }

    public void setRutasEliminadas(int rutasEliminadas) {
        this.rutasEliminadas = rutasEliminadas;
    }

    public List<Long> getPedidosReasignados() {
        return pedidosReasignados;
    }

    public void setPedidosReasignados(List<Long> pedidosReasignados) {
        this.pedidosReasignados = pedidosReasignados;
    }

    public List<String> getErrores() {
        return errores;
    }

    public void setErrores(List<String> errores) {
        this.errores = errores;
    }
}