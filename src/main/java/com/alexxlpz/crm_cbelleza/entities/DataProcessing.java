package com.alexxlpz.crm_cbelleza.entities;

/**
 * Contrato de encargo del tratamiento (art. 28 RGPD) entre cada centro y Cuquora: el texto está en
 * {@code templates/encargo.html} (página pública /encargo). Al cambiar el texto de forma importante, sube la versión:
 * los dueños de los centros volverán a ver el aviso para aceptarlo en su panel.
 */
public final class DataProcessing {

    /** Versión vigente del contrato (fecha de la última redacción). */
    public static final String VERSION = "2026-11-25";

    /** Fecha legible de esa versión, para la página del contrato. */
    public static final String VERSION_LABEL = "25 de noviembre de 2026";

    private DataProcessing() {
    }
}
