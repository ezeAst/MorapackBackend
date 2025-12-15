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
    private final TiempoSimuladoService tiempoSimuladoService;
    private final AlmacenOcupacionService almacenOcupacionService;  // ✅ NUEVO
    private boolean activo = false;
    private LocalDateTime inicioOperaciones;
    private List<String> eventosRecientes = new ArrayList<>();

    public OperacionesDiaDiaService(PedidoRepository pedidoRepository,
                                    RutaAsignadaRepository rutaAsignadaRepository,
                                    AeropuertoRepository aeropuertoRepository,
                                    JdbcTemplate jdbcTemplate,
                                    TiempoSimuladoService tiempoSimuladoService,
                                    AlmacenOcupacionService almacenOcupacionService) {  // ✅ NUEVO
        this.pedidoRepository = pedidoRepository;
        this.rutaAsignadaRepository = rutaAsignadaRepository;
        this.aeropuertoRepository = aeropuertoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.tiempoSimuladoService = tiempoSimuladoService;
        this.almacenOcupacionService = almacenOcupacionService;  // ✅ NUEVO
    }

    /**
     * Inicia las operaciones día a día con fecha/hora específica
     */
    public void iniciar(LocalDateTime fechaHoraInicio) {
        this.activo = true;
        this.inicioOperaciones = fechaHoraInicio;
        this.eventosRecientes.clear();

        // Inicializar tiempo simulado
        tiempoSimuladoService.iniciarSimulacion(fechaHoraInicio);

        agregarEvento("🚀 Operaciones día a día iniciadas a las " + fechaHoraInicio);
        System.out.println("✅ Operaciones día a día iniciadas: " + fechaHoraInicio);
    }

    /**
     * Detiene las operaciones día a día
     */
    public void detener() {
        this.activo = false;
        tiempoSimuladoService.detenerSimulacion();
        agregarEvento("⏸️ Operaciones día a día detenidas");
        System.out.println("⏸️ Operaciones día a día detenidas");
    }

    /**
     * Motor principal - Se ejecuta cada 10 segundos
     */
    @Scheduled(fixedRate = 10000) // Cada 10 segundos
    @Transactional
    public void procesarOperaciones() {
        if (!activo) return;

        LocalDateTime ahora = tiempoSimuladoService.obtenerTiempoActual();

        // Buscar pedidos activos (ASIGNADO, EN_TRANSITO, EN_ALMACEN_INTERMEDIO)
        List<Pedido> pedidosActivos = pedidoRepository.findByEstadoIn(
                List.of(EstadoPedido.ASIGNADO, EstadoPedido.EN_TRANSITO, EstadoPedido.EN_ALMACEN_INTERMEDIO)
        );

        if (pedidosActivos.isEmpty()) return;

        System.out.println("📊 Pedidos encontrados: " + pedidosActivos.size());

        // Obtener TODAS las rutas de esos pedidos
        List<Long> pedidoIds = pedidosActivos.stream().map(Pedido::getId).toList();
        List<RutaAsignada> todasLasRutas = rutaAsignadaRepository.findByPedidoIdIn(pedidoIds);

        // Agrupar rutas por pedido
        Map<Long, List<RutaAsignada>> rutasPorPedido = todasLasRutas.stream()
                .collect(Collectors.groupingBy(RutaAsignada::getPedidoId));

        List<Pedido> pedidosModificados = new ArrayList<>();

        // ✅ NUEVA LÓGICA: Procesar cada RUTA independientemente
        for (Pedido pedido : pedidosActivos) {
            List<RutaAsignada> rutasDelPedido = rutasPorPedido.get(pedido.getId());
            if (rutasDelPedido == null || rutasDelPedido.isEmpty()) continue;

            boolean modificado = false;

            // Procesar CADA ruta del pedido
            for (RutaAsignada ruta : rutasDelPedido) {
                if (procesarRutaIndividual(pedido, ruta, ahora)) {
                    modificado = true;
                }
            }

            // ✅ TRANSICIÓN DE ESTADOS DEL PEDIDO
            // Solo cuando TODAS las rutas hayan completado
            if (modificado) {
                verificarEstadoPedido(pedido, rutasDelPedido, ahora);
                pedidosModificados.add(pedido);
            }
        }

        // Guardar cambios en batch
        if (!pedidosModificados.isEmpty()) {
            actualizarPedidosEnLote(pedidosModificados);
        }
    }

    /**
     * Procesa una ruta individual (puede haber varias rutas del mismo pedido)
     */
    private boolean procesarRutaIndividual(Pedido pedido, RutaAsignada ruta, LocalDateTime ahora) {
        if (ruta.getTramos().isEmpty()) return false;

        Integer tramoActual = pedido.getTramoActual();
        if (tramoActual == null) tramoActual = 0;

        // Si esta ruta ya completó todos sus tramos, no hacer nada
        if (tramoActual >= ruta.getTramos().size()) return false;

        RutaTramo tramo = ruta.getTramos().get(tramoActual);

        LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
        LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

        if (horaLlegada.isBefore(horaSalida)) {
            horaLlegada = horaLlegada.plusDays(1);
        }

        boolean cambio = false;

        // DESPEGUE: Si llegó la hora y el pedido está ASIGNADO
        if (!ahora.isBefore(horaSalida) && pedido.getEstado() == EstadoPedido.ASIGNADO) {
            actualizarCapacidadAlmacen(tramo.getOrigen(), -ruta.getCantidad());
            String vueloId = generarVueloId(tramo);
            agregarEvento("🛫 Vuelo " + vueloId + " (Ruta " + ruta.getId() + ") despegó con " +
                    ruta.getCantidad() + " paquetes del pedido #" + pedido.getId());
            cambio = true;
        }

        // ATERRIZAJE: Si llegó la hora y el pedido está EN_TRANSITO
        if (!ahora.isBefore(horaLlegada) && pedido.getEstado() == EstadoPedido.EN_TRANSITO) {
            actualizarCapacidadAlmacen(tramo.getDestino(), ruta.getCantidad());

            try {
                almacenOcupacionService.eliminarOcupacion(pedido.getId(), tramo.getDestino());
            } catch (Exception e) {
                System.err.println("⚠️ Error eliminando ocupación: " + e.getMessage());
            }

            String vueloId = generarVueloId(tramo);
            agregarEvento("🛬 Vuelo " + vueloId + " (Ruta " + ruta.getId() + ") aterrizó en " + tramo.getDestino());
            cambio = true;
        }

        // SIGUIENTE VUELO: Si está en almacén intermedio
        if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO && tramoActual + 1 < ruta.getTramos().size()) {
            RutaTramo siguienteTramo = ruta.getTramos().get(tramoActual + 1);
            LocalDateTime siguienteSalida = LocalDateTime.of(siguienteTramo.getFecha(),
                    LocalTime.parse(siguienteTramo.getHoraSalida()));

            if (!ahora.isBefore(siguienteSalida)) {
                actualizarCapacidadAlmacen(siguienteTramo.getOrigen(), -ruta.getCantidad());
                String vueloId = generarVueloId(siguienteTramo);
                agregarEvento("🛫 Vuelo " + vueloId + " (Ruta " + ruta.getId() +
                        ") despegó con " + ruta.getCantidad() + " paquetes");
                cambio = true;
            }
        }

        return cambio;
    }

    /**
     * Verifica el estado global del pedido basado en TODAS sus rutas
     */
    private void verificarEstadoPedido(Pedido pedido, List<RutaAsignada> rutasDelPedido, LocalDateTime ahora) {
        Integer tramoActual = pedido.getTramoActual();
        if (tramoActual == null) tramoActual = 0;

        // Verificar si al menos UNA ruta ya despegó
        boolean algunaDespego = false;
        boolean todasAterrizaron = true;
        boolean todasEntregadas = true;

        for (RutaAsignada ruta : rutasDelPedido) {
            if (tramoActual >= ruta.getTramos().size()) continue;

            RutaTramo tramo = ruta.getTramos().get(tramoActual);
            LocalDateTime horaSalida = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraSalida()));
            LocalDateTime horaLlegada = LocalDateTime.of(tramo.getFecha(), LocalTime.parse(tramo.getHoraLlegada()));

            if (horaLlegada.isBefore(horaSalida)) {
                horaLlegada = horaLlegada.plusDays(1);
            }

            if (!ahora.isBefore(horaSalida)) {
                algunaDespego = true;
            }

            if (ahora.isBefore(horaLlegada)) {
                todasAterrizaron = false;
            }

            if (tramoActual < ruta.getTramos().size() - 1) {
                todasEntregadas = false;
            }
        }

        // TRANSICIONES DE ESTADO
        if (pedido.getEstado() == EstadoPedido.ASIGNADO && algunaDespego) {
            pedido.setEstado(EstadoPedido.EN_TRANSITO);
        } else if (pedido.getEstado() == EstadoPedido.EN_TRANSITO && todasAterrizaron) {
            if (todasEntregadas) {
                pedido.setEstado(EstadoPedido.ENTREGADO);
                pedido.setHoraEntrega(ahora);
                agregarEvento("✅ Pedido #" + pedido.getId() + " entregado completamente");
            } else {
                pedido.setEstado(EstadoPedido.EN_ALMACEN_INTERMEDIO);
                pedido.setTramoActual(tramoActual + 1);
            }
        } else if (pedido.getEstado() == EstadoPedido.EN_ALMACEN_INTERMEDIO) {
            // Verificar si el siguiente tramo ya despegó
            boolean siguienteDespego = false;
            for (RutaAsignada ruta : rutasDelPedido) {
                if (tramoActual + 1 < ruta.getTramos().size()) {
                    RutaTramo siguienteTramo = ruta.getTramos().get(tramoActual + 1);
                    LocalDateTime siguienteSalida = LocalDateTime.of(siguienteTramo.getFecha(),
                            LocalTime.parse(siguienteTramo.getHoraSalida()));
                    if (!ahora.isBefore(siguienteSalida)) {
                        siguienteDespego = true;
                        break;
                    }
                }
            }

            if (siguienteDespego) {
                pedido.setEstado(EstadoPedido.EN_TRANSITO);
            }
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

            // ✅ NUEVO: Eliminar ocupación temporal (ya llegó físicamente)
            try {
                almacenOcupacionService.eliminarOcupacion(pedido.getId(), tramo.getDestino());
            } catch (Exception e) {
                System.err.println("⚠️ Error eliminando ocupación temporal: " + e.getMessage());
            }

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
        LocalDateTime tiempoActual = tiempoSimuladoService.obtenerTiempoActual();

        // Formatear la hora de forma segura (HH:mm:ss)
        String horaStr = String.format("%02d:%02d:%02d",
                tiempoActual.getHour(),
                tiempoActual.getMinute(),
                tiempoActual.getSecond()
        );

        eventosRecientes.add(horaStr + " - " + mensaje);

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