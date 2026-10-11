package com.alexxlpz.crm_cbelleza.googlecalendar;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Cifra los tokens de Google antes de guardarlos (AES-256-GCM con la clave GOOGLE_TOKEN_KEY):
 * con una copia de la base de datos no basta para escribir en los calendarios de los trabajadores.
 */
@Component
public class GoogleTokenCipher {

    /** Sal fija de la derivación de la clave; el secreto es GOOGLE_TOKEN_KEY y cada cifrado lleva su propio IV. */
    private static final String SALT = "6375717175726121";

    private final TextEncryptor encryptor;

    public GoogleTokenCipher(GoogleCalendarSettings settings) {
        this.encryptor = settings.isEnabled() ? Encryptors.delux(settings.tokenKey(), SALT) : null;
    }

    public String encrypt(String token) {
        return encryptor().encrypt(token);
    }

    public String decrypt(String encrypted) {
        return encryptor().decrypt(encrypted);
    }

    private TextEncryptor encryptor() {
        if (encryptor == null) {
            throw new IllegalStateException("Google Calendar no está configurado (falta GOOGLE_TOKEN_KEY o el ID de cliente).");
        }
        return encryptor;
    }
}
