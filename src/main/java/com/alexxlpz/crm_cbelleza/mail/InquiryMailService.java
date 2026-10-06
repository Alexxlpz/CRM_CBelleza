package com.alexxlpz.crm_cbelleza.mail;

import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.ContactForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Correos de los formularios públicos (contacto y alta de centro): un aviso a administración
 * y un acuse de recibo al remitente. Se envían en segundo plano para que la página responda al instante.
 * El HTML de cada correo está en templates/email/*.html.
 */
@Service
public class InquiryMailService {

    private static final Logger log = LoggerFactory.getLogger(InquiryMailService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmailGateway gateway;
    private final ITemplateEngine templateEngine;
    private final String adminEmail;

    public InquiryMailService(EmailGateway gateway,
                              ITemplateEngine templateEngine,
                              @Value("${crm.mail.admin-email}") String adminEmail) {
        this.gateway = gateway;
        this.templateEngine = templateEngine;
        this.adminEmail = adminEmail;
    }

    @Async
    public void sendContactInquiry(ContactForm form) {
        Map<String, Object> vars = Map.of("form", form, "timestamp", now());
        sendSafely(new EmailMessage(adminEmail, form.email(), form.name(),
                "[Cuquora Contacto] " + form.subject() + " - " + form.name(),
                render("email/contact-admin", vars)));
        sendSafely(new EmailMessage(form.email(), null, null,
                "Hemos recibido tu consulta: " + form.subject() + " - Cuquora",
                render("email/contact-ack", vars)));
    }

    @Async
    public void sendCenterRegistration(CenterRegistrationForm form) {
        Map<String, Object> vars = Map.of(
                "form", form,
                "specialties", form.specialtiesOrEmpty().isEmpty() ? "No especificadas" : String.join(", ", form.specialtiesOrEmpty()),
                "timestamp", now());
        sendSafely(new EmailMessage(adminEmail, form.email(), form.contactName(),
                "[Cuquora Auditoría] Nueva solicitud de centro: " + form.centerName() + " (" + form.cif() + ")",
                render("email/center-registration-admin", vars)));
        sendSafely(new EmailMessage(form.email(), null, null,
                "Solicitud de certificación recibida: " + form.centerName() + " - Cuquora",
                render("email/center-registration-ack", vars)));
    }

    private String render(String template, Map<String, Object> variables) {
        return templateEngine.process(template, new Context(Locale.forLanguageTag("es"), variables));
    }

    /** Un fallo de envío no debe impedir el siguiente correo ni llegar al usuario. */
    private void sendSafely(EmailMessage message) {
        try {
            gateway.send(message);
            log.info("Correo enviado a {}: {}", message.to(), message.subject());
        } catch (Exception e) {
            log.error("Fallo al enviar el correo '{}' a {}", message.subject(), message.to(), e);
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIMESTAMP);
    }
}
