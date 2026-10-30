package com.alexxlpz.crm_cbelleza.config;

import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, guarda el enlace corto de los centros que aún no lo tienen (los dados de alta antes de la migración V26
 * y los de los datos de demostración), con el mismo nombre que ya tenían. Desde ahí ya no cambia (S-03).
 */
@Component
public class CenterSlugBackfill {

    private static final Logger log = LoggerFactory.getLogger(CenterSlugBackfill.class);

    private final CenterService centers;

    public CenterSlugBackfill(CenterService centers) {
        this.centers = centers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void assignMissingSlugs() {
        int assigned = centers.assignMissingSlugs();
        if (assigned > 0) {
            log.info("Enlace corto guardado para {} centros", assigned);
        }
    }
}
