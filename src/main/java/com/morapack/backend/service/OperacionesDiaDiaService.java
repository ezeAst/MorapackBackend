package com.morapack.backend.service;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.entity.RutaAsignada;
import com.morapack.backend.entity.RutaTramo;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import com.morapack.backend.repository.RutaAsignadaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.stream.Collectors;

@Service
public class OperacionesDiaDiaService {

    private final PedidoRepository pedidoRepository;
    private final RutaAsignadaRepository rutaAsignadaRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final JdbcTemplate jdbcTemplate;
    private boolean activo = false;
    private LocalDateTime inicioOperaciones;
    private List<String> eventosRecientes = new ArrayList<>();

    public OperacionesDiaDiaService(PedidoRepository pedidoRepository,
                                    RutaAsignadaRepository rutaAsignadaRepository,
                                    AeropuertoRepository aeropuertoRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.pedidoRepository = pedidoRepository;
        this.rutaAsignadaRepository = rutaAsignadaRepository;
        this.aeropuertoRepository = aeropuertoRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        LocalDateTime limite = ahora.plusHours(2);


        String fechaStr = ahora.toLocalDate().toString();
        String fechaMananaStr = limite.toLocalDate().toString(); // Puede ser hoy o mañana
        String horaActualStr = ahora.toLocalTime().toString().substring(0, 5);
        String horaLimiteStr = limite.toLocalTime().toString().substring(0, 5);
        String fechaAyerStr = ahora.minusDays(1).toLocalDate().toString();

        System.out.println("🔍 DEBUG Query params:");
        System.out.println("   fechaStr: '" + fechaStr + "'");
        System.out.println("   horaLimiteStr: '" + horaLimiteStr + "'");
        System.out.println("   fechaAyerStr: '" + fechaAyerStr + "'");

        List<Pedido> pedidosActivos = pedidoRepository.findActivosConVuelosProximos(
                fechaStr,
                fechaMananaStr,
                horaActualStr,
                horaLimiteStr,
                fechaAyerStr
        );
        System.out.println("📊 Pedidos encontrados: " + pedidosActivos.size());

        if (pedidosActivos.isEmpty()) {
            System.out.println("⚠️ No se encontraron pedidos activos");
            return;
        }

        if (pedidosActivos.isEmpty()) return;

        System.out.println("📊 Procesando " + pedidosActivos.size() + " pedidos (antes: ~9000)");

        // 2. CARGAR TODAS LAS RUTAS DE UNA VEZ
        List<Long> pedidoIds = pedidosActivos.stream()
                .map(Pedido::getId)
                .toList();

        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);

        Map<Long, RutaAsignada> rutasPorPedido = new HashMap<>();
        for (RutaAsignada ruta : todasLasRutas) {
            rutasPorPedido.put(ruta.getPedidoId(), ruta);
        }

        // 4. Procesar cada pedido
        List<Pedido> pedidosModificados = pedidosActivos.stream()
                .filter(pedido -> {
                    RutaAsignada ruta = rutasPorPedido.get(pedido.getId());
                    return procesarPedido(pedido, ruta, ahora);
                })
                .collect(Collectors.toList());


