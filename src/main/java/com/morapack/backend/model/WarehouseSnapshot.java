package com.morapack.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WarehouseSnapshot {

    private String id;
    private String name;
    private String code;
    private List<Map<String, Object>> outgoingFlights = new ArrayList<>();
    private List<Map<String, Object>> outgoingOrders = new ArrayList<>();

    // Ubicación
    private double lat;
    private double lng;

    // Capacidad
    private int capacity;
    private int current;
    private int available;

    // Estado: "normal", "warning", "critical", "full"
    private String status;
    private double occupancyPercentage;

    // Productos
    private int productsInTransit;
    private int productsAtDestination;


    public void updateStatus() {
        if (capacity > 0) {
            occupancyPercentage = Math.min(100.0, (current * 100.0) / capacity); // ← Limitar a 100%

            if (occupancyPercentage >= 100) {
                status = "full";
            } else if (occupancyPercentage >= 90) {
                status = "critical";
            } else if (occupancyPercentage >= 70) {
                status = "warning";
            } else {
                status = "normal";
            }
        }
    }

    public List<Map<String, Object>> getOutgoingFlights() {
        return outgoingFlights;
    }

    public void setOutgoingFlights(List<Map<String, Object>> outgoingFlights) {
        this.outgoingFlights = outgoingFlights;
    }

    public List<Map<String, Object>> getOutgoingOrders() {
        return outgoingOrders;
    }

    public void setOutgoingOrders(List<Map<String, Object>> outgoingOrders) {
        this.outgoingOrders = outgoingOrders;
    }


}