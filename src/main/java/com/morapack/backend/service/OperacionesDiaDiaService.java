package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.repository.RutaAsignadaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperacionesDiaDiaService {

    private final PedidoRepository pedidoRepository;
    private final RutaAsignadaRepository rutaAsignadaRepository;
    private final AeropuertoRepository aeropuertoRepository;

    private boolean activo = false;
    private LocalDateTime inicioOperaciones;
    private List<String> eventosRecientes = new ArrayList<>();

    public OperacionesDiaDiaService(PedidoRepository pedidoRepository,
                                    RutaAsignadaRepository rutaAsignadaRepository,
                                    AeropuertoRepository aeropuertoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.rutaAsignadaRepository = rutaAsignadaRepository;
        this.aeropuertoRepository = aeropuertoRepository;
    }

    /**
     * Inicia las operaciones día a día
     */
    public void iniciar() {
        this.activo = true;
        this.inicioOperaciones = LocalDateTime.now(); // Siempre hora actual
        this.eventosRecientes.clear();
        agregarEvento("🚀 Operaciones día a día iniciadas a las " + this.inicioOperaciones);
        System.out.println("✅ Operaciones día a día iniciadas: " + this.inicioOperaciones);
    }

    /**
     * Detiene las operaciones día a día
     */
    public void detener() {
        this.activo = false;
        agregarEvento("⏸️ Operaciones día a día detenidas");
        System.out.println("⏸️ Operaciones día a día detenidas");
    }

    /**
     * Motor principal - Se ejecuta cada 10 segundos
     */
    @Scheduled(fixedDelay = 10000) // 10 segundos
    @Transactional
    public void procesarOperaciones() {
        if (!activo) return;

        LocalDateTime ahora = LocalDateTime.now();

        // 1. Buscar pedidos activos (ASIGNADO, EN_TRANSITO, EN_ALMACEN_INTERMEDIO)
        List<Pedido> pedidosActivos = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.ASIGNADO, EstadoPedido.EN_TRANSITO, EstadoPedido.EN_ALMACEN_INTERMEDIO)
        );

        for (Pedido pedido : pedidosActivos) {
            procesarPedido(pedido, ahora);
        }
    }

    /**
     * Procesa un pedido individual
     */
    private void procesarPedido(Pedido pedido, LocalDateTime ahora) {
        // Obtener ruta asignada
        RutaAsignada ruta = rutaAsignadaRepository.findByPedidoId(pedido.getId());
        if (ruta == null || ruta.getTramos().isEmpty()) return;

        Integer tramoActual = pedido.getTramoActual();
        if (tramoActual == null) tramoActual = 0;

        // Verificar si ya completó todos los tramos
        if (tramoActual >= ruta.getTramos().size()) {
            if (pedido.getEstado() != EstadoPedido.ENTREGADO) {
                pedido.setEstado(EstadoPedido.ENTREGADO);
                pedidoRepository.save(pedido);
                agregarEvento("✅ Pedido #" + pedido.getId() + " entregado en " + pedido.getAeropuertoDestino());
            }
            return;
        }

        RutaTramo tramo = ruta.getTramos().get(tramoActual);

        // Construir fecha/hora de salida y llegada
        LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
        LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

        // Ajustar si la llegada es al día siguiente
        if (horaLlegada.isBefore(horaSalida)) {
            horaLlegada = horaLlegada.plusDays(1);
        }

        // CASO 1: Llegó hora de despegue
        if (!ahora.isBefore(horaSalida) && pedido.getEstado() == EstadoPedido.ASIGNADO) {
            // Cambiar a EN_TRANSITO
            pedido.setEstado(EstadoPedido.EN_TRANSITO);
            pedidoRepository.save(pedido);

            // Restar del almacén de origen
            actualizarCapacidadAlmacen(tramo.getOrigen(), -pedido.getCantidad());

            String vueloId = generarVueloId(tramo);
            agregarEvento("🛫 Vuelo " + vueloId + " despegó con pedido #" + pedido.getId() + " (" + pedido.getCantidad() + " paquetes)");

            return;
        }

        // CASO 2: Llegó hora de aterrizaje
        if (!ahora.isBefore(horaLlegada) && pedido.getEstado() == EstadoPedido.EN_TRANSITO) {
            // Sumar al almacén de destino
            actualizarCapacidadAlmacen(tramo.getDestino(), pedido.getCantidad());

            String vueloId = generarVueloId(tramo);
            agregarEvento("🛬 Vuelo " + vueloId + " aterrizó en " + tramo.getDestino());

            // Verificar si es el último tramo
            if (tramoActual == ruta.getTramos().size() - 1) {
                // Es el destino final
                pedido.setEstado(EstadoPedido.ENTREGADO);
                agregarEvento("✅ Pedido #" + pedido.getId() + " entregado en " + tramo.getDestino());
            } else {
                // Es un almacén intermedio
                pedido.setEstado(EstadoPedido.EN_ALMACEN_INTERMEDIO);
                pedido.setTramoActual(tramoActual + 1);
                agregarEvento("📦 Pedido #" + pedido.getId() + " en almacén intermedio " + tramo.getDestino());
            }

            pedidoRepository.save(pedido);
            return;
        }

        // CASO 3: Está en almacén intermedio esperando siguiente vuelo
        if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
            // Verificar si ya es hora del siguiente vuelo
            if (tramoActual < ruta.getTramos().size()) {
                RutaTramo siguienteTramo = ruta.getTramos().get(tramoActual);
                LocalDateTime siguienteSalida = LocalDateTime.of(siguienteTramo.getFecha(), LocalTime.parse(siguienteTramo.getHoraSalida()));

                if (!ahora.isBefore(siguienteSalida)) {
                    // Ya es hora del siguiente vuelo
                    pedido.setEstado(EstadoPedido.EN_TRANSITO);
                    pedidoRepository.save(pedido);

                    // Restar del almacén intermedio
                    actualizarCapacidadAlmacen(siguienteTramo.getOrigen(), -pedido.getCantidad());

                    String vueloId = generarVueloId(siguienteTramo);
                    agregarEvento("🛫 Vuelo " + vueloId + " despegó con pedido #" + pedido.getId());
                }
            }
        }
    }

    /**
     * Actualiza la capacidad de un almacén
     */
    private void actualizarCapacidadAlmacen(String codigoAeropuerto, int delta) {
        AeropuertoEntity aeropuerto = aeropuertoRepository.findByCodigo(codigoAeropuerto)
                .orElse(null);

        if (aeropuerto != null) {
            int nuevaCapacidad = aeropuerto.getCapacidadActual() + delta;
            aeropuerto.setCapacidadActual(Math.max(0, nuevaCapacidad));
            aeropuertoRepository.save(aeropuerto);

            System.out.println("📊 Almacén " + codigoAeropuerto + ": " + aeropuerto.getCapacidadActual() + "/" + aeropuerto.getCapacidad());
        }
    }

    /**
     * Genera ID único de vuelo
     */
    private String generarVueloId(RutaTramo tramo) {
        return tramo.getOrigen() + "-" + tramo.getDestino() + "-" +
                tramo.getFecha() + "-" + tramo.getHoraSalida().replace(":", "");
    }

    /**
     * Agrega un evento reciente
     */
    private void agregarEvento(String mensaje) {
        eventosRecientes.add(LocalDateTime.now().toString().substring(11, 19) + " - " + mensaje);

        // Mantener solo últimos 50 eventos
        if (eventosRecientes.size() > 50) {
            eventosRecientes.remove(0);
        }
    }

    /**
     * Obtiene eventos recientes
     */
    public List<String> getEventosRecientes() {
        return new ArrayList<>(eventosRecientes);
    }

    /**
     * Verifica si está activo
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Obtiene la hora de inicio
     */
    public LocalDateTime getInicioOperaciones() {
        return inicioOperaciones;
    }
}