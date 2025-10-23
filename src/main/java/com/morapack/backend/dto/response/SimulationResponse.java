package com.morapack.backend.dto.response;

import com.morapack.backend.model.FlightSnapshot;
import com.morapack.backend.model.WarehouseSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimulationResponse {

    private String simulationId;
    private String status;
    private String message;

    private List<FlightSnapshot> flights;
    private List<WarehouseSnapshot> warehouses;

    private int totalOrders;
    private int totalFlights;
    private int totalPackages;

    private long estimatedDurationSeconds;  // 5400 segundos = 90 minutos
}