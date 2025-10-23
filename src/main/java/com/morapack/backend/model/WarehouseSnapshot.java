package com.morapack.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WarehouseSnapshot {

    private String id;
    private String name;
    private String code;

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
        this.occupancyPercentage = (current * 100.0) / capacity;
        this.available = capacity - current;

        if (occupancyPercentage >= 100) {
            this.status = "full";
        } else if (occupancyPercentage >= 90) {
            this.status = "critical";
        } else if (occupancyPercentage >= 70) {
            this.status = "warning";
        } else {
            this.status = "normal";
        }
    }
}