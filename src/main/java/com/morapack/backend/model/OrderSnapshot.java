package com.morapack.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderSnapshot {
    private String orderId;
    private String destinationAirport;
    private String status;  // "pending", "in_transit", "delivered"
    private String clientId;
    private int day;
    private int hour;
    private int minute;
    private String arrivalTime;  // Tiempo cuando llegó al almacén
    private String deliveryTime; // Tiempo cuando se entregó al cliente
    private double progressPercentage; // 0-100%
}