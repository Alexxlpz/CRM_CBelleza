package com.alexxlpz.crm_cbelleza.mail;

/** Canal de envío de correos. Permite cambiar SMTP por otro proveedor (o por un simulador) sin tocar a quien envía. */
public interface EmailGateway {

    void send(EmailMessage message);
}
