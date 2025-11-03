package com.morapack.backend.dto.response;

import com.morapack.backend.model.FlightSnapshot;
import com.morapack.backend.model.OrderSnapshot;
import com.morapack.backend.model.SimulationEvent;
import com.morapack.backend.model.SimulationMetrics;
import com.morapack.backend.model.WarehouseSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimulationStatusResponse {

    private long elapsedSeconds;
    private double progressPercentage;
    private String status;

    // Tiempo simulado actual
    private int currentDay;
    private int currentHour;
    private int currentMinute;

    // Estados actuales
    private List<FlightSnapshot> activeFlights;
    private List<WarehouseSnapshot> warehouses;
    private List<OrderSnapshot> activeOrders;  // Pedidos en proceso (pending o in_transit)
    private List<OrderSnapshot> recentlyDeliveredOrders;  // Últimos pedidos entregados

    private SimulationMetrics metrics;
    private List<SimulationEvent> recentEvents;

    private String currentDateTime;
}