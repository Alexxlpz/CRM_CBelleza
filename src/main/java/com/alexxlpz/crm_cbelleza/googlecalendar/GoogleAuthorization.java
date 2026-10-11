package com.alexxlpz.crm_cbelleza.googlecalendar;

/** Resultado de canjear el código de Google: token de actualización (permanente) y correo de la cuenta. */
public record GoogleAuthorization(String refreshToken, String email) {
}
