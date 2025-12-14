package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PedidoOperacionesCargaService {

    private final PedidoRepository pedidoRepository;
    private final Pedidobatchservice pedidobatchservice;
    private final TiempoSimuladoService tiempoSimuladoService;

    public PedidoOperacionesCargaService(PedidoRepository pedidoRepository,
                                         Pedidobatchservice pedidobatchservice,
                                         TiempoSimuladoService tiempoSimuladoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidobatchservice = pedidobatchservice;
        this.tiempoSimuladoService = tiempoSimuladoService;
    }

    /**
     * DTO para recibir pedidos desde el frontend
     */
    public static class PedidoOperacionDTO {
        private String idPedido;
        private Integer anho;
        private Integer mes;
        private Integer dia;           // null si usa ##
        private Integer hora;          // null si usa ##
        private Integer minuto;        // null si usa ##
        private String destino;
        private Integer cantidad;
        private String idCliente;
        private Boolean usarTiempoSimulado;

        // Getters y setters
        public String getIdPedido() { return idPedido; }
        public void setIdPedido(String idPedido) { this.idPedido = idPedido; }

        public Integer getAnho() { return anho; }
        public void setAnho(Integer anho) { this.anho = anho; }

        public Integer getMes() { return mes; }
        public void setMes(Integer mes) { this.mes = mes; }

        public Integer getDia() { return dia; }
        public void setDia(Integer dia) { this.dia = dia; }

        public Integer getHora() { return hora; }
        public void setHora(Integer hora) { this.hora = hora; }

        public Integer getMinuto() { return minuto; }
        public void setMinuto(Integer minuto) { this.minuto = minuto; }

        public String getDestino() { return destino; }
        public void setDestino(String destino) { this.destino = destino; }

        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

        public String getIdCliente() { return idCliente; }
        public void setIdCliente(String idCliente) { this.idCliente = idCliente; }

        public Boolean getUsarTiempoSimulado() { return usarTiempoSimulado; }
        public void setUsarTiempoSimulado(Boolean usarTiempoSimulado) { this.usarTiempoSimulado = usarTiempoSimulado; }
    }

    /**
     * Importa pedidos para operaciones día a día
     * Los pedidos con ## en día/hora/minuto usan el tiempo actual de la simulación
     */
    @Transactional
    public ImportResult importarPedidosOperaciones(List<PedidoOperacionDTO> pedidosDTO) {
        long startTime = System.currentTimeMillis();

        // Obtener tiempo actual de la simulación
        LocalDateTime tiempoSimulado = tiempoSimuladoService.obtenerTiempoActual();

        System.out.println("🕐 Tiempo simulado actual: " + tiempoSimulado);
        System.out.println("📦 Importando " + pedidosDTO.size() + " pedidos para operaciones día a día");

        // Obtener último ID
        Long ultimoId = pedidoRepository.findMaxId().orElse(0L);
        System.out.println("🔢 Último ID en BD: " + ultimoId);

        List<Pedido> pedidosParaInsertar = new ArrayList<>();
        int conTiempoSimulado = 0;
        int sinTiempoSimulado = 0;

        for (PedidoOperacionDTO dto : pedidosDTO) {
            Pedido pedido = new Pedido();

            // Determinar fecha/hora del pedido
            int dia, hora, minuto;

            if (dto.getUsarTiempoSimulado() != null && dto.getUsarTiempoSimulado()) {
                // Usar tiempo de la simulación para los valores ##
                dia = (dto.getDia() != null) ? dto.getDia() : tiempoSimulado.getDayOfMonth();
                hora = (dto.getHora() != null) ? dto.getHora() : tiempoSimulado.getHour();
                minuto = (dto.getMinuto() != null) ? dto.getMinuto() : tiempoSimulado.getMinute();
                conTiempoSimulado++;
            } else {
                // Usar valores del archivo
                dia = dto.getDia();
                hora = dto.getHora();
                minuto = dto.getMinuto();
                sinTiempoSimulado++;
            }

            pedido.setDia(dia);
            pedido.setMes(dto.getMes());
            pedido.setAnho(dto.getAnho());
            pedido.setHora(hora);
            pedido.setMinuto(minuto);
            pedido.setAeropuertoDestino(dto.getDestino());
            pedido.setCantidad(dto.getCantidad());
            pedido.setIdCliente(dto.getIdCliente());
            pedido.setEstado(EstadoPedido.NO_ASIGNADO);
            pedido.setTramoActual(0);
            pedido.setCantidadCumplida(0);

            // Crear fecha de pedido
            pedido.setFechaPedido(LocalDateTime.of(
                    dto.getAnho(),
                    dto.getMes(),
                    dia,
                    hora,
                    minuto
            ));

            pedidosParaInsertar.add(pedido);
        }

        // Insertar todos en lote
        int insertados = pedidobatchservice.insertarPedidosEnLote(pedidosParaInsertar, ultimoId);

        long endTime = System.currentTimeMillis();
        long tiempoTotal = endTime - startTime;

        System.out.println("✅ IMPORTACIÓN COMPLETADA:");
        System.out.println("   - Pedidos insertados: " + insertados);
        System.out.println("   - Con tiempo simulado: " + conTiempoSimulado);
        System.out.println("   - Con tiempo del archivo: " + sinTiempoSimulado);
        System.out.println("   - Tiempo total: " + tiempoTotal + "ms");

        return new ImportResult(
                insertados,
                tiempoTotal,
                conTiempoSimulado,
                sinTiempoSimulado,
                tiempoSimulado.toString()
        );
    }

    /**
     * Clase de respuesta para importación
     */
    public static class ImportResult {
        private int pedidosInsertados;
        private long tiempoMs;
        private int conTiempoSimulado;
        private int conTiempoArchivo;
        private String tiempoSimuladoUsado;

        public ImportResult(int pedidosInsertados, long tiempoMs,
                            int conTiempoSimulado, int conTiempoArchivo,
                            String tiempoSimuladoUsado) {
            this.pedidosInsertados = pedidosInsertados;
            this.tiempoMs = tiempoMs;
            this.conTiempoSimulado = conTiempoSimulado;
            this.conTiempoArchivo = conTiempoArchivo;
            this.tiempoSimuladoUsado = tiempoSimuladoUsado;
        }

        // Getters
        public int getPedidosInsertados() { return pedidosInsertados; }
        public long getTiempoMs() { return tiempoMs; }
        public int getConTiempoSimulado() { return conTiempoSimulado; }
        public int getConTiempoArchivo() { return conTiempoArchivo; }
        public String getTiempoSimuladoUsado() { return tiempoSimuladoUsado; }
    }
}