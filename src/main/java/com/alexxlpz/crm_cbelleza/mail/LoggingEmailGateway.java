package com.alexxlpz.crm_cbelleza.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulación local: sin SMTP configurado, los correos se muestran en la consola. */
public class LoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailGateway.class);

    @Override
    public void send(EmailMessage message) {
        log.info("""

                ==================== [ SIMULACIÓN DE EMAIL (sin SMTP) ] ====================
                PARA: {}
                RESPONDER A: {}
                ASUNTO: {}
                Configura MAIL_HOST para enviar correos reales.
                ===========================================================================""",
                message.to(),
                message.replyTo() != null ? message.replyTo() : "-",
                message.subject());
    }
}