        if (!pedidosModificados.isEmpty()) {
            System.out.println("💾 Guardando " + pedidosModificados.size() + " pedidos modificados");


            actualizarPedidosEnLote(pedidosModificados);
        }
    }

    /**
     * Procesa un pedido individual
     */
    private boolean procesarPedido(Pedido pedido, RutaAsignada ruta, LocalDateTime ahora) {
        if (ruta == null || ruta.getTramos().isEmpty()) return false;

        Integer tramoActual = pedido.getTramoActual();
        if (tramoActual == null) tramoActual = 0;

        // Verificar si ya completó todos los tramos
        if (tramoActual >= ruta.getTramos().size()) {
            if (pedido.getEstado() != EstadoPedido.ENTREGADO) {
                pedido.setEstado(EstadoPedido.ENTREGADO);
                agregarEvento("✅ Pedido #" + pedido.getId() + " entregado en " + pedido.getAeropuertoDestino());
                return true;
            }
            return false;
        }

        RutaTramo tramo = ruta.getTramos().get(tramoActual);

        // Construir fecha/hora de salida y llegada
        LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
        LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

        // Ajustar si la llegada es al día siguiente
        if (horaLlegada.isBefore(horaSalida)) {
            horaLlegada = horaLlegada.plusDays(1);
        }

        if (pedido.getId() % 1000 == 0) {
            System.out.println("🔍 Pedido #" + pedido.getId() + " estado=" + pedido.getEstado());
            System.out.println("   Ahora: " + ahora);
            System.out.println("   Salida: " + horaSalida + " (¿llegó hora? " + !ahora.isBefore(horaSalida) + ")");
            System.out.println("   Llegada: " + horaLlegada);
        }

        // CASO 1: Llegó hora de despegue
        if (!ahora.isBefore(horaSalida) && pedido.getEstado() == EstadoPedido.ASIGNADO) {
            pedido.setEstado(EstadoPedido.EN_TRANSITO);
            actualizarCapacidadAlmacen(tramo.getOrigen(), -pedido.getCantidad());

            String vueloId = generarVueloId(tramo);
            agregarEvento("🛫 Vuelo " + vueloId + " despegó con pedido #" + pedido.getId() + " (" + pedido.getCantidad() + " paquetes)");

            return true;
        }

        // CASO 2: Llegó hora de aterrizaje
        if (!ahora.isBefore(horaLlegada) && pedido.getEstado() == EstadoPedido.EN_TRANSITO) {
            actualizarCapacidadAlmacen(tramo.getDestino(), pedido.getCantidad());

            String vueloId = generarVueloId(tramo);
            agregarEvento("🛬 Vuelo " + vueloId + " aterrizó en " + tramo.getDestino());

            if (tramoActual == ruta.getTramos().size() - 1) {
                pedido.setEstado(EstadoPedido.ENTREGADO);
                pedido.setHoraEntrega(ahora);
                agregarEvento("✅ Pedido #" + pedido.getId() + " entregado en " + tramo.getDestino());
            } else {
                pedido.setEstado(EstadoPedido.EN_ALMACEN_INTERMEDIO);
                pedido.setTramoActual(tramoActual + 1);
                agregarEvento("📦 Pedido #" + pedido.getId() + " en almacén intermedio " + tramo.getDestino());
            }

            return true;
        }

        // CASO 3: Está en almacén intermedio esperando siguiente vuelo
        if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
            if (tramoActual < ruta.getTramos().size()) {
                RutaTramo siguienteTramo = ruta.getTramos().get(tramoActual);
                LocalDateTime siguienteSalida = LocalDateTime.of(siguienteTramo.getFecha(), LocalTime.parse(siguienteTramo.getHoraSalida()));

                if (!ahora.isBefore(siguienteSalida)) {
                    pedido.setEstado(EstadoPedido.EN_TRANSITO);
                    actualizarCapacidadAlmacen(siguienteTramo.getOrigen(), -pedido.getCantidad());

                    String vueloId = generarVueloId(siguienteTramo);
                    agregarEvento("🛫 Vuelo " + vueloId + " despegó con pedido #" + pedido.getId());

                    return true;
                }
            }
        }

        return false;
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

    private void actualizarPedidosEnLote(List<Pedido> pedidos) {
        if (pedidos.isEmpty()) return;

        // Actualizar en lotes de 500
        int batchSize = 500;
        for (int i = 0; i < pedidos.size(); i += batchSize) {
            int end = Math.min(i + batchSize, pedidos.size());
            List<Pedido> batch = pedidos.subList(i, end);

            jdbcTemplate.batchUpdate(
                    "UPDATE pedido SET estado = ?, tramo_actual = ?, hora_entrega = ? WHERE id = ?",
                    batch,
                    batchSize,
                    (ps, pedido) -> {
                        ps.setString(1, pedido.getEstado().name());
                        ps.setObject(2, pedido.getTramoActual());
                        ps.setObject(3, pedido.getHoraEntrega());
                        ps.setLong(4, pedido.getId());
                    }
            );
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