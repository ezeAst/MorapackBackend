package com.morapack.backend.model;

import lombok.Data;

@Data
public class SimulationMetrics {

    private int ordersProcessed;
    private int flightsCompleted;
    private int packagesDelivered;
    private int packagesPending;
    private int warehouseViolations;
    private int flightViolations;
    private double successRate;

    public SimulationMetrics() {
        this.ordersProcessed = 0;
        this.flightsCompleted = 0;
        this.packagesDelivered = 0;
        this.packagesPending = 0;
        this.warehouseViolations = 0;
        this.flightViolations = 0;
        this.successRate = 0.0;
    }

    public void updateSuccessRate() {
        if (ordersProcessed > 0) {
            this.successRate = (packagesDelivered * 100.0) /
                    (packagesDelivered + packagesPending);
        }
    }
}