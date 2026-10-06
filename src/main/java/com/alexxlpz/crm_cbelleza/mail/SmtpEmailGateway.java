package com.alexxlpz.crm_cbelleza.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;

/** Envío real por SMTP (se activa cuando hay MAIL_HOST configurado). */
public class SmtpEmailGateway implements EmailGateway {

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
            helper.setFrom(fromEmail, "Cuquora CRM");
            helper.setTo(message.to());
            if (message.replyTo() != null) {
                helper.setReplyTo(message.replyTo(), message.replyToName());
            }
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mime);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailSendException("No se pudo preparar el correo para " + message.to(), e);
        }
    }
}
