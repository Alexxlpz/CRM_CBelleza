package com.alexxlpz.crm_cbelleza.booking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Zonas horarias que se pueden elegir para un centro en «Mi Centro» (identificador IANA → nombre en pantalla). */
public final class CenterTimeZones {

    public static final String DEFAULT = "Europe/Madrid";

    public static final Map<String, String> OPTIONS = options();

    private CenterTimeZones() {
    }

    public static boolean isValid(String zone) {
        return OPTIONS.containsKey(zone);
    }

    private static Map<String, String> options() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Europe/Madrid", "España (península y Baleares)");
        options.put("Atlantic/Canary", "España (Canarias)");
        options.put("Europe/Lisbon", "Portugal");
        options.put("America/Mexico_City", "México (centro)");
        options.put("America/Bogota", "Colombia");
        options.put("America/Lima", "Perú");
        options.put("America/Santiago", "Chile");
        options.put("America/Argentina/Buenos_Aires", "Argentina");
        return Collections.unmodifiableMap(options);
    }
}
