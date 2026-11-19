package com.alexxlpz.crm_cbelleza.forms;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redes sociales de un centro: convierte lo que escribe el dueño (el usuario con o sin @ o el enlace entero) en el
 * usuario de cada red, y la web propia en una dirección http(s). Así en la ficha solo salen enlaces a la red de verdad
 * y nadie puede colar un enlace a otra web en el botón de Instagram.
 */
public final class SocialLinks {

    public static final int MAX_WEBSITE = 300;

    private static final Pattern INSTAGRAM = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final Pattern TIKTOK = Pattern.compile("[A-Za-z0-9._]{2,24}");
    private static final Pattern FACEBOOK = Pattern.compile("[A-Za-z0-9.\\-]{2,80}");
    private static final Pattern HOST = Pattern.compile("(?i)[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?)*\\.[a-z]{2,24}");

    private SocialLinks() {
    }

    /** Usuario de Instagram, o nulo si está vacío. */
    public static String instagram(String input) {
        return handle(input, "instagram.com", INSTAGRAM, "Instagram",
                "Escribe tu usuario de Instagram (por ejemplo, @tucentro) o pega el enlace de tu perfil.");
    }

    /** Usuario de TikTok, o nulo si está vacío. */
    public static String tiktok(String input) {
        return handle(input, "tiktok.com", TIKTOK, "TikTok",
                "Escribe tu usuario de TikTok (por ejemplo, @tucentro) o pega el enlace de tu perfil.");
    }

    /** Nombre de la página de Facebook, o nulo si está vacío. */
    public static String facebook(String input) {
        return handle(input, "facebook.com", FACEBOOK, "Facebook",
                "Escribe el nombre de tu página de Facebook o pega su enlace (facebook.com/tucentro).");
    }

    /** Web propia como dirección https (o http si se escribió así), o nula si está vacía. */
    public static String website(String input) {
        String value = clean(input);
        if (value == null) {
            return null;
        }
        String withScheme = value.matches("(?i)https?://.*") ? value : "https://" + value;
        try {
            URI uri = new URI(withScheme);
            String host = uri.getHost();
            if (host == null || !HOST.matcher(host).matches() || uri.getUserInfo() != null || uri.getPort() != -1
                    || withScheme.length() > MAX_WEBSITE) {
                throw new URISyntaxException(value, "host");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String rest = withScheme.substring(withScheme.indexOf("://") + 3 + host.length());
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + rest;
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("La web no parece una dirección válida. Escríbela así: www.tucentro.com.");
        }
    }

    public static String instagramUrl(String handle) {
        return handle == null ? null : "https://www.instagram.com/" + handle + "/";
    }

    public static String tiktokUrl(String handle) {
        return handle == null ? null : "https://www.tiktok.com/@" + handle;
    }

    public static String facebookUrl(String page) {
        return page == null ? null : "https://www.facebook.com/" + page;
    }

    /** Lo que se escribió sin espacios ni @ inicial; si es un enlace de la red, solo la primera parte de la ruta. */
    private static String handle(String input, String domain, Pattern pattern, String network, String error) {
        String value = clean(input);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        // Perfil de Facebook sin nombre propio: facebook.com/profile.php?id=123
        Matcher profile = Pattern.compile("(?i)^(?:https?://)?(?:[a-z]+\\.)?facebook\\.com/profile\\.php\\?id=(\\d{1,20})(?:[&#].*)?$")
                .matcher(value);
        if (domain.equals("facebook.com") && profile.matches()) {
            return "profile.php?id=" + profile.group(1);
        }
        int at = lower.indexOf(domain + "/");
        if (at >= 0) {
            String before = lower.substring(0, at);
            if (!before.matches("(https?://)?([a-z]+\\.)?")) {
                throw new BusinessRuleException("Ese enlace no es de " + network + ". " + error);
            }
            value = value.substring(at + domain.length() + 1);
            int end = value.length();
            for (char stop : new char[]{'/', '?', '#'}) {
                int i = value.indexOf(stop);
                if (i >= 0 && i < end) end = i;
            }
            value = value.substring(0, end);
        } else if (lower.contains("://") || lower.contains("/")) {
            throw new BusinessRuleException("Ese enlace no es de " + network + ". " + error);
        }
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (!pattern.matcher(value).matches()) {
            throw new BusinessRuleException(error);
        }
        return value;
    }

    private static String clean(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }
}
