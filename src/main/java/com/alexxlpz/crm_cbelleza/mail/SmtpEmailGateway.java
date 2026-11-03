package com.alexxlpz.crm_cbelleza.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;

/** Envío real por SMTP (se activa cuando hay MAIL_HOST configurado). */
public class SmtpEmailGateway implements EmailGateway {

    /** Nombre que ve el destinatario junto al remitente ({@code CRM_FROM_EMAIL}). */
    static final String SENDER_NAME = "Cuquora";

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public SmtpEmailGateway(JavaMailSender mailSender, String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail, SENDER_NAME);
            helper.setTo(message.to());
            if (message.replyTo() != null) {
                helper.setReplyTo(message.replyTo(), message.replyToName());
            }
            helper.setSubject(message.subject());
            // Texto plano y HTML a la vez (multipart/alternative): los filtros de spam desconfían de los correos solo HTML
            helper.setText(HtmlToText.convert(message.htmlBody()), message.htmlBody());
            mailSender.send(mime);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailSendException("No se pudo preparar el correo para " + message.to(), e);
        }
    }
}
