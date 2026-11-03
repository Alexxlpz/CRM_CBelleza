package com.alexxlpz.crm_cbelleza.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Revisión de la configuración al arrancar con el perfil {@code prod} (docker-compose lo activa).
 * <p>
 * Lo que dejaría la web insegura o rota (sin dirección pública, BD en memoria, cuentas demo con contraseña
 * pública, cookies sin cifrar…) <b>impide arrancar</b> con un mensaje que dice qué variable del {@code .env} falta.
 * Lo que solo empeora el servicio (sin correo, sin titular legal) se avisa en el log.
 * Para probar el {@code docker compose} en local por http:// se arranca sin el perfil: {@code SPRING_PROFILES_ACTIVE=}.
 */
@Component
@Profile("prod")
public class ProductionChecks implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionChecks.class);

    /** Correos gratuitos: enviar «desde» ellos con Brevo falla la comprobación DMARC y los correos acaban en spam. */
    private static final List<String> FREE_MAIL_DOMAINS = List.of(
            "hotmail.com", "hotmail.es", "outlook.com", "outlook.es", "live.com", "gmail.com", "yahoo.com", "yahoo.es");

    private final Environment env;

    public ProductionChecks(Environment env) {
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        Result result = check(env);
        result.warnings().forEach(warning -> log.warn("Producción: {}", warning));
        if (!result.errors().isEmpty()) {
            throw new IllegalStateException("La configuración no sirve para producción (perfil «prod»):\n  - "
                    + String.join("\n  - ", result.errors())
                    + "\nCorrígelo en el .env y vuelve a arrancar. Para probar en local por http://, arranca sin el perfil prod "
                    + "(SPRING_PROFILES_ACTIVE= en el .env).");
        }
        log.info("Producción: configuración revisada, sitio en {}", value("crm.site-url"));
    }

    static Result check(Environment env) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String siteUrl = value(env, "crm.site-url");

        if (env.matchesProfiles("dev")) {
            errors.add("el perfil «dev» está activo junto a «prod» (entrar como cualquiera sin contraseña, consola H2).");
        }

        URI site = parse(siteUrl);
        if (siteUrl.isEmpty()) {
            errors.add("falta SITE_URL, la dirección pública (p. ej. https://cuquora.com): con ella se escriben los enlaces "
                    + "de los correos, el enlace corto /c/<nombre>, el QR del cartel y el sitemap.");
        } else if (site == null || site.getHost() == null || !"https".equals(site.getScheme())) {
            errors.add("SITE_URL=" + siteUrl + " tiene que empezar por https:// y llevar el dominio (p. ej. https://cuquora.com).");
        } else if (isLocal(site.getHost())) {
            errors.add("SITE_URL=" + siteUrl + " apunta a este equipo: los enlaces de los correos y el QR no funcionarían fuera.");
        } else if (site.getPath() != null && !site.getPath().isEmpty() && !site.getPath().equals("/")) {
            errors.add("SITE_URL=" + siteUrl + " tiene que ser solo el dominio, sin ruta (p. ej. https://cuquora.com).");
        }

        String datasource = value(env, "spring.datasource.url");
        if (datasource.isEmpty() || datasource.startsWith("jdbc:h2:")) {
            errors.add("la base de datos es H2 en memoria y se borraría al reiniciar: falta DB_URL (docker-compose lo pone para PostgreSQL).");
        }
        if (bool(env, "spring.h2.console.enabled")) {
            errors.add("la consola H2 está activada (H2_CONSOLE_ENABLED): tiene que estar a false.");
        }
        if (bool(env, "crm.seed-demo-data") || bool(env, "crm.seed-demo-history")) {
            errors.add("SEED_DEMO_DATA=true crea cuentas demo con la contraseña pública «" + DatabaseSeeder.DEMO_PASSWORD + "».");
        }
        if (value(env, "crm.security.remember-me-key").length() < 32) {
            errors.add("REMEMBER_ME_KEY falta o es corta (mínimo 32 caracteres). Genera una con: openssl rand -base64 48");
        }
        if (!bool(env, "server.servlet.session.cookie.secure")) {
            errors.add("COOKIE_SECURE=false: la cookie de sesión viajaría también sin cifrar. Con HTTPS delante tiene que ser true.");
        }
        boolean googleId = !value(env, "crm.security.google.client-id").isEmpty();
        boolean googleSecret = !value(env, "crm.security.google.client-secret").isEmpty();
        if (googleId != googleSecret) {
            errors.add("para «Iniciar sesión con Google» hacen falta las dos: GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET.");
        }

        if (!"native".equalsIgnoreCase(value(env, "server.forward-headers-strategy"))) {
            warnings.add("FORWARD_HEADERS_STRATEGY no es native: detrás de Cloudflare la app no ve la IP real del visitante "
                    + "(el freno del login bloquearía a todos a la vez) ni que la visita llegó por HTTPS.");
        }
        if (value(env, "spring.mail.host").isEmpty()) {
            warnings.add("falta MAIL_HOST: los correos (invitaciones, citas, alta de centros) solo se escriben en el log, no se envían.");
        }
        String from = value(env, "crm.mail.from-email").toLowerCase(Locale.ROOT);
        String fromDomain = from.contains("@") ? from.substring(from.indexOf('@') + 1) : "";
        if (FREE_MAIL_DOMAINS.contains(fromDomain)) {
            warnings.add("CRM_FROM_EMAIL=" + from + " es de un correo gratuito: Brevo lo envía, pero muchos acabarán en spam. "
                    + "Usa una dirección del dominio (p. ej. citas@" + (site != null && site.getHost() != null ? site.getHost() : "tudominio")
                    + ") verificada en Brevo con SPF y DKIM.");
        }
        if (value(env, "crm.legal.owner-name").isEmpty() || value(env, "crm.legal.owner-nif").isEmpty()
                || value(env, "crm.legal.owner-address").isEmpty()) {
            warnings.add("faltan LEGAL_OWNER_NAME, LEGAL_OWNER_NIF o LEGAL_OWNER_ADDRESS: la LSSI obliga a mostrarlos en Términos y Privacidad.");
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings));
    }

    private String value(String key) {
        return value(env, key);
    }

    private static String value(Environment env, String key) {
        String value = env.getProperty(key);
        return value == null ? "" : value.strip();
    }

    private static boolean bool(Environment env, String key) {
        return Boolean.parseBoolean(value(env, key));
    }

    private static URI parse(String url) {
        try {
            return url.isEmpty() ? null : URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.endsWith(".localhost") || h.startsWith("127.") || h.equals("0.0.0.0")
                || h.equals("[::1]") || h.startsWith("192.168.") || h.startsWith("10.");
    }

    record Result(List<String> errors, List<String> warnings) {
    }
}
