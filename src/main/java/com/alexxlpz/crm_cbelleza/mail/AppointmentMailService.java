package com.alexxlpz.crm_cbelleza.mail;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correos de citas (con Brevo u otro SMTP; sin MAIL_HOST salen por consola).
 * <ul>
 *     <li>Al cliente, lo que hace el centro: confirmar, rechazar, crear, mover, proponer horas, anular.</li>
 *     <li>Al centro, lo que hace el cliente: nueva solicitud (o cita ya confirmada si el centro las acepta
 *     automáticamente), aceptar o no una propuesta, anular.</li>
 *     <li>Recordatorio al cliente unas 24 h antes de la cita.</li>
 * </ul>
 * Los clientes sin correo real (alta a mano sin correo) no reciben nada.
 */
@Service
public class AppointmentMailService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentMailService.class);
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final EmailGateway gateway;
    private final ITemplateEngine templateEngine;
    private final Clock clock;
    private final String siteUrl;
    private final DailyQuota dailyQuota;

    public AppointmentMailService(AppointmentRepository appointments,
                                  EmailGateway gateway,
                                  ITemplateEngine templateEngine,
                                  Clock clock,
                                  @Value("${crm.site-url:}") String siteUrl,
                                  @Value("${crm.mail.max-client-emails-per-center-per-day:150}") int maxPerCenterPerDay) {
        this.dailyQuota = new DailyQuota(maxPerCenterPerDay);
        this.appointments = appointments;
        this.gateway = gateway;
        this.templateEngine = templateEngine;
        this.clock = clock;
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
    }

    /** Avisos de un cambio de cita. Lo llama {@link AppointmentMailListener} tras guardar el cambio. */
    @Transactional(readOnly = true)
    public void notifyChange(Long appointmentId, AppointmentChange change) {
        Appointment app = appointments.findWithDetailsById(appointmentId).orElse(null);
        if (app == null) {
            return;
        }
        String treatment = app.getTreatment().getName();
        String center = app.getCenter().getName();
        String when = when(app.getDateTime());
        switch (change) {
            case REQUESTED -> {
                toClient(app, "Solicitud enviada: " + treatment, "Hemos enviado tu solicitud",
                        center + " revisará tu solicitud y te avisaremos en cuanto la confirme o te proponga otra hora.");
                toCenter(app, "Nueva solicitud de cita: " + treatment + " (" + when + ")", "Nueva solicitud de cita",
                        clientName(app) + " ha pedido cita. Acéptala, recházala o propón otra hora desde la agenda.");
            }
            case BOOKED -> {
                toClient(app, "Cita confirmada: " + treatment + ", " + when, "¡Tu cita está confirmada!",
                        center + " te espera el " + when + ".");
                toCenter(app, "Nueva cita: " + treatment + " (" + when + ")", "Nueva cita confirmada",
                        clientName(app) + " ha reservado cita y ha quedado confirmada automáticamente"
                                + (app.getWorker() != null ? " con " + app.getWorker().getName() : "") + ". Ya está en la agenda.");
            }
            case CONFIRMED -> toClient(app, "Cita confirmada: " + treatment + ", " + when, "¡Tu cita está confirmada!",
                    center + " te espera el " + when + ".");
            case CREATED_BY_CENTER -> toClient(app, "Tienes una cita en " + center + ": " + when, "Tienes una cita nueva",
                    center + " te ha reservado " + treatment + " el " + when + ".");
            case REJECTED -> toClient(app, "Tu solicitud de cita en " + center, "No hemos podido aceptar tu solicitud",
                    center + " no puede atenderte el " + when + ". Puedes pedir otra hora cuando quieras.");
            case RESCHEDULED -> toClient(app, "Tu cita ha cambiado: " + treatment + ", " + when, "Tu cita ha cambiado",
                    center + " ha cambiado tu cita. Ahora es el " + when
                            + (app.getPreviousDateTime() != null ? " (antes era el " + when(app.getPreviousDateTime()) + ")." : "."));
            case PROPOSED -> toClient(app, center + " te propone otra hora para " + treatment, "Te proponemos otra hora",
                    center + " no puede atenderte a la hora que pediste y te propone estas. Entra en «Mis Citas» y elige la que te venga bien.");
            case CANCELLED_BY_CENTER -> toClient(app, "Cita anulada: " + treatment + ", " + when, "Tu cita se ha anulado",
                    center + " ha anulado tu cita del " + when + ". Disculpa las molestias.");
            case PROPOSAL_EXPIRED -> {
                toClient(app, "La propuesta de " + center + " ha caducado", "La propuesta ha caducado",
                        "No elegiste ninguna de las horas propuestas a tiempo. Puedes pedir una cita nueva cuando quieras.");
                toCenter(app, "Propuesta sin respuesta: " + clientName(app), "Propuesta caducada",
                        clientName(app) + " no ha respondido a tiempo a las horas que le propusisteis.");
            }
            case PROPOSAL_ACCEPTED -> toCenter(app, clientName(app) + " ha aceptado la propuesta: " + when, "Propuesta aceptada",
                    clientName(app) + " vendrá el " + when + ". La cita ya está confirmada en la agenda.");
            case PROPOSAL_DECLINED -> toCenter(app, clientName(app) + " no puede a ninguna hora propuesta", "Propuesta rechazada",
                    clientName(app) + " no puede venir a ninguna de las horas que le propusisteis.");
            case CANCELLED_BY_CLIENT -> toCenter(app, "Cita anulada por el cliente: " + clientName(app) + ", " + when, "Cita anulada",
                    clientName(app) + " ha anulado su cita del " + when + ". El hueco vuelve a estar libre.");
            case RESCHEDULED_BY_CLIENT -> toCenter(app, "Cita cambiada por el cliente: " + clientName(app) + ", " + when, "El cliente ha cambiado su cita",
                    clientName(app) + " ha cambiado su cita de " + treatment + ". Ahora es el " + when
                            + (app.getPreviousDateTime() != null ? " (antes era el " + when(app.getPreviousDateTime()) + ")" : "")
                            + (app.getWorker() != null ? " con " + app.getWorker().getName() : "") + ".");
            default -> {
                // PROPOSAL_WITHDRAWN: basta con la campana
            }
        }
    }

    /**
     * Recordatorio a los clientes con cita confirmada en las próximas 24 h. Devuelve cuántos se han enviado.
     * Cada cita se apunta como recordada antes de enviar el correo y en su propia transacción: si algo falla
     * (la BD, el SMTP) se pierde como mucho ese recordatorio, pero nunca se reenvía cada minuto ni bloquea los demás.
     */
    public int sendReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> upcoming = appointments.findByStatusAndReminderSentAtIsNullAndDateTimeBetween(
                AppointmentStatus.CONFIRMED, now, now.plusHours(24));
        int sent = 0;
        for (Appointment app : upcoming) {
            if (appointments.markReminderSent(app.getId(), now) == 0) {
                continue;
            }
            toClient(app, "Recordatorio: " + app.getTreatment().getName() + ", " + when(app.getDateTime()),
                    "Te esperamos pronto", "Te recordamos tu cita en " + app.getCenter().getName() + " el " + when(app.getDateTime())
                            + ". Si no puedes venir, anúlala desde «Mis Citas» para que otra persona pueda aprovechar el hueco.");
            sent++;
        }
        return sent;
    }

    private void toClient(Appointment app, String subject, String heading, String intro) {
        User client = app.getClient();
        if (client == null || !hasRealEmail(client.getEmail())) {
            return;
        }
        // Un centro sin publicar no escribe a clientes: lo puede dar de alta cualquiera y elegir el nombre del centro,
        // del tratamiento y el mensaje, así que serviría para mandar correos de Cuquora a cualquier dirección.
        if (!app.getCenter().isPublished()) {
            log.info("Correo de cita no enviado a {}: el centro {} aún no está publicado", client.getEmail(), app.getCenter().getId());
            return;
        }
        if (!dailyQuota.tryUse(app.getCenter().getId(), LocalDate.now(clock))) {
            log.warn("Correo de cita no enviado a {}: el centro {} ha llegado a {} correos a clientes hoy",
                    client.getEmail(), app.getCenter().getId(), dailyQuota.max());
            return;
        }
        send(client.getEmail(), subject, heading, "Hola, " + client.getName(), intro, app, "/client/appointments", "Ver mis citas");
    }

    private void toCenter(Appointment app, String subject, String heading, String intro) {
        String to = app.getCenter().getEmail();
        if (!hasRealEmail(to) && app.getWorker() != null) {
            to = app.getWorker().getEmail();
        }
        if (!hasRealEmail(to)) {
            return;
        }
        send(to, subject, heading, app.getCenter().getName(), intro, app,
                "/worker/calendar?date=" + app.getDateTime().toLocalDate(), "Abrir la agenda");
    }

    private void send(String to, String subject, String heading, String subtitle, String intro,
                      Appointment app, String path, String buttonLabel) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("heading", heading);
        vars.put("subtitle", subtitle);
        vars.put("intro", intro);
        vars.put("details", details(app));
        vars.put("proposals", app.getStatus() == AppointmentStatus.PROPOSED
                ? app.getProposals().stream().map(p -> capitalize(when(p.getDateTime()))).toList()
                : List.of());
        vars.put("note", app.getWorkerMessage() == null || app.getWorkerMessage().isBlank() ? null : app.getWorkerMessage());
        vars.put("buttonUrl", siteUrl.isEmpty() ? null : siteUrl + path);
        vars.put("buttonLabel", buttonLabel);
        try {
            String html = templateEngine.process("email/appointment", new Context(ES, vars));
            gateway.send(new EmailMessage(to, null, null, subject + " - Cuquora", html));
            log.info("Correo de cita enviado a {}: {}", to, subject);
        } catch (Exception e) {
            // Un correo que falla no debe afectar a la cita
            log.error("Fallo al enviar el correo de cita '{}' a {}", subject, to, e);
        }
    }

    private static List<String[]> details(Appointment app) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Tratamiento", app.getTreatment().getName() + " (" + app.getTreatment().getDuration() + " min)"});
        rows.add(new String[]{"Centro", app.getCenter().getName()});
        if (app.getCenter().getAddress() != null && !app.getCenter().getAddress().isBlank()) {
            rows.add(new String[]{"Dirección", app.getCenter().getAddress()});
        }
        rows.add(new String[]{app.getStatus() == AppointmentStatus.PROPOSED ? "Hora que pediste" : "Fecha", capitalize(when(app.getDateTime()))});
        if (app.getWorker() != null) {
            rows.add(new String[]{"Profesional", app.getWorker().getName()});
        }
        if (app.getClient() != null) {
            rows.add(new String[]{"Cliente", app.getClient().getName()});
        }
        return rows;
    }

    private static String clientName(Appointment app) {
        return app.getClient() != null ? app.getClient().getName() : "Un cliente";
    }

    private static String when(LocalDateTime dateTime) {
        return dateTime.format(DATE) + " a las " + dateTime.format(TIME);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static boolean hasRealEmail(String email) {
        return email != null && !email.isBlank() && !email.toLowerCase(Locale.ROOT).endsWith(User.PLACEHOLDER_EMAIL_DOMAIN);
    }

    /**
     * Tope de correos a clientes por centro y día (recordatorios incluidos). El plan gratis de Brevo da 300 al día para
     * toda la web: un centro que se pase (por un error o a propósito) no puede dejar sin avisos a los demás.
     */
    static final class DailyQuota {
        private final int max;
        private final Map<Long, int[]> used = new HashMap<>();
        private LocalDate day;

        DailyQuota(int max) {
            this.max = max;
        }

        int max() {
            return max;
        }

        synchronized boolean tryUse(Long centerId, LocalDate today) {
            if (!today.equals(day)) {
                used.clear();
                day = today;
            }
            int[] count = used.computeIfAbsent(centerId, id -> new int[1]);
            if (count[0] >= max) {
                return false;
            }
            count[0]++;
            return true;
        }
    }
}
