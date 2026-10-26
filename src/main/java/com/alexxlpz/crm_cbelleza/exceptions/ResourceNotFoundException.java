package com.alexxlpz.crm_cbelleza.exceptions;

import java.io.Serial;

/**
 * El recurso no existe o no pertenece al centro/usuario actual.
 * Se usa el mismo error en ambos casos para no revelar datos de otros centros.
 */
public class ResourceNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
