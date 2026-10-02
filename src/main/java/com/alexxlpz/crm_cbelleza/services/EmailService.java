package com.alexxlpz.crm_cbelleza.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${crm.mail.admin-email:soporte@cuquora.com}")
    private String adminEmail;

    @Value("${crm.mail.from-email:no-reply@cuquora.com}")
    private String fromEmail;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends contact inquiry to admin and an automated acknowledgment to the user.
     * Executes asynchronously so the user interface responds instantly.
     */
    public void sendContactInquiryAsync(String name, String email, String phone, String subject, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                sendContactInquiry(name, email, phone, subject, message);
            } catch (Exception e) {
                log.error("Error al procesar el envío de correos de contacto para {}: {}", email, e.getMessage(), e);
            }
        });
    }

    public void sendContactInquiry(String name, String email, String phone, String subject, String message) {
        boolean isSmtpReady = mailSender != null && mailHost != null && !mailHost.trim().isEmpty();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        if (!isSmtpReady) {
            // Local fallback simulation (printed clearly in console)
            logSimulation(name, email, phone, subject, message, timestamp);
            return;
        }

        // 1. Send notification email to Administrator
        try {
            sendAdminNotification(name, email, phone, subject, message, timestamp);
        } catch (Exception e) {
            log.error("Fallo al enviar notificación de contacto al administrador ({})", adminEmail, e);
        }

        // 2. Send acknowledgment email to User
        try {
            sendUserAcknowledgment(name, email, subject, message, timestamp);
        } catch (Exception e) {
            log.error("Fallo al enviar acuse de recibo de contacto al usuario ({})", email, e);
        }
    }

    private void sendAdminNotification(String name, String email, String phone, String subject, String message, String timestamp)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(fromEmail, "Cuquora CRM");
        helper.setTo(adminEmail);
        helper.setReplyTo(email, name);
        helper.setSubject("[Cuquora Contacto] " + subject + " - " + name);

        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0; padding:24px; font-family:'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color:#f9f7f5; color:#2b2825;">
                <div style="max-width:600px; margin:0 auto; background:#ffffff; border:1px solid #e7ded5; border-radius:16px; padding:32px; box-shadow:0 8px 24px rgba(40,32,25,0.06);">
                    <div style="border-bottom:2px solid #c5a880; padding-bottom:16px; margin-bottom:24px;">
                        <h2 style="margin:0; color:#2b2825; font-size:22px;">Nuevo Mensaje de Contacto</h2>
                        <span style="font-size:13px; color:#857b74;">Recibido a través del portal Cuquora CRM el %s</span>
                    </div>

                    <table style="width:100%%; border-collapse:collapse; margin-bottom:24px; font-size:14.5px;">
                        <tr>
                            <td style="padding:8px 0; color:#7a716a; width:130px; font-weight:600;">Remitente:</td>
                            <td style="padding:8px 0; color:#2b2825; font-weight:700;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:8px 0; color:#7a716a; font-weight:600;">Correo Electrónico:</td>
                            <td style="padding:8px 0;"><a href="mailto:%s" style="color:#a87d4f; text-decoration:none; font-weight:600;">%s</a></td>
                        </tr>
                        <tr>
                            <td style="padding:8px 0; color:#7a716a; font-weight:600;">Teléfono:</td>
                            <td style="padding:8px 0; color:#2b2825;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding:8px 0; color:#7a716a; font-weight:600;">Asunto:</td>
                            <td style="padding:8px 0; color:#2b2825; font-weight:600;">%s</td>
                        </tr>
                    </table>

                    <div style="background:#faf7f4; border-left:4px solid #c5a880; padding:18px 20px; border-radius:8px; margin-bottom:28px;">
                        <h4 style="margin:0 0 10px; font-size:13px; text-transform:uppercase; letter-spacing:0.5px; color:#857b74;">Mensaje:</h4>
                        <p style="margin:0; font-size:15px; line-height:1.6; white-space:pre-wrap; color:#2b2825;">%s</p>
                    </div>

                    <div style="text-align:center;">
                        <a href="mailto:%s?subject=Re: %s" style="display:inline-block; background:#c5a880; color:#ffffff; padding:12px 26px; border-radius:10px; font-weight:700; text-decoration:none; font-size:14.5px;">
                            Responder a %s
                        </a>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                timestamp,
                escapeHtml(name),
                escapeHtml(email),
                escapeHtml(email),
                phone != null && !phone.isBlank() ? escapeHtml(phone) : "No especificado",
                escapeHtml(subject),
                escapeHtml(message),
                escapeHtml(email),
                escapeHtml(subject),
                escapeHtml(name)
        );

        helper.setText(html, true);
        mailSender.send(mimeMessage);
        log.info("Notificación de contacto enviada exitosamente a administración ({}) para el remitente {}", adminEmail, email);
    }

    private void sendUserAcknowledgment(String name, String email, String subject, String message, String timestamp)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(fromEmail, "Cuquora CRM");
        helper.setTo(email);
        helper.setSubject("Hemos recibido tu consulta: " + subject + " - Cuquora");

        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0; padding:24px; font-family:'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color:#f9f7f5; color:#2b2825;">
                <div style="max-width:600px; margin:0 auto; background:#ffffff; border:1px solid #e7ded5; border-radius:16px; padding:32px; box-shadow:0 8px 24px rgba(40,32,25,0.06);">
                    <div style="text-align:center; border-bottom:1px solid #eee8e2; padding-bottom:20px; margin-bottom:24px;">
                        <h1 style="margin:0; color:#2b2825; font-size:24px; font-weight:800; letter-spacing:-0.5px;">CUQUORA</h1>
                        <p style="margin:4px 0 0; color:#a87d4f; font-size:13px; font-weight:600; text-transform:uppercase; letter-spacing:1px;">Gestión de Belleza & Bienestar</p>
                    </div>

                    <h2 style="font-size:19px; color:#2b2825; margin-bottom:12px;">¡Hola, %s!</h2>
                    <p style="font-size:15px; color:#5c5550; line-height:1.6; margin-bottom:20px;">
                        Gracias por ponerte en contacto con el equipo de <strong>Cuquora</strong>. Hemos recibido tu solicitud correctamente y un miembro de nuestro equipo te responderá a la mayor brevedad posible.
                    </p>

                    <div style="background:#fdfcfb; border:1px solid #eee8e2; border-radius:12px; padding:18px; margin-bottom:24px;">
                        <h4 style="margin:0 0 10px; font-size:13px; color:#857b74; text-transform:uppercase;">Resumen de tu consulta:</h4>
                        <p style="margin:0 0 6px; font-size:14px; color:#2b2825;"><strong>Asunto:</strong> %s</p>
                        <p style="margin:0 0 6px; font-size:14px; color:#2b2825;"><strong>Fecha:</strong> %s</p>
                        <p style="margin:8px 0 0; font-size:14px; color:#5c5550; line-height:1.5; font-style:italic;">"%s"</p>
                    </div>

                    <p style="font-size:13.5px; color:#857b74; line-height:1.5; margin-bottom:0;">
                        Este es un mensaje automático generado por nuestro sistema. Si necesitas añadir más información a tu consulta, puedes responder directamente a este correo.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(name),
                escapeHtml(subject),
                timestamp,
                escapeHtml(message)
        );

        helper.setText(html, true);
        mailSender.send(mimeMessage);
        log.info("Acuse de recibo de contacto enviado exitosamente a {}", email);
    }

    private void logSimulation(String name, String email, String phone, String subject, String message, String timestamp) {
        log.info("""
            \n==================== [ SIMULACIÓN DE ENVÍO DE EMAIL (MODO LOCAL) ] ====================
            [ADMIN NOTIFICATION]
            DE: {}
            PARA: {}
            REMITENTE: {} <{}> | TELÉFONO: {}
            FECHA: {}
            ASUNTO: [Cuquora Contacto] {} - {}
            MENSAJE:
            {}
            --------------------------------------------------------------------------------------
            [USER ACKNOWLEDGMENT]
            PARA: {}
            ASUNTO: Hemos recibido tu consulta: {} - Cuquora
            ESTADO: Simulación exitosa. Configura MAIL_HOST en application.properties para entrega real vía SMTP.
            ======================================================================================
            """,
                fromEmail,
                adminEmail,
                name,
                email,
                phone != null && !phone.isBlank() ? phone : "No especificado",
                timestamp,
                subject,
                name,
                message,
                email,
                subject
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
