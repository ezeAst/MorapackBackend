package com.morapack.backend.scheduler;

import com.morapack.backend.service.PlanificadorPersistenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlanificacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlanificacionScheduler.class);

    private final PlanificadorPersistenciaService persistenciaService;

    public PlanificacionScheduler(PlanificadorPersistenciaService persistenciaService) {
        this.persistenciaService = persistenciaService;
    }

    // corre en el minuto 0, 15, 30, 45 de cada hora; zona Perú
    @Scheduled(cron = "0 */15 * * * *", zone = "America/Lima")
    public void ejecutarPlanificacionPeriodica() {
        String resumen = persistenciaService.ejecutarYGuardar();
        log.info("[Planificador automático] {}", resumen);
    }
}
