package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a los formularios públicos que crean cuentas o envían correos (contacto, alta de centro, registro): cada IP
 * puede enviar cada uno unas pocas veces por hora. Así un script no puede usarlos para mandar correos de Cuquora a
 * miles de direcciones ni para probar en bloque qué correos tienen cuenta. Se cuenta en memoria (se reinicia con la app).
 */
@Service
public class PublicFormLimiter {

    public static final Duration WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_SIZE = 10_000;

    public enum Form {
        CONTACT("Has enviado varios mensajes seguidos. Espera un rato o escríbenos directamente por correo."),
        CENTER_REGISTRATION("Se han enviado varias altas de centro desde tu conexión. Espera un rato e inténtalo de nuevo."),
        REGISTRATION("Demasiados intentos de registro desde tu conexión. Espera un rato e inténtalo de nuevo.");

        private final String message;

        Form(String message) {
            this.message = message;
        }
    }

    private final Clock clock;
    private final Map<Form, Integer> limits;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicFormLimiter(Clock clock,
                             @Value("${crm.security.form-limits.contact:5}") int contact,
                             @Value("${crm.security.form-limits.center-registration:5}") int centerRegistration,
                             @Value("${crm.security.form-limits.registration:10}") int registration) {
        this.clock = clock;
        this.limits = Map.of(Form.CONTACT, contact, Form.CENTER_REGISTRATION, centerRegistration, Form.REGISTRATION, registration);
    }

    /** Apunta un envío de esa IP; si ya ha llegado al máximo de la última hora lanza {@link BusinessRuleException}. */
    public void check(Form form, String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_SIZE) {
            windows.values().removeIf(w -> w.expired(now));
        }
        int max = limits.get(form);
        Window window = windows.compute(form + ":" + ip, (k, w) -> w == null || w.expired(now)
                ? new Window(1, now) : new Window(w.count + 1, w.start));
        if (window.count > max) {
            throw new BusinessRuleException(form.message);
        }
    }

    /** Envíos de la última hora: la ventana empieza con el primero y dura {@link #WINDOW}. */
    private record Window(int count, Instant start) {
        boolean expired(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }
    }
}
