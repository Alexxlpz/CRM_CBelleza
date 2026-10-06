package com.alexxlpz.crm_cbelleza.exceptions;

/**
 * El recurso no existe o no pertenece al centro/usuario actual.
 * Se usa el mismo error en ambos casos para no revelar datos de otros centros.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
