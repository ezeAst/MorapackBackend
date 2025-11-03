package com.morapack.algoritmologistica.algorithm.solver;

import com.morapack.algoritmologistica.algorithm.models.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class GRASP {

    // === Atributos ===
    private List<Pedido> pedidos;                    // Pedidos a planificar
    private List<Vuelo> vuelos;                      // Todos los vuelos disponibles de la semana
    private List<Aeropuerto> aeropuertos;            // Todos los aeropuertos
    private List<Aeropuerto> sedesPrincipales;       // Lima, Bruselas, Baku

    // Parámetros de GRASP
    private double alpha;                             // Parámetro de aleatorización (0.0 a 1.0)
    private int tamanoRCL;                           // Tamaño de la Lista de Candidatos Restringida
    private Map<String, List<Vuelo>> cacheRutasGlobal;

    private Map<String, List<Vuelo>> vuelosPorOrigen;
    // === Constructores ===
    public GRASP() {
        this.pedidos = new ArrayList<>();
        this.vuelos = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
        this.sedesPrincipales = new ArrayList<>();
        this.alpha = 0.3;           // Valor por defecto
        this.tamanoRCL = 3;         // Valor por defecto
        this.vuelosPorOrigen = new HashMap<>();
    }

    public GRASP(List<Pedido> pedidos, List<Vuelo> vuelos,
                 List<Aeropuerto> aeropuertos, List<Aeropuerto> sedesPrincipales,
                 double alpha, int tamanoRCL) {
        this.pedidos = pedidos;
        this.vuelos = vuelos;
        this.aeropuertos = aeropuertos;
        this.sedesPrincipales = sedesPrincipales;
        this.alpha = alpha;
        this.tamanoRCL = tamanoRCL;
        inicializarIndiceVuelos();
        this.cacheRutasGlobal = new HashMap<>();
    }

    // === Getters y Setters ===
    public List<Pedido> getPedidos() {
        return pedidos;
    }

    public void setPedidos(List<Pedido> pedidos) {
        this.pedidos = pedidos;
    }

    public List<Vuelo> getVuelos() {
        return vuelos;
    }

    public void setVuelos(List<Vuelo> vuelos) {
        this.vuelos = vuelos;
    }

    public List<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void setAeropuertos(List<Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos;
    }

    public List<Aeropuerto> getSedesPrincipales() {
        return sedesPrincipales;
    }

    public void setSedesPrincipales(List<Aeropuerto> sedesPrincipales) {
        this.sedesPrincipales = sedesPrincipales;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public int getTamanoRCL() {
        return tamanoRCL;
    }

    public void setTamanoRCL(int tamanoRCL) {
        this.tamanoRCL = tamanoRCL;
    }

    // === Método principal ===

    /**
     * Genera una solución usando GRASP.
     * @return Una solución construida de manera greedy con aleatorización
     */
    public Solucion generarSolucion(int year) { // ← NUEVO parámetro
        long startTime = System.currentTimeMillis();
        Solucion solucion = new Solucion();

        for (Pedido pedido : pedidos) {
            int cantidadRestante = pedido.getCantidad();
            int intentos = 0;
            int maxIntentos = 5;

            while (cantidadRestante > 0 && intentos < maxIntentos) {
                intentos++;

                Aeropuerto aeropuertoDestino = buscarAeropuertoPorCodigo(pedido.getAeropuertoDestino());
                if (aeropuertoDestino == null) {
                    break;
                }

                List<OpcionSede> opciones = new ArrayList<>();

                // ✅ CONSTRUIR FECHA USANDO EL AÑO PASADO
                LocalDateTime fechaPedido = pedido.getFechaPedido(year);

                for (Aeropuerto sede : sedesPrincipales) {
                    int plazo = determinarPlazo(sede, aeropuertoDestino);
                    List<Vuelo> ruta = buscarRutaOptima(sede, aeropuertoDestino, fechaPedido, plazo);

                    if (ruta != null) {
                        if (cumplePlazo(ruta, fechaPedido, aeropuertoDestino, plazo)) {
                            double score = calcularScore(ruta, plazo);
                            opciones.add(new OpcionSede(sede, ruta, score));
                        } else {
                            System.out.println("ADVERTENCIA: Ruta desde " + sede.getCodigo() +
                                    " excede el plazo de " + plazo + " días para pedido " +
                                    pedido.getIdCliente());
                        }
                    }
                }

                List<OpcionSede> rcl = crearRCL(opciones);

                if (rcl.isEmpty()) {
                    System.out.println("ERROR: No hay rutas factibles para pedido " + pedido.getIdCliente() +
                            " (intento " + intentos + ")");
                    break;
                }

                List<Ruta> rutasDelPedido = asignarProductosConRCL(pedido, rcl);

                if (rutasDelPedido.isEmpty()) {
                    System.out.println("ERROR: No se pudo asignar ningún producto del pedido " +
                            pedido.getIdCliente() + " (intento " + intentos + ")");
                    break;
                }

                for (Ruta ruta : rutasDelPedido) {
                    solucion.agregarRuta(ruta);
                    StringBuilder sb = new StringBuilder();
                    sb.append("INFO: Se le agregó la ruta al pedido ")
                            .append(pedido.getIdCliente())
                            .append(" con ")
                            .append(ruta.getCantidad())
                            .append(" paquetes. Ruta: ");
                    for (Vuelo vuelo : ruta.getVuelos()) {
                        sb.append(vuelo.getAeropuertoOrigen().getPais())
                                .append(" -> ");
                    }
                    sb.append(" Con Hora ").append(ruta.getVuelos().getLast().getHoraLlegada());
                    if (!ruta.getVuelos().isEmpty()) {
                        sb.append(ruta.getVuelos().get(ruta.getVuelos().size()-1).getAeropuertoDestino().getPais());
                    }
                    System.out.println(sb.toString());
                }

                int asignadosAhora = 0;
                for (Ruta ruta : rutasDelPedido) {
                    asignadosAhora += ruta.getCantidad();
                }
                pedido.setCantidadCumplida(pedido.getCantidadCumplida() + asignadosAhora);
                cantidadRestante -= asignadosAhora;
            }

            if (cantidadRestante > 0) {
                System.out.println("ERROR CRÍTICO: Pedido " + pedido.getIdCliente() +
                        " NO completado. Quedan " + cantidadRestante +
                        " productos sin asignar después de " + intentos + " intentos.");
            }
        }

        solucion.evaluarSolucion(pedidos, vuelos, aeropuertos);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("\n⏱️ TIEMPO DE EJECUCIÓN GRASP: " + duration + " ms (" +
                (duration / 1000.0) + " segundos)");

        return solucion;
    }

    private int determinarPlazo(Aeropuerto sede, Aeropuerto destino) {
        if (sede.getContinente().equals(destino.getContinente())) {
            return 2; // Mismo continente
        } else {
            return 3; // Distinto continente
        }
    }

    @Override
    public String toString() {
        return "GRASP{" +
                "numPedidos=" + pedidos.size() +
                ", numVuelos=" + vuelos.size() +
                ", numAeropuertos=" + aeropuertos.size() +
                ", alpha=" + alpha +
                ", tamanoRCL=" + tamanoRCL +
                '}';
    }

    private Aeropuerto buscarAeropuertoPorCodigo(String codigo) {
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCodigo().equals(codigo)) {
                return aeropuerto;
            }
        }
        return null; // No encontrado
    }

    // 1. Convertir a UTC para comparaciones
    private LocalDateTime convertirAUTC(LocalDateTime fechaLocal, int husoHorario) {
        return fechaLocal.minusHours(husoHorario);
    }

    // 2. Calcular duración entre dos momentos considerando zonas horarias
    private long calcularDuracionHoras(LocalDateTime inicio, int husoInicio,
                                       LocalDateTime fin, int husoFin) {
        LocalDateTime inicioUTC = convertirAUTC(inicio, husoInicio);
        LocalDateTime finUTC = convertirAUTC(fin, husoFin);
        return Duration.between(inicioUTC, finUTC).toHours();
    }

    // 3. Obtener vuelos disponibles desde un aeropuerto después de cierto momento
    private List<Vuelo> obtenerVuelosDisponibles(Aeropuerto origen, LocalDateTime despuesDe) {
        List<Vuelo> vuelosDisponibles = new ArrayList<>();

        // ← USAR EL ÍNDICE en vez de iterar todos los vuelos
        List<Vuelo> vuelosDesdeOrigen = vuelosPorOrigen.get(origen.getCodigo());

        if (vuelosDesdeOrigen == null) {
            return vuelosDisponibles;  // No hay vuelos desde este origen
        }

        for (Vuelo v : vuelosDesdeOrigen) {  // ← Solo itera ~7 vuelos en vez de 210
            if (v.getHoraSalida().isAfter(despuesDe) || v.getHoraSalida().isEqual(despuesDe)) {
                vuelosDisponibles.add(v);
            }
        }
        return vuelosDisponibles;
    }

    // 4. Validar si hay tiempo suficiente para conexión
    private boolean esConexionValida(LocalDateTime llegada, LocalDateTime salida) {
        Duration espera = Duration.between(llegada, salida);
        return espera.toHours() >= 1;
    }

    private class NodoRuta implements Comparable<NodoRuta> {
        Aeropuerto aeropuerto;           // Aeropuerto actual
        LocalDateTime tiempoLlegada;     // Cuándo llegamos a este aeropuerto
        long tiempoAcumuladoHoras;       // Tiempo total desde el origen
        List<Vuelo> rutaHastaAqui;       // Vuelos tomados hasta este punto
        int numeroEscalas;

        public NodoRuta(Aeropuerto aeropuerto, LocalDateTime tiempoLlegada,
                        long tiempoAcumuladoHoras, List<Vuelo> rutaHastaAqui, int numeroEscalas) {
            this.aeropuerto = aeropuerto;
            this.tiempoLlegada = tiempoLlegada;
            this.tiempoAcumuladoHoras = tiempoAcumuladoHoras;
            this.rutaHastaAqui = new ArrayList<>(rutaHastaAqui);
            this.numeroEscalas = numeroEscalas;
        }

        @Override
        public int compareTo(NodoRuta otro) {
            return Long.compare(this.tiempoAcumuladoHoras, otro.tiempoAcumuladoHoras);
        }
    }

    private List<Vuelo> buscarRutaOptima(Aeropuerto origen, Aeropuerto destino,
                                         LocalDateTime fechaInicio, int plazoMaximoDias) {

        // ← AGREGAR: Generar clave de caché
        String cacheKey = origen.getCodigo() + "-" + destino.getCodigo() + "-" +
                fechaInicio.getDayOfMonth();  // Agrupamos por día

        // ← AGREGAR: Verificar si ya tenemos esta ruta en caché
        if (cacheRutasGlobal.containsKey(cacheKey)) {
            List<Vuelo> rutaCacheada = cacheRutasGlobal.get(cacheKey);

            // Validar que la ruta sigue siendo factible (capacidades)
            boolean esFactible = true;
            for (Vuelo v : rutaCacheada) {
                if (v.getCapacidadActual() >= v.getCapacidadMaxima()) {
                    esFactible = false;
                    break;
                }
            }

            if (esFactible) {
                return rutaCacheada;  // ← Retornar ruta del caché
            }
        }

        // Priority Queue ordenada por tiempo acumulado
        PriorityQueue<NodoRuta> cola = new PriorityQueue<>();

        // Set de aeropuertos visitados (para evitar ciclos)
        Set<String> visitados = new HashSet<>();


        LocalDateTime fechaInicioEnZonaOrigen = convertirEntreZonas(
                fechaInicio,
                destino.getHusoHorario(),  // Zona horaria del pedido (destino)
                origen.getHusoHorario()     // Zona horaria de la sede de origen
        );

        // Nodo inicial
        NodoRuta nodoInicial = new NodoRuta(origen, fechaInicioEnZonaOrigen, 0, new ArrayList<>(),0);
        cola.add(nodoInicial);

        long plazoMaximoHoras = plazoMaximoDias * 24;

        while (!cola.isEmpty()) {
            NodoRuta actual = cola.poll();

            // Si llegamos al destino
            if (actual.aeropuerto.getCodigo().equals(destino.getCodigo())) {
                // GUARDAR EN CACHÉ ANTES DE RETORNAR
                cacheRutasGlobal.put(cacheKey, actual.rutaHastaAqui);  // ← Solo usa la variable que ya existe

                return actual.rutaHastaAqui;
            }

            if (actual.numeroEscalas > 3) {  // Máximo 3 escalas
                continue;
            }

            // Si ya visitamos este aeropuerto, skip
            if (visitados.contains(actual.aeropuerto.getCodigo())) {
                continue;
            }
            visitados.add(actual.aeropuerto.getCodigo());

            // Si excedemos el plazo máximo, skip
            if (actual.tiempoAcumuladoHoras > plazoMaximoHoras) {
                continue;
            }

            LocalDateTime tiempoMinimo;

            if (actual.tiempoAcumuladoHoras == 0) {
                // Nodo inicial (sede principal) - sin tiempo de espera adicional
                tiempoMinimo = actual.tiempoLlegada;
            } else {
                // Escala/conexión - mínimo 1 hora de espera
                tiempoMinimo = actual.tiempoLlegada.plusHours(1);
            }

            // Explorar vuelos disponibles desde este aeropuerto
            List<Vuelo> vuelosDesdeAqui = obtenerVuelosDisponibles(
                    actual.aeropuerto,
                    tiempoMinimo // Mínimo 1 hora de espera
            );

            for (Vuelo vuelo : vuelosDesdeAqui) {
                // Validar conexión (mínimo 1 hora)

                if(actual.tiempoAcumuladoHoras!=0){
                    if (!esConexionValida(actual.tiempoLlegada, vuelo.getHoraSalida())) {
                        continue;
                    }
                }

                if (vuelo.getCapacidadActual() >= vuelo.getCapacidadMaxima()) {
                    continue; // Vuelo lleno, skip
                }

                // ===== NUEVA VALIDACIÓN: CAPACIDAD DE ALMACÉN =====
                Aeropuerto aeropuertoLlegada = vuelo.getAeropuertoDestino();
                LocalDateTime horaLlegada = vuelo.getHoraLlegada();

                int capacidadDisponible = aeropuertoLlegada.getCapacidad() -
                        aeropuertoLlegada.calcularOcupacionEnMomento(horaLlegada);

                if (capacidadDisponible <= 0) {
                    continue; // Almacén lleno, skip este vuelo
                }
                // ===== FIN VALIDACIÓN =====

                // Calcular tiempo de este vuelo
                long duracionVuelo = calcularDuracionHoras(
                        vuelo.getHoraSalida(),
                        vuelo.getAeropuertoOrigen().getHusoHorario(),
                        vuelo.getHoraLlegada(),
                        vuelo.getAeropuertoDestino().getHusoHorario()
                );

                // Calcular tiempo de espera antes de este vuelo
                long tiempoEspera = calcularDuracionHoras(
                        actual.tiempoLlegada,
                        actual.aeropuerto.getHusoHorario(),
                        vuelo.getHoraSalida(),
                        vuelo.getAeropuertoOrigen().getHusoHorario()
                );

                long nuevoTiempoAcumulado = actual.tiempoAcumuladoHoras + tiempoEspera + duracionVuelo;

                if (nuevoTiempoAcumulado > plazoMaximoHoras) {
                    continue;  // No agregar este nodo a la cola
                }

                // Crear nueva ruta incluyendo este vuelo
                List<Vuelo> nuevaRuta = new ArrayList<>(actual.rutaHastaAqui);
                nuevaRuta.add(vuelo);

                // Crear nuevo nodo
                NodoRuta nuevoNodo = new NodoRuta(
                        vuelo.getAeropuertoDestino(),
                        vuelo.getHoraLlegada(),
                        nuevoTiempoAcumulado,
                        nuevaRuta,
                        actual.numeroEscalas + 1
                );

                cola.add(nuevoNodo);
            }
        }

        return null; // No se encontró ruta factible
    }

    /**
     * Calcula un score para una ruta. Menor score = mejor opción
     * @param ruta Lista de vuelos de la ruta
     * @param plazoMaximoDias Plazo máximo permitido (2 o 3 días)
     * @return Score de la ruta (menor es mejor)
     */
    private double calcularScore(List<Vuelo> ruta, int plazoMaximoDias) {
        if (ruta == null || ruta.isEmpty()) {
            return Double.MAX_VALUE; // Ruta inválida
        }

        // Factor 1: Tiempo total de la ruta (en horas)
        Vuelo primerVuelo = ruta.get(0);
        Vuelo ultimoVuelo = ruta.get(ruta.size() - 1);

        long tiempoTotalHoras = calcularDuracionHoras(
                primerVuelo.getHoraSalida(),
                primerVuelo.getAeropuertoOrigen().getHusoHorario(),
                ultimoVuelo.getHoraLlegada(),
                ultimoVuelo.getAeropuertoDestino().getHusoHorario()
        );

        // Factor 2: Número de escalas (menos escalas = mejor)
        int numeroEscalas = ruta.size() - 1;

        // Combinar factores en un score
        double score =
                (tiempoTotalHoras * 1.0) +      // Peso 1: tiempo total
                        (numeroEscalas * 5.0);          // Peso 5: penalizar escalas

        return score;
    }

    private class OpcionSede {
        Aeropuerto sede;           // Sede desde donde sale (Lima, Bruselas o Baku)
        List<Vuelo> ruta;          // Ruta óptima encontrada desde esta sede
        double score;              // Puntaje de esta opción

        public OpcionSede(Aeropuerto sede, List<Vuelo> ruta, double score) {
            this.sede = sede;
            this.ruta = ruta;
            this.score = score;
        }
    }

    private List<OpcionSede> crearRCL(List<OpcionSede> opciones) {
        if (opciones.isEmpty()) {
            return new ArrayList<>();
        }

        // Ordenar por score (menor score = mejor)
        opciones.sort(Comparator.comparingDouble(o -> o.score));

        // Tomar las primeras 'tamanoRCL' opciones
        int tamaño = Math.min(tamanoRCL, opciones.size());
        return new ArrayList<>(opciones.subList(0, tamaño));
    }

    private OpcionSede seleccionarAleatorio(List<OpcionSede> rcl) {
        Random random = new Random();
        int indice = random.nextInt(rcl.size());
        return rcl.get(indice);
    }

    /**
     * Asigna productos de un pedido usando las opciones de la RCL aleatoriamente
     * Valida y actualiza capacidades de almacenes
     * @param pedido Pedido a asignar
     * @param rcl Lista de candidatos restringida
     * @return Lista de rutas creadas
     */
    private List<Ruta> asignarProductosConRCL(Pedido pedido, List<OpcionSede> rcl) {
        List<Ruta> rutasCreadas = new ArrayList<>();
        int cantidadPendiente = pedido.getCantidad() - pedido.getCantidadCumplida();

        // Copiar RCL para poder remover opciones ya usadas
        List<OpcionSede> rclDisponible = new ArrayList<>(rcl);
        Random random = new Random();

        // Intentar asignar hasta completar el pedido o agotar opciones
        while (cantidadPendiente > 0 && !rclDisponible.isEmpty()) {

            // Seleccionar aleatoriamente una opción
            int indice = random.nextInt(rclDisponible.size());
            OpcionSede opcion = rclDisponible.get(indice);
            rclDisponible.remove(indice); // Remover para no repetir

            // Calcular cuántos productos caben en esta ruta (considerando VUELOS)
            int capacidadDisponibleVuelos = Integer.MAX_VALUE;
            for (Vuelo vuelo : opcion.ruta) {
                int capacidadDisponible = vuelo.getCapacidadMaxima() - vuelo.getCapacidadActual();
                capacidadDisponibleVuelos = Math.min(capacidadDisponibleVuelos, capacidadDisponible);
            }

            if (capacidadDisponibleVuelos <= 0) {
                continue; // No hay capacidad en vuelos
            }

            // Validar capacidades de ALMACENES en toda la ruta
            int capacidadDisponibleAlmacenes = validarCapacidadAlmacenesEnRuta(opcion.ruta);

            if (capacidadDisponibleAlmacenes <= 0) {
                System.out.println("ADVERTENCIA: No hay capacidad en almacenes para ruta del pedido " +
                        pedido.getIdCliente());
                continue; // No hay capacidad en almacenes
            }

            // Capacidad real disponible es el mínimo entre vuelos y almacenes
            int capacidadDisponibleRuta = Math.min(capacidadDisponibleVuelos, capacidadDisponibleAlmacenes);

            // Asignar lo que cabe
            int cantidadAsignada = Math.min(cantidadPendiente, capacidadDisponibleRuta);

            // Crear objeto Ruta primero (lo necesitamos para ProductoEnAlmacen)
            Ruta nuevaRuta = new Ruta(pedido, opcion.sede, opcion.ruta, cantidadAsignada);

            // Actualizar VUELOS
            for (Vuelo vuelo : opcion.ruta) {
                vuelo.cargarProductos(cantidadAsignada);
            }

            LocalDateTime fechaPedido = LocalDateTime.of(2025, pedido.getMes(), pedido.getDia(),
                    pedido.getHora(), pedido.getMinuto());
            boolean cumple = cumplePlazo(opcion.ruta, fechaPedido,
                    buscarAeropuertoPorCodigo(pedido.getAeropuertoDestino()),
                    determinarPlazo(opcion.sede,
                            buscarAeropuertoPorCodigo(pedido.getAeropuertoDestino())));
            nuevaRuta.setCumplePlazo(cumple);

            // Actualizar ALMACENES
            actualizarAlmacenesEnRuta(nuevaRuta, opcion.ruta, cantidadAsignada);

            rutasCreadas.add(nuevaRuta);
            cantidadPendiente -= cantidadAsignada;
        }

        // Si aún quedan productos sin asignar
        if (cantidadPendiente > 0) {
            System.out.println("ADVERTENCIA: No se pudieron asignar " + cantidadPendiente +
                    " productos del pedido " + pedido.getIdCliente());
        }

        return rutasCreadas;
    }

    /**
     * Valida si una ruta cumple con el plazo establecido
     * @param ruta Secuencia de vuelos
     * @param fechaRegistroPedido Fecha y hora de registro del pedido
     * @param aeropuertoDestino Aeropuerto de destino
     * @param plazoMaximoDias Plazo máximo (2 o 3 días)
     * @return true si cumple el plazo, false si lo excede
     */
    private boolean cumplePlazo(List<Vuelo> ruta, LocalDateTime fechaRegistroPedido,
                                Aeropuerto aeropuertoDestino, int plazoMaximoDias) {
        if (ruta == null || ruta.isEmpty()) {
            return false;
        }

        // Obtener hora de llegada del último vuelo
        Vuelo ultimoVuelo = ruta.get(ruta.size() - 1);
        LocalDateTime horaDisponibleParaCliente = ultimoVuelo.getHoraLlegada();

        // Agregar 2 horas de procesamiento en destino
        //LocalDateTime horaDisponibleParaCliente = horaLlegadaDestino.plusHours(2);

        // Convertir ambas fechas a UTC para comparación precisa
        LocalDateTime registroUTC = convertirAUTC(fechaRegistroPedido, aeropuertoDestino.getHusoHorario());
        LocalDateTime llegadaUTC = convertirAUTC(horaDisponibleParaCliente, aeropuertoDestino.getHusoHorario());

        // Calcular tiempo transcurrido en horas
        long horasTranscurridas = Duration.between(registroUTC, llegadaUTC).toHours();
        long plazoMaximoHoras = plazoMaximoDias * 24;

        return horasTranscurridas <= plazoMaximoHoras;
    }

    /**
     * Valida que todos los almacenes en la ruta tengan capacidad
     * @param ruta Lista de vuelos
     * @return Capacidad mínima disponible en los almacenes, o 0 si alguno está lleno
     */
    private int validarCapacidadAlmacenesEnRuta(List<Vuelo> ruta) {
        if (ruta.isEmpty()) {
            return 0;
        }

        int capacidadMinima = Integer.MAX_VALUE;

        // Validar cada aeropuerto de llegada en la ruta
        for (int i = 0; i < ruta.size(); i++) {
            Vuelo vueloActual = ruta.get(i);
            Aeropuerto aeropuertoLlegada = vueloActual.getAeropuertoDestino();
            LocalDateTime horaLlegada = vueloActual.getHoraLlegada();

            // Determinar si es destino final o tránsito
            Vuelo siguienteVuelo = null;
            if (i < ruta.size() - 1) {
                siguienteVuelo = ruta.get(i + 1);  // Hay siguiente vuelo (es tránsito)
            }

            // ← CAMBIO CLAVE: Validar TODO EL PERIODO, no solo un instante
            // Probar con 1 producto para ver cuánto espacio hay realmente
            int capacidadDisponibleReal = 0;

            // Buscar la capacidad máxima disponible mediante búsqueda binaria o incremental
            int capacidadActual = aeropuertoLlegada.getCapacidad() -
                    aeropuertoLlegada.calcularOcupacionEnMomento(horaLlegada);

            // Validar si ese espacio está disponible durante todo el periodo
            if (aeropuertoLlegada.hayEspacioEnPeriodo(capacidadActual, horaLlegada, siguienteVuelo)) {
                capacidadDisponibleReal = capacidadActual;
            } else {
                // Si no hay espacio, buscar cuánto espacio realmente hay
                // Esto es costoso, pero más preciso
                for (int test = 1; test <= capacidadActual; test++) {
                    if (aeropuertoLlegada.hayEspacioEnPeriodo(test, horaLlegada, siguienteVuelo)) {
                        capacidadDisponibleReal = test;
                    } else {
                        break;  // Ya no cabe más
                    }
                }
            }

            capacidadMinima = Math.min(capacidadMinima, capacidadDisponibleReal);

            if (capacidadDisponibleReal <= 0) {
                return 0; // No hay espacio durante el periodo
            }
        }

        return capacidadMinima;
    }

    /**
     * Actualiza los almacenes agregando los productos de la ruta
     * @param ruta Ruta creada
     * @param vuelos Lista de vuelos de la ruta
     * @param cantidad Cantidad de productos
     */
    private void actualizarAlmacenesEnRuta(Ruta ruta, List<Vuelo> vuelos, int cantidad) {
        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo vueloActual = vuelos.get(i);
            Aeropuerto aeropuertoLlegada = vueloActual.getAeropuertoDestino();
            LocalDateTime horaLlegada = vueloActual.getHoraLlegada();

            // Determinar si es destino final o tránsito
            Vuelo siguienteVuelo = null;
            if (i < vuelos.size() - 1) {
                siguienteVuelo = vuelos.get(i + 1); // Hay siguiente vuelo (es tránsito)
            }

            // Crear producto en almacén
            ProductoEnAlmacen producto = new ProductoEnAlmacen(ruta, cantidad, horaLlegada, siguienteVuelo);

            // Agregar al almacén
            boolean agregado = aeropuertoLlegada.agregarProductoAlAlmacen(producto, horaLlegada);

            if (!agregado) {
                System.out.println("ERROR: No se pudo agregar producto al almacén " +
                        aeropuertoLlegada.getCodigo() + " (no debería pasar si validamos bien)");
            }
        }
    }
    //Sebastian_v2
    private LocalDateTime convertirEntreZonas(LocalDateTime tiempo, int husoDesde, int husoHasta) {
        // Convertir a UTC
        LocalDateTime tiempoUTC = convertirAUTC(tiempo, husoDesde);
        // Convertir a zona destino
        return tiempoUTC.plusHours(husoHasta);
    }

    private void inicializarIndiceVuelos() {
        vuelosPorOrigen = new HashMap<>();
        for (Vuelo v : vuelos) {
            String origen = v.getAeropuertoOrigen().getCodigo();
            vuelosPorOrigen.computeIfAbsent(origen, k -> new ArrayList<>()).add(v);
        }
        System.out.println("✅ Índice de vuelos creado: " + vuelosPorOrigen.size() + " orígenes");
    }

}