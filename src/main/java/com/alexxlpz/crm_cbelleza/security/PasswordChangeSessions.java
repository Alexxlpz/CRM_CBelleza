package com.alexxlpz.crm_cbelleza.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Al cambiar o restablecer la contraseña, las demás sesiones abiertas de esa cuenta dejan de valer: quien se hubiera
 * quedado con la sesión abierta en otro navegador sale en su siguiente pantalla ({@link PasswordChangeInterceptor}).
 * La sesión desde la que se cambia sigue abierta.
 * <p>
 * Se guarda en memoria cuándo cambió cada cuenta su contraseña y, en cada sesión, desde cuándo vale su inicio de
 * sesión. Las sesiones también viven en memoria, así que al reiniciar la app se pierden las dos cosas a la vez.
 */
@Component
public class PasswordChangeSessions {

    /** El inicio de sesión que se vio la última vez en esta sesión (cambia al volver a entrar). */
    static final String SEEN_AUTHENTICATION = PasswordChangeSessions.class.getName() + ".AUTHENTICATION";
    /** Desde cuándo vale ese inicio de sesión. */
    static final String VALID_SINCE = PasswordChangeSessions.class.getName() + ".SINCE";

    private final Map<Long, Instant> passwordChangedAt = new ConcurrentHashMap<>();
    private final Clock clock;

    public PasswordChangeSessions(Clock clock) {
        this.clock = clock;
    }

    /**
     * Cierra las demás sesiones del usuario. Si hay una transacción en curso, cuando se confirme (si se deshace,
     * la contraseña no ha cambiado y no se cierra nada).
     */
    public void endOtherSessions(Long userId) {
        if (userId == null) {
            return;
        }
        HttpSession current = currentSession();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    end(userId, current);
                }
            });
        } else {
            end(userId, current);
        }
    }

    private void end(Long userId, HttpSession current) {
        Instant now = clock.instant();
        passwordChangedAt.put(userId, now);
        if (current != null) {
            try {
                current.setAttribute(VALID_SINCE, now);
            } catch (IllegalStateException alreadyInvalidated) {
                // La sesión de esta petición ya se ha cerrado: no hay nada que mantener abierto
            }
        }
    }

    /**
     * ¿Sigue valiendo esta sesión? Falso si se inició antes del último cambio de contraseña de la cuenta.
     * Un inicio de sesión nuevo (otra vez la contraseña, Google o la cuenta recordada) vale desde ese momento.
     */
    boolean isStillValid(Long userId, Authentication authentication, HttpSession session) {
        if (session.getAttribute(SEEN_AUTHENTICATION) != authentication) {
            // Inicio de sesión nuevo en este navegador (o la primera pantalla tras entrar): vale desde ahora
            session.setAttribute(SEEN_AUTHENTICATION, authentication);
            session.setAttribute(VALID_SINCE, clock.instant());
            return true;
        }
        Instant changedAt = passwordChangedAt.get(userId);
        Instant since = (Instant) session.getAttribute(VALID_SINCE);
        return changedAt == null || since == null || !since.isBefore(changedAt);
    }

    private static HttpSession currentSession() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getSession(false);
        }
        return null;
    }
}
