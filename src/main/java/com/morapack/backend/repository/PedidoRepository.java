package com.morapack.backend.repository;

import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Encuentra pedidos que caen dentro de una semana específica
     *
     */
    @Query("SELECT p FROM Pedido p WHERE " +
            "p.anho = :anho AND " +
            "(p.mes > :mesInicio OR (p.mes = :mesInicio AND p.dia >= :diaInicio)) AND " +
            "(p.mes < :mesFin OR (p.mes = :mesFin AND p.dia < :diaFin)) " +
            "ORDER BY p.mes, p.dia, p.hora, p.minuto " +
            "LIMIT 70000")
    List<Pedido> findPedidosByWeek(
            @Param("anho") int anho,
            @Param("mesInicio") int mesInicio,
            @Param("diaInicio") int diaInicio,
            @Param("mesFin") int mesFin,
            @Param("diaFin") int diaFin
    );

    /**
     * Busca pedidos NO_ASIGNADO
     *
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'NO_ASIGNADO' " +
            "ORDER BY p.anho, p.mes, p.dia, p.hora, p.minuto " +
            "LIMIT 70000")
    List<Pedido> findPendientes();

    /**
     * Busca pedidos por estados específicos
     *
     * Usado por OperacionesDiaDiaService
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado IN :estados " +
            "ORDER BY p.id DESC " +
            "LIMIT 70000")
    List<Pedido> findByEstadoIn(@Param("estados") List<EstadoPedido> estados);

    /**
     * Obtiene el ID máximo actual
     * Usado para generar nuevos IDs en importación masiva
     */
    @Query("SELECT MAX(p.id) FROM Pedido p")
    Optional<Long> findMaxId();

    /*@Query("SELECT p FROM Pedido p ORDER BY p.id DESC")
    List<Pedido> findRecientes(Pageable pageable);*/

    @Query(value = "SELECT id, id_cliente, aeropuerto_destino, cantidad FROM pedido ORDER BY id DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecientesSimple(@Param("limit") int limit);

    /**
     *
     * Para el planificador automático (ventana de 72 horas)
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'NO_ASIGNADO' " +
            "AND ((p.anho = :anhoInicio AND p.mes = :mesInicio AND p.dia >= :diaInicio) " +
            "     OR (p.anho = :anhoInicio AND p.mes > :mesInicio) " +
            "     OR (p.anho > :anhoInicio)) " +
            "AND ((p.anho = :anhoFin AND p.mes = :mesFin AND p.dia <= :diaFin) " +
            "     OR (p.anho = :anhoFin AND p.mes < :mesFin) " +
            "     OR (p.anho < :anhoFin)) " +
            "ORDER BY p.anho, p.mes, p.dia, p.hora, p.minuto " +
            "LIMIT 70000")
    List<Pedido> findNoAsignadosEnRango(
            @Param("anhoInicio") int anhoInicio,
            @Param("mesInicio") int mesInicio,
            @Param("diaInicio") int diaInicio,
            @Param("anhoFin") int anhoFin,
            @Param("mesFin") int mesFin,
            @Param("diaFin") int diaFin
    );

    /**
     * ✅ NUEVO: Busca pedidos NO_ASIGNADO en un rango PRECISO de fecha/hora/minuto
     * Para operaciones día a día con ventana de 15 minutos
     *
     * Ejemplo: Rango de 20:45 a 21:00 del 01/12/2025
     * Solo traerá pedidos entre esas horas exactas, no todo el día
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'NO_ASIGNADO' " +
            "AND (" +
            // Comparación usando fecha/hora como número: YYYYMMDDHHMM
            "   (p.anho * 100000000 + p.mes * 1000000 + p.dia * 10000 + p.hora * 100 + p.minuto) " +
            "   BETWEEN " +
            "   (:anhoInicio * 100000000 + :mesInicio * 1000000 + :diaInicio * 10000 + :horaInicio * 100 + :minutoInicio) " +
            "   AND " +
            "   (:anhoFin * 100000000 + :mesFin * 1000000 + :diaFin * 10000 + :horaFin * 100 + :minutoFin) " +
            ") " +
            "ORDER BY p.anho, p.mes, p.dia, p.hora, p.minuto " +
            "LIMIT 70000")
    List<Pedido> findNoAsignadosEnRangoPreciso(
            @Param("anhoInicio") int anhoInicio,
            @Param("mesInicio") int mesInicio,
            @Param("diaInicio") int diaInicio,
            @Param("horaInicio") int horaInicio,
            @Param("minutoInicio") int minutoInicio,
            @Param("anhoFin") int anhoFin,
            @Param("mesFin") int mesFin,
            @Param("diaFin") int diaFin,
            @Param("horaFin") int horaFin,
            @Param("minutoFin") int minutoFin
    );

    /**
     *
     * Solo trae los que tienen hora_entrega registrada
     */
    @Query("SELECT p FROM Pedido p WHERE p.estado = 'ENTREGADO' " +
            "AND p.horaEntrega IS NOT NULL " +
            "ORDER BY p.horaEntrega ASC " +
            "LIMIT 70000")
    List<Pedido> findEntregadosParaLimpieza();


    @Query("SELECT p FROM Pedido p ORDER BY p.id DESC LIMIT 70000")
    List<Pedido> findAllWithLimit();

    /**
     *
     * Solo trae pedidos cuyo próximo vuelo está cerca de despegar/aterrizar
     */
    @Query(value = """
    SELECT DISTINCT p.* FROM pedido p
    INNER JOIN rutas_asignadas ra ON p.id = ra.pedido_id
    INNER JOIN rutas_tramo rt ON ra.id = rt.ruta_id AND rt.orden = p.tramo_actual
    WHERE p.estado IN ('ASIGNADO', 'EN_TRANSITO', 'EN_ALMACEN_INTERMEDIO')
    AND (
        -- Caso 1: ASIGNADO - vuelo despega en próximas 2h (mismo día O día siguiente)
        (p.estado = 'ASIGNADO' AND (
            (rt.fecha = :fecha AND rt.hora_salida >= :horaActual)
            OR (rt.fecha = :fechaManana AND rt.hora_salida <= :horaLimite)
        ))
        
        -- Caso 2: EN_TRANSITO - vuelo ya despegó
        OR (p.estado = 'EN_TRANSITO' AND rt.fecha >= :fechaAyer)
        
        -- Caso 3: EN_ALMACEN - próximo vuelo sale en próximas 2h
        OR (p.estado = 'EN_ALMACEN_INTERMEDIO' AND (
            (rt.fecha = :fecha AND rt.hora_salida >= :horaActual)
            OR (rt.fecha = :fechaManana AND rt.hora_salida <= :horaLimite)
        ))
    )
    LIMIT 10000
    """, nativeQuery = true)
    List<Pedido> findActivosConVuelosProximos(
            @Param("fecha") String fecha,
            @Param("fechaManana") String fechaManana,
            @Param("horaActual") String horaActual,
            @Param("horaLimite") String horaLimite,
            @Param("fechaAyer") String fechaAyer
    );

    /**
     *
     */
    @Query("SELECT p.estado, COUNT(p) FROM Pedido p GROUP BY p.estado")
    List<Object[]> countByEstadoGrouped();

    /**
     *
     * Un pedido está en almacén cuando:
     * - Estado = EN_ALMACEN_INTERMEDIO (parte de operaciones día a día)
     * - El destino del tramo ANTERIOR (tramo_actual - 1) es el código del almacén
     * - Tiene ruta asignada (operaciones día a día)
     * Nota: tramo_actual apunta al SIGUIENTE vuelo, no al actual
     * Excluye: NO_ASIGNADO, ENTREGADO, RECOGIDO (no están en operaciones activas)
     */
    @Query(value = """
    SELECT DISTINCT p.* FROM pedido p
    INNER JOIN rutas_asignadas ra ON p.id = ra.pedido_id
    INNER JOIN rutas_tramo rt ON ra.id = rt.ruta_id AND rt.orden = p.tramo_actual - 1
    WHERE p.estado = 'EN_ALMACEN_INTERMEDIO'
    AND rt.destino = :codigoAlmacen
    ORDER BY p.id DESC
    LIMIT 10000
    """, nativeQuery = true)
    List<Pedido> findPedidosEnAlmacen(@Param("codigoAlmacen") String codigoAlmacen);

    /**
     * ✅ OPTIMIZACIÓN: Buscar pedidos EN_TRANSITO con JOIN de rutas (evita N+1)
     */
    @Query(value = """
    SELECT DISTINCT p.* FROM pedido p
    INNER JOIN rutas_asignadas ra ON p.id = ra.pedido_id
    INNER JOIN rutas_tramo rt ON ra.id = rt.ruta_id AND rt.orden = p.tramo_actual
    WHERE p.estado = 'EN_TRANSITO'
    AND (
        -- Caso 1: Vuelo llega hoy después de la hora actual
        (rt.fecha = :fecha AND rt.hora_llegada >= :horaActual)
        
        -- Caso 2: Vuelo llega hoy pero antes de la hora actual (cruzó medianoche)
        OR (rt.fecha = :fecha AND rt.hora_llegada < :horaActual AND rt.hora_salida > rt.hora_llegada)
        
        -- Caso 3: Vuelo llega mañana
        OR (rt.fecha > :fecha)
    )
    LIMIT 1000
    """, nativeQuery = true)
    List<Pedido> findEnTransitoConVuelosActivos(
            @Param("fecha") String fecha,
            @Param("horaActual") String horaActual
    );

    /**
     *
     * Un pedido está en un vuelo cuando:
     * - Estado = EN_TRANSITO
     * - El tramo actual coincide con origen, destino, fecha y hora del vuelo
     */
    @Query(value = """
    SELECT DISTINCT p.* FROM pedido p
    INNER JOIN rutas_asignadas ra ON p.id = ra.pedido_id
    INNER JOIN rutas_tramo rt ON ra.id = rt.ruta_id AND rt.orden = p.tramo_actual
    WHERE p.estado = 'EN_TRANSITO'
    AND rt.origen = :origen
    AND rt.destino = :destino
    AND rt.fecha = :fecha
    AND rt.hora_salida = :hora
    ORDER BY p.id DESC
    LIMIT 10000
    """, nativeQuery = true)
    List<Pedido> findPedidosEnVuelo(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("fecha") String fecha,
            @Param("hora") String hora
    );



}