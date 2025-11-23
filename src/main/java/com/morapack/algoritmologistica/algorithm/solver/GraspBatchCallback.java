package com.morapack.algoritmologistica.algorithm.solver;

import java.util.List;
import com.morapack.algoritmologistica.algorithm.models.Ruta;

public interface GraspBatchCallback {
    void onBatchComplete(List<Ruta> rutasBatch, int pedidosProcesados, int totalPedidos);
}