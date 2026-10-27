package com.alexxlpz.crm_cbelleza.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a quien prueba contraseñas: tras {@link #MAX_ACCOUNT_FAILURES} fallos seguidos con una cuenta, o
 * {@link #MAX_IP_FAILURES} desde una misma IP, el login queda cerrado {@link #LOCK} para esa cuenta o esa IP.
 * Se cuenta en memoria (se reinicia con la app) y por cuenta escrita, exista o no, para no revelar qué correos
 * están dados de alta. Detrás de Cloudflare la IP real llega con server.forward-headers-strategy=native.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ACCOUNT_FAILURES = 5;
    public static final int MAX_IP_FAILURES = 20;
    public static final Duration LOCK = Duration.ofMinutes(15);

    /** Más allá de esto se limpian las entradas caducadas para que el mapa no crezca sin fin. */
    private static final int CLEANUP_SIZE = 10_000;

    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** ¿Está cerrado el login para esta cuenta o esta IP? */
    public boolean isBlocked(String identifier, String ip) {
        Instant now = clock.instant();
        return reached(accountKey(identifier), MAX_ACCOUNT_FAILURES, now) || reached(ipKey(ip), MAX_IP_FAILURES, now);
    }

    public void recordFailure(String identifier, String ip) {
        Instant now = clock.instant();
        if (failures.size() > CLEANUP_SIZE) {
            failures.values().removeIf(f -> f.expired(now));
        }
        add(accountKey(identifier), now);
        add(ipKey(ip), now);
    }

    /** Entrar bien borra los fallos de la cuenta (los de la IP siguen contando). */
    public void recordSuccess(String identifier) {
        String key = accountKey(identifier);
        if (key != null) {
            failures.remove(key);
        }
    }

    private boolean reached(String key, int max, Instant now) {
        if (key == null) {
            return false;
        }
        Failures f = failures.get(key);
        return f != null && !f.expired(now) && f.count >= max;
    }

    private void add(String key, Instant now) {
        if (key != null) {
            failures.compute(key, (k, f) -> f == null || f.expired(now) ? new Failures(1, now) : new Failures(f.count + 1, now));
        }
    }

    private static String accountKey(String identifier) {
        return identifier == null || identifier.isBlank() ? null : "cuenta:" + identifier.strip().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(String ip) {
        return ip == null || ip.isBlank() ? null : "ip:" + ip;
    }

    /** Fallos seguidos y cuándo fue el último: el cierre dura {@link #LOCK} desde el último fallo. */
    private record Failures(int count, Instant last) {
        boolean expired(Instant now) {
            return !now.isBefore(last.plus(LOCK));
        }
    }
}
