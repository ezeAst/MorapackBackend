package com.morapack.backend.repository;

import com.morapack.backend.entity.AeropuertoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AeropuertoRepository extends JpaRepository<AeropuertoEntity, Long> {

    Optional<AeropuertoEntity> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    // Insertar aeropuerto
    @org.springframework.data.jpa.repository.query.Procedure("insertar_aeropuerto")
    void insertarAeropuerto(Long almacen_id, Long pedido_id, String tipo, Integer cantidad_paquetes, java.sql.Timestamp fecha_hora);

    // Actualizar aeropuerto
    @org.springframework.data.jpa.repository.query.Procedure("actualizar_aeropuerto")
    void actualizarAeropuerto(Long id, Long almacen_id, Long pedido_id, String tipo, Integer cantidad_paquetes, java.sql.Timestamp fecha_hora);

    // Eliminar aeropuerto
    @org.springframework.data.jpa.repository.query.Procedure("eliminar_aeropuerto")
    void eliminarAeropuerto(Long id);

    // Filtrar aeropuertos
    @org.springframework.data.jpa.repository.query.Procedure("filtrar_aeropuertos")
    java.util.List<AeropuertoEntity> filtrarAeropuertos(Long almacen_id, Long pedido_id, String tipo);

    // Listar todos los aeropuertos usando procedimiento
    @org.springframework.data.jpa.repository.query.Procedure("listar_todos_aeropuertos")
    java.util.List<AeropuertoEntity> listarTodosAeropuertos();
}