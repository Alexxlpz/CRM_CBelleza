package com.alexxlpz.productapi;

import java.util.Optional;

/** Fotos de envase: solo JPEG o PNG (se mira el contenido, no lo que diga quien la manda) y de 5 MB como mucho. */
public final class Images {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private Images() {
    }

    /** El tipo de la foto, o vacío si no es un JPEG o PNG o si pesa demasiado. */
    public static Optional<String> contentType(byte[] data) {
        if (data == null || data.length < 8 || data.length > MAX_BYTES) return Optional.empty();
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Optional.of("image/png");
        }
        return Optional.empty();
    }
}
