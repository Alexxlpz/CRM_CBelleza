package com.alexxlpz.crm_cbelleza.mail;

/** Correo listo para enviar. {@code replyTo} es opcional. */
public record EmailMessage(String to, String replyTo, String replyToName, String subject, String htmlBody) {
}
