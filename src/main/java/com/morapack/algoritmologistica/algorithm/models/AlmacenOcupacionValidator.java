package com.morapack.algoritmologistica.algorithm.models;

import java.time.LocalDateTime;

/**
 * Interface para validar ocupación de almacenes
 * Permite al algoritmo consultar el servicio sin dependencia directa
 */
public interface AlmacenOcupacionValidator {
    boolean hayEspacioEnPeriodo(String aeropuertoCodigo,
                                int capacidadMaxima,
                                int capacidadFisicaActual,
                                int cantidadNueva,
                                LocalDateTime inicio,
                                LocalDateTime fin);
}
