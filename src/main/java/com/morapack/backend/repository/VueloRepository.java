package com.morapack.backend.repository;



import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface VueloRepository extends JpaRepository<Vuelo, Long> {
    // Aquí puedes agregar métodos de búsqueda específicos si los necesitas

    // Ejemplos:
     List<Vuelo> findByOrigen(String origen);


     List<Vuelo> findByDestino(String destino);






}
