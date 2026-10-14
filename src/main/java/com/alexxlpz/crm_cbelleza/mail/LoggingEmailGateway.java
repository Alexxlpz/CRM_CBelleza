package com.alexxlpz.crm_cbelleza.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simulación local: sin SMTP configurado, los correos se muestran en la consola.
 * Se imprimen también sus enlaces, para poder abrir en local las invitaciones y la publicación de centros.
 */
public class LoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailGateway.class);
    private static final Pattern LINK = Pattern.compile("href=\"(https?://[^\"]+)\"");

    @Override
    public void send(EmailMessage message) {
        String links = message.htmlBody() == null ? "" : LINK.matcher(message.htmlBody()).results()
                .map(m -> m.group(1).replace("&amp;", "&"))
                .filter(url -> !url.startsWith("mailto:"))
                .distinct()
                .collect(Collectors.joining("\n                "));
        log.info("""

                ==================== [ SIMULACIÓN DE EMAIL (sin SMTP) ] ====================
                PARA: {}
                RESPONDER A: {}
                ASUNTO: {}
                ENLACES: {}
                Configura MAIL_HOST para enviar correos reales.
                ===========================================================================""",
                message.to(),
                message.replyTo() != null ? message.replyTo() : "-",
                message.subject(),
                links.isEmpty() ? "-" : links);
    }
}
