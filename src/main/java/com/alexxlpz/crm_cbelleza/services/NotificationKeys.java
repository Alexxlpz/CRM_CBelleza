package com.alexxlpz.crm_cbelleza.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Huella de un aviso de la campana: los avisos no se guardan (se calculan al vuelo), así que para recordar
 * cuáles ha quitado cada usuario se identifica cada uno por su texto y su enlace. Si cambia el texto
 * («2 solicitudes» en vez de «1») es otro aviso y vuelve a salir.
 */
public final class NotificationKeys {

    private static final Pattern VALID = Pattern.compile("[0-9a-f]{24}");

    private NotificationKeys() {
    }

    public static String of(String message, String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((message + "\n" + url).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValid(String key) {
        return key != null && VALID.matcher(key).matches();
    }
}
