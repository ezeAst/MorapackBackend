package com.morapack.backend.scheduler;

import com.morapack.backend.service.PlanificadorPersistenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PlanificacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlanificacionScheduler.class);

    private final PlanificadorPersistenciaService persistenciaService;


    private final AtomicBoolean ejecutando = new AtomicBoolean(false);

    public PlanificacionScheduler(PlanificadorPersistenciaService persistenciaService) {
        this.persistenciaService = persistenciaService;
    }

    @Scheduled(fixedDelay = 30000) // 30 segundos
    public void ejecutarPlanificacionPeriodica() {

        if (!ejecutando.compareAndSet(false, true)) {
            log.warn("[Planificador] Ejecución anterior aún en curso, saltando...");
            return;
        }

        try {
            long inicio = System.currentTimeMillis();
            String resumen = persistenciaService.ejecutarYGuardar();
            long duracion = System.currentTimeMillis() - inicio;

            log.info("[Planificador automático] {} (tardó {}ms)", resumen, duracion);
        } catch (Exception e) {
            log.error("[Planificador] Error en ejecución: {}", e.getMessage(), e);
        } finally {
            ejecutando.set(false);
        }
    }
}