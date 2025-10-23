package com.morapack.algoritmologistica.algorithm.solver;
import com.morapack.algoritmologistica.algorithm.models.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Solucion {

    // === Atributos ===
    private List<Ruta> rutas;                    // Todas las rutas asignadas
    private double fitness;                       // Valor de calidad de la solución
    private int pedidosEntregadosATiempo;        // Contador para objetivo 1
    private int violacionesCapacidadVuelos;      // Contador para objetivo 2 (vuelos)
    private int violacionesCapacidadAlmacenes;   // Contador para objetivo 2 (almacenes)

    // === Constructores ===
    public Solucion() {
        this.rutas = new ArrayList<>();
        this.fitness = 0.0;
        this.pedidosEntregadosATiempo = 0;
        this.violacionesCapacidadVuelos = 0;
        this.violacionesCapacidadAlmacenes = 0;
    }

    public Solucion(List<Ruta> rutas) {
        this.rutas = (rutas != null) ? rutas : new ArrayList<>();
        this.fitness = 0.0;
        this.pedidosEntregadosATiempo = 0;
        this.violacionesCapacidadVuelos = 0;
        this.violacionesCapacidadAlmacenes = 0;
    }

    // === Getters y Setters ===
    public List<Ruta> getRutas() {
        return rutas;
    }

    public void setRutas(List<Ruta> rutas) {
        this.rutas = rutas;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public int getPedidosEntregadosATiempo() {
        return pedidosEntregadosATiempo;
    }

    public void setPedidosEntregadosATiempo(int pedidosEntregadosATiempo) {
        this.pedidosEntregadosATiempo = pedidosEntregadosATiempo;
    }

    public int getViolacionesCapacidadVuelos() {
        return violacionesCapacidadVuelos;
    }

    public void setViolacionesCapacidadVuelos(int violacionesCapacidadVuelos) {
        this.violacionesCapacidadVuelos = violacionesCapacidadVuelos;
    }

    public int getViolacionesCapacidadAlmacenes() {
        return violacionesCapacidadAlmacenes;
    }

    public void setViolacionesCapacidadAlmacenes(int violacionesCapacidadAlmacenes) {
        this.violacionesCapacidadAlmacenes = violacionesCapacidadAlmacenes;
    }

    // === Métodos funcionales ===

    /**
     * Agrega una ruta a la solución.
     */
    public void agregarRuta(Ruta ruta) {
        if (ruta != null) {
            rutas.add(ruta);
        }
    }

    /**
     * Retorna el número total de rutas en la solución.
     */
    public int getNumeroDeRutas() {
        return rutas.size();
    }

    @Override
    public String toString() {
        return "Solucion{" +
                "numRutas=" + rutas.size() +
                ", fitness=" + fitness +
                ", pedidosATiempo=" + pedidosEntregadosATiempo +
                ", violacionesVuelos=" + violacionesCapacidadVuelos +
                ", violacionesAlmacenes=" + violacionesCapacidadAlmacenes +
                '}';
    }

    /**
     * Calcula cuántos pedidos fueron entregados a tiempo y completos
     * @param listaPedidos Lista original de todos los pedidos
     */
    public void calcularPedidosEntregadosATiempo(List<Pedido> listaPedidos) {
        pedidosEntregadosATiempo = 0;

        for (Pedido pedido : listaPedidos) {
            // Verificar si el pedido está completo
            if (pedido.getCantidadCumplida() >= pedido.getCantidad()) {
                // Verificar si todas las rutas de este pedido cumplen plazo
                boolean todasCumplenPlazo = true;

                for (Ruta ruta : rutas) {
                    if (ruta.getPedido().getIdCliente().equals(pedido.getIdCliente())) {
                        if (!ruta.isCumplePlazo()) {
                            todasCumplenPlazo = false;
                            break;
                        }
                    }
                }

                if (todasCumplenPlazo) {
                    pedidosEntregadosATiempo++;
                }
            }
        }
    }

    /**
     * Calcula violaciones de capacidad en vuelos
     * Recorre todos los vuelos y cuenta cuántos exceden su capacidad máxima
     * @param listaVuelos Lista de todos los vuelos utilizados
     */
    public void calcularViolacionesCapacidadVuelos(List<Vuelo> listaVuelos) {
        violacionesCapacidadVuelos = 0;

        for (Vuelo vuelo : listaVuelos) {
            if (vuelo.getCapacidadActual() > vuelo.getCapacidadMaxima()) {
                violacionesCapacidadVuelos++;
            }
        }
    }

    /**
     * Calcula violaciones de capacidad en almacenes
     * Verifica cada almacén en múltiples momentos de la semana
     * @param listaAeropuertos Lista de todos los aeropuertos
     */
    public void calcularViolacionesCapacidadAlmacenes(List<Aeropuerto> listaAeropuertos) {
        violacionesCapacidadAlmacenes = 0;

        // Para cada aeropuerto, verificar ocupación en diferentes momentos
        for (Aeropuerto aeropuerto : listaAeropuertos) {
            // Verificar cada día de la semana, cada hora
            for (int dia = 1; dia <= 7; dia++) {
                for (int hora = 0; hora < 24; hora++) {
                    LocalDateTime momento = LocalDateTime.of(2025, 1, dia, hora, 0);
                    int ocupacion = aeropuerto.calcularOcupacionEnMomento(momento);

                    if (ocupacion > aeropuerto.getCapacidad()) {
                        violacionesCapacidadAlmacenes++;
                        // Registrar solo una violación por aeropuerto para no inflar el contador
                        break; // Pasar al siguiente aeropuerto
                    }
                }

                if (violacionesCapacidadAlmacenes > 0) {
                    break; // Ya encontramos violación en este aeropuerto
                }
            }
        }
    }

    /**
     * Calcula el fitness total de la solución
     * Combina los diferentes objetivos con pesos según prioridad
     * @param totalPedidos Número total de pedidos a entregar
     */
    public void calcularFitness(int totalPedidos) {
        // Pesos de la función fitness (ajustables según prioridad)
        double W1 = 1000.0;  // Peso para cumplimiento (prioridad máxima)
        double W2 = 500.0;   // Peso para violaciones de vuelos
        double W3 = 500.0;   // Peso para violaciones de almacenes
        double W4 = 100.0;   // Peso para eficiencia (bonus)

        // Componente 1: Porcentaje de pedidos entregados a tiempo (0 a 1)
        double tasaCumplimiento = (double) pedidosEntregadosATiempo / totalPedidos;

        // Componente 2: Penalización por violaciones de capacidad
        double penalizacionVuelos = violacionesCapacidadVuelos;
        double penalizacionAlmacenes = violacionesCapacidadAlmacenes;

        // Componente 3: Bonus por eficiencia (menor número de rutas = mejor)
        double bonusEficiencia = 1.0 / (1.0 + rutas.size());

        // Función de fitness (mayor = mejor)
        fitness = (W1 * tasaCumplimiento)
                - (W2 * penalizacionVuelos)
                - (W3 * penalizacionAlmacenes)
                + (W4 * bonusEficiencia);

        // Asegurar que fitness no sea negativo
        if (fitness < 0) {
            fitness = 0;
        }
    }

    /**
     * Evalúa completamente la solución calculando todos los indicadores
     * @param listaPedidos Lista de todos los pedidos
     * @param listaVuelos Lista de todos los vuelos
     * @param listaAeropuertos Lista de todos los aeropuertos
     */
    public void evaluarSolucion(List<Pedido> listaPedidos, List<Vuelo> listaVuelos,
                                List<Aeropuerto> listaAeropuertos) {
        calcularPedidosEntregadosATiempo(listaPedidos);
        calcularViolacionesCapacidadVuelos(listaVuelos);
        calcularViolacionesCapacidadAlmacenes(listaAeropuertos);
        calcularFitness(listaPedidos.size());
    }
}