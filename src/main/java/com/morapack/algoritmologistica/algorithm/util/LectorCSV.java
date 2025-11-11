package com.morapack.algoritmologistica.algorithm.util;

import com.morapack.algoritmologistica.algorithm.models.Aeropuerto;
import com.morapack.algoritmologistica.algorithm.models.EstadoPedido;
import com.morapack.algoritmologistica.algorithm.models.Pedido;
import com.morapack.algoritmologistica.algorithm.models.Vuelo;
import com.morapack.backend.entity.AeropuertoEntity;
import com.morapack.backend.repository.AeropuertoRepository;
import com.morapack.backend.repository.PedidoRepository;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LectorCSV {


    /**
     * Lee aeropuertos directamente desde la base de datos
     * con capacidad DISPONIBLE (máxima - ocupada)
     * Para operaciones día a día en tiempo real
     *
     * @param repository Repositorio de aeropuertos
     * @return Lista de aeropuertos con capacidad ajustada
     */
    public static List<Aeropuerto> leerAeropuertosDesdeDB(
            AeropuertoRepository aeropuertoRepository,
            PedidoRepository pedidoRepository
    ) {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        try {
            List<AeropuertoEntity> entities = aeropuertoRepository.findAll();

            // ✅ Obtener todos los pedidos ASIGNADOS de una sola vez
            List<Pedido> pedidosAsignados = pedidoRepository.findByEstadoIn(
                    List.of(EstadoPedido.ASIGNADO, EstadoPedido.EN_TRANSITO)  // ← Ambos
            );

            // ✅ Calcular ocupación reservada por aeropuerto
            Map<String, Integer> ocupacionReservada = new HashMap<>();
            for (Pedido pedido : pedidosAsignados) {
                String destino = pedido.getAeropuertoDestino();
                ocupacionReservada.put(
                        destino,
                        ocupacionReservada.getOrDefault(destino, 0) + pedido.getCantidad()
                );
            }

            for (AeropuertoEntity entity : entities) {
                // ✅ Calcular capacidad DISPONIBLE (ocupada + reservada)
                int capacidadMaxima = entity.getCapacidad();
                int capacidadOcupada = entity.getCapacidadActual();
                int reservada = ocupacionReservada.getOrDefault(entity.getCodigo(), 0);
                int ocupacionTotal = capacidadOcupada + reservada;
                int capacidadDisponible = Math.max(0, capacidadMaxima - ocupacionTotal);

                // Crear aeropuerto con capacidad disponible como "máxima"
                Aeropuerto aeropuerto = new Aeropuerto(
                        entity.getCodigo(),
                        entity.getNombre(),
                        entity.getPais(),
                        capacidadDisponible,  // ← Capacidad disponible
                        entity.getHusoHorario(),
                        entity.getContinente()
                );

                // Desde la perspectiva del algoritmo, empieza vacío
                aeropuerto.setCapacidadActual(0);

                aeropuertos.add(aeropuerto);

                // Log para debug
                System.out.println("   📦 " + entity.getCodigo() +
                        " - Ocupado: " + capacidadOcupada +
                        " + Reservado: " + reservada +
                        " = Total: " + ocupacionTotal +
                        " | Disponible: " + capacidadDisponible);
            }

            System.out.println("✅ Aeropuertos cargados desde BD: " + aeropuertos.size());
            System.out.println("   (Capacidad ajustada según ocupación actual + reservas)");

        } catch (Exception e) {
            System.err.println("❌ Error al leer aeropuertos desde BD: " + e.getMessage());
            e.printStackTrace();
        }

        return aeropuertos;
    }

    /**
     * Lee el archivo de aeropuertos desde resources
     * Formato: codigo,nombre,pais,capacidad,capacidadAct,husoHorario,continente
     * @param rutaArchivo Ruta del archivo CSV (ej: "data/aeropuertos.csv")
     * @return Lista de aeropuertos
     */
    public static List<Aeropuerto> leerAeropuertos(String rutaArchivo) {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        try {
            // Leer desde classpath (resources)
            ClassPathResource resource = new ClassPathResource(rutaArchivo);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), "UTF-8")
            );

            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {
                // Saltar encabezado
                if (primeraLinea) {
                    primeraLinea = false;
                    continue;
                }

                // Saltar líneas vacías
                if (linea.trim().isEmpty()) {
                    continue;
                }

                String[] datos = linea.split(",");

                if (datos.length >= 7) {
                    String codigo = datos[0].trim();
                    String nombre = datos[1].trim();
                    String pais = datos[2].trim();
                    int capacidad = Integer.parseInt(datos[3].trim());
                    int husoHorario = Integer.parseInt(datos[5].trim());
                    String continente = datos[6].trim();

                    Aeropuerto aeropuerto = new Aeropuerto(codigo, nombre, pais,
                            capacidad, husoHorario, continente);
                    aeropuertos.add(aeropuerto);
                }
            }

            br.close();
            System.out.println("✅ Aeropuertos cargados: " + aeropuertos.size());

        } catch (IOException e) {
            System.err.println("❌ Error al leer archivo de aeropuertos: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("❌ Error al parsear datos de aeropuertos: " + e.getMessage());
            e.printStackTrace();
        }

        return aeropuertos;
    }

    /**
     * Lee el archivo de pedidos desde resources
     * Formato: dd-hh-mm-DEST-###-IdCliente
     * @param rutaArchivo Ruta del archivo (ej: "data/pedidos_m.txt")
     * @return Lista de pedidos
     */
    public static List<Pedido> leerPedidos(String rutaArchivo) {
        List<Pedido> pedidos = new ArrayList<>();

        try {
            // Leer desde classpath (resources)
            ClassPathResource resource = new ClassPathResource(rutaArchivo);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), "UTF-8")
            );

            String linea;

            while ((linea = br.readLine()) != null) {
                // Saltar líneas vacías
                if (linea.trim().isEmpty()) {
                    continue;
                }

                // Parsear formato: dd-hh-mm-DEST-###-IdCliente
                String[] partes = linea.trim().split("-");

                if (partes.length >= 6) {
                    int dia = Integer.parseInt(partes[0]);
                    int hora = Integer.parseInt(partes[1]);
                    int minuto = Integer.parseInt(partes[2]);
                    String destino = partes[3];
                    int cantidad = Integer.parseInt(partes[4]);
                    String idCliente = partes[5];

                    Pedido pedido = new Pedido(dia, hora, minuto, destino, cantidad, idCliente);
                    pedidos.add(pedido);
                }
            }

            br.close();
            System.out.println("✅ Pedidos cargados: " + pedidos.size());

        } catch (IOException e) {
            System.err.println("❌ Error al leer archivo de pedidos: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("❌ Error al parsear datos de pedidos: " + e.getMessage());
            e.printStackTrace();
        }

        return pedidos;
    }

    /**
     * Lee el archivo de vuelos y genera instancias para la semana específica
     * @param rutaArchivo Ruta del archivo (ej: "data/vuelos.txt")
     * @param aeropuertos Lista de aeropuertos
     * @param startTime Fecha de inicio de la semana (hora Lima)
     * @return Lista de vuelos (7 instancias por cada plan de vuelo)
     */
    public static List<Vuelo> leerVuelos(String rutaArchivo, List<Aeropuerto> aeropuertos,
                                         LocalDateTime startTime) {
        List<Vuelo> vuelos = new ArrayList<>();

        // Crear mapa para búsqueda rápida de aeropuertos
        Map<String, Aeropuerto> mapaAeropuertos = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            mapaAeropuertos.put(a.getCodigo(), a);
        }

        try {
            ClassPathResource resource = new ClassPathResource(rutaArchivo);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), "UTF-8")
            );

            String linea;
            int planesLeidos = 0;

            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty()) {
                    continue;
                }

                String[] partes = linea.trim().split("-");

                if (partes.length >= 5) {
                    String codigoOrigen = partes[0];
                    String codigoDestino = partes[1];
                    String[] horaSalidaParts = partes[2].split(":");
                    String[] horaLlegadaParts = partes[3].split(":");
                    int capacidad = Integer.parseInt(partes[4]);

                    Aeropuerto origen = mapaAeropuertos.get(codigoOrigen);
                    Aeropuerto destino = mapaAeropuertos.get(codigoDestino);

                    if (origen == null || destino == null) {
                        System.err.println("⚠️ Aeropuerto no encontrado en línea: " + linea);
                        continue;
                    }

                    int horaSalida = Integer.parseInt(horaSalidaParts[0]);
                    int minutoSalida = Integer.parseInt(horaSalidaParts[1]);
                    int horaLlegada = Integer.parseInt(horaLlegadaParts[0]);
                    int minutoLlegada = Integer.parseInt(horaLlegadaParts[1]);

                    // ✅ CREAR 7 INSTANCIAS DEL VUELO PARA LA SEMANA ESPECÍFICA
                    for (int dia = 0; dia < 7; dia++) {
                        LocalDateTime fechaSalida = startTime
                                .plusDays(dia)
                                .withHour(horaSalida)
                                .withMinute(minutoSalida)
                                .withSecond(0)
                                .withNano(0);

                        LocalDateTime fechaLlegada = startTime
                                .plusDays(dia)
                                .withHour(horaLlegada)
                                .withMinute(minutoLlegada)
                                .withSecond(0)
                                .withNano(0);

                        // Si la hora de llegada es menor que la de salida, es del día siguiente
                        if (fechaLlegada.isBefore(fechaSalida)) {
                            fechaLlegada = fechaLlegada.plusDays(1);
                        }

                        Vuelo vuelo = new Vuelo(origen, destino, fechaSalida, fechaLlegada, capacidad);
                        vuelos.add(vuelo);
                    }

                    planesLeidos++;
                }
            }

            br.close();
            System.out.println("✅ Planes de vuelo leídos: " + planesLeidos);
            System.out.println("✅ Instancias de vuelos generadas: " + vuelos.size() + " (7 días)");
            System.out.println("📅 Semana: " + startTime + " → " + startTime.plusDays(7));

        } catch (IOException e) {
            System.err.println("❌ Error al leer archivo de vuelos: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("❌ Error al parsear datos de vuelos: " + e.getMessage());
            e.printStackTrace();
        }

        return vuelos;
    }

    /**
     * Identifica las sedes principales a partir de códigos dados
     * @param aeropuertos Lista de todos los aeropuertos
     * @param codigosSedes Lista de códigos de sedes (ej: ["SPIM", "EBCI", "UBBB"])
     * @return Lista de aeropuertos que son sedes principales
     */
    public static List<Aeropuerto> identificarSedesPrincipales(List<Aeropuerto> aeropuertos,
                                                               List<String> codigosSedes) {
        List<Aeropuerto> sedes = new ArrayList<>();

        for (String codigo : codigosSedes) {
            for (Aeropuerto a : aeropuertos) {
                if (a.getCodigo().equals(codigo)) {
                    sedes.add(a);
                    break;
                }
            }
        }

        System.out.println("✅ Sedes principales identificadas: " + sedes.size());
        for (Aeropuerto sede : sedes) {
            System.out.println("   - " + sede.getNombre() + " (" + sede.getCodigo() + ")");
        }

        return sedes;
    }
}