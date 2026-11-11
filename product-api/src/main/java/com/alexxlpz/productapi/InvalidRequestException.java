package com.alexxlpz.productapi;

/** Petición con datos que no valen: responde 400 con el mensaje. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
