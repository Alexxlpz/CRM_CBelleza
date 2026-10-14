package com.alexxlpz.crm_cbelleza.mail;

import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService.Invitee;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService.Registration;
import com.alexxlpz.crm_cbelleza.services.InvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correos del alta de centros: la solicitud a administración (con el enlace para publicar el centro),
 * la bienvenida al responsable, las invitaciones al equipo y el aviso de centro publicado.
 * Se envían en segundo plano; {@code siteUrl} lo pasa el controlador porque aquí ya no hay petición.
 */
@Service
public class CenterOnboardingMailService {

    private static final Logger log = LoggerFactory.getLogger(CenterOnboardingMailService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmailGateway gateway;
    private final ITemplateEngine templateEngine;
    private final String adminEmail;

    public CenterOnboardingMailService(EmailGateway gateway, ITemplateEngine templateEngine,
                                       @Value("${crm.mail.admin-email}") String adminEmail) {
        this.gateway = gateway;
        this.templateEngine = templateEngine;
        this.adminEmail = adminEmail;
    }

    @Async
    public void sendRegistration(CenterRegistrationForm form, Registration registration, String siteUrl) {
        Invitee owner = registration.owner();
        List<Invitee> team = registration.invitees();
        Map<String, Object> admin = new HashMap<>();
        admin.put("form", form);
        admin.put("taxId", form.cif().trim().toUpperCase(Locale.ROOT));
        admin.put("owner", owner);
        admin.put("team", team);
        admin.put("specialties", form.specialtiesOrEmpty().isEmpty() ? "No especificadas" : String.join(", ", form.specialtiesOrEmpty()));
        admin.put("loyalty", loyaltySummary(form));
        admin.put("mapUrl", "https://www.openstreetmap.org/?mlat=%s&mlon=%s#map=18/%s/%s"
                .formatted(form.latitude(), form.longitude(), form.latitude(), form.longitude()));
        admin.put("publishUrl", siteUrl + "/register-center/approve/" + registration.approvalToken());
        admin.put("timestamp", LocalDateTime.now().format(TIMESTAMP));
        sendSafely(new EmailMessage(adminEmail, owner.email(), owner.name(),
                "[Cuquora Auditoría] Nuevo centro: " + registration.centerName() + " (" + admin.get("taxId") + ")",
                render("email/center-registration-admin", admin)));

        sendSafely(new EmailMessage(owner.email(), null, null,
                "Tu centro " + registration.centerName() + " ya está en Cuquora: crea tu contraseña",
                render("email/center-registration-ack", Map.of(
                        "name", owner.name(),
                        "centerName", registration.centerName(),
                        "teamSize", team.size() - 1,
                        "invitationUrl", invitationUrl(siteUrl, owner.invitationToken()),
                        "validityDays", InvitationService.VALIDITY_DAYS))));

        for (Invitee member : team.subList(1, team.size())) {
            sendInvitation(member.name(), member.email(), registration.centerName(), member.invitationToken(), siteUrl);
        }
    }

    /** Invitación de un trabajador (también la que se pide de nuevo cuando caduca). */
    @Async
    public void sendInvitationAsync(String name, String email, String centerName, String token, String siteUrl) {
        sendInvitation(name, email, centerName, token, siteUrl);
    }

    @Async
    public void sendPublished(String centerName, String email, String siteUrl) {
        sendSafely(new EmailMessage(email, null, null,
                centerName + " ya aparece en Cuquora",
                render("email/center-published", Map.of(
                        "centerName", centerName,
                        "loginUrl", siteUrl + "/login"))));
    }

    private void sendInvitation(String name, String email, String centerName, String token, String siteUrl) {
        sendSafely(new EmailMessage(email, null, null,
                "Te han dado de alta en " + centerName + " - Cuquora",
                render("email/worker-invitation", Map.of(
                        "name", name,
                        "centerName", centerName,
                        "invitationUrl", invitationUrl(siteUrl, token),
                        "validityDays", InvitationService.VALIDITY_DAYS))));
    }

    private static String invitationUrl(String siteUrl, String token) {
        return siteUrl + "/invitation/" + token;
    }

    private static String loyaltySummary(CenterRegistrationForm form) {
        if (form.mode() == null || LoyaltyMode.OFF.name().equals(form.mode())) {
            return "Sin tarjeta de puntos";
        }
        String perUnit = "PER_ITEM".equals(form.mode()) ? "por tratamiento o producto" : "por euro";
        return "%d puntos %s; %d puntos: %s".formatted(form.pointsPerUnit(), perUnit, form.rewardPoints(), form.rewardText().trim());
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
}
