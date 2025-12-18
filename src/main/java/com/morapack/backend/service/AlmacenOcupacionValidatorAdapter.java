package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.AlmacenOcupacionValidator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Adaptador que permite al algoritmo consultar la ocupación de almacenes
 * sin tener dependencia directa del servicio de backend
 */
@Component
public class AlmacenOcupacionValidatorAdapter implements AlmacenOcupacionValidator {

    private final AlmacenOcupacionService almacenOcupacionService;

    public AlmacenOcupacionValidatorAdapter(AlmacenOcupacionService almacenOcupacionService) {
        this.almacenOcupacionService = almacenOcupacionService;
    }

    @Override
    public boolean hayEspacioEnPeriodo(String aeropuertoCodigo,
                                       int capacidadMaxima,
                                       int capacidadFisicaActual,
                                       int cantidadNueva,
                                       LocalDateTime inicio,
                                       LocalDateTime fin) {
        return almacenOcupacionService.hayEspacioEnPeriodo(
                aeropuertoCodigo,
                capacidadMaxima,
                capacidadFisicaActual,
                cantidadNueva,
                inicio,
                fin
        );
    }
}