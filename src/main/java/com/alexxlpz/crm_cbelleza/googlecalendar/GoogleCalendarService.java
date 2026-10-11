package com.alexxlpz.crm_cbelleza.googlecalendar;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.GoogleCalendarLink;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.GoogleCalendarLinkRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sincronización de la agenda de cada trabajador con su Google Calendar.
 * <p>
 * No hace falta haber entrado con Google: el trabajador concede permiso desde el interruptor de «Mi Centro»
 * con la cuenta de Google que quiera. Cuquora crea en esa cuenta un calendario propio («Cuquora · Centro»)
 * y solo puede tocar ese calendario (ámbito {@code calendar.app.created}), nunca el resto de su agenda.
 * Las citas confirmadas que tiene asignadas se copian ahí y se actualizan o borran al cambiar.
 */
@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    /** openid + email solo para mostrar en el perfil qué cuenta de Google está conectada. */
    static final String SCOPES = "openid email https://www.googleapis.com/auth/calendar.app.created";

    private final GoogleCalendarSettings settings;
    private final GoogleCalendarGateway google;
    private final GoogleTokenCipher cipher;
    private final GoogleCalendarLinkRepository links;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final Clock clock;
    private final String siteUrl;

    public GoogleCalendarService(GoogleCalendarSettings settings,
                                 GoogleCalendarGateway google,
                                 GoogleTokenCipher cipher,
                                 GoogleCalendarLinkRepository links,
                                 AppointmentRepository appointments,
                                 UserRepository users,
                                 Clock clock,
                                 @Value("${crm.site-url:}") String siteUrl) {
        this.settings = settings;
        this.google = google;
        this.cipher = cipher;
        this.links = links;
        this.appointments = appointments;
        this.users = users;
        this.clock = clock;
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
    }

    /** Estado del interruptor del perfil. */
    @Transactional(readOnly = true)
    public CalendarSyncStatus status(Long workerId) {
        if (!settings.isEnabled()) {
            return new CalendarSyncStatus(false, false, null);
        }
        return links.findByWorkerId(workerId)
                .map(link -> new CalendarSyncStatus(true, true, link.getGoogleEmail()))
                .orElseGet(() -> new CalendarSyncStatus(true, false, null));
    }

    /**
     * Pantalla de permiso de Google. {@code access_type=offline} y {@code prompt=consent} para recibir
     * siempre un token de actualización: las citas se sincronizan aunque el trabajador no tenga la sesión abierta.
     */
    public String authorizationUrl(String state, String redirectUri) {
        ensureEnabled();
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_URL)
                .queryParam("client_id", settings.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    /** Vuelta de Google con el permiso concedido: crea el calendario y copia las citas confirmadas futuras. */
    @Transactional
    public int connect(Long workerId, String code, String redirectUri) {
        ensureEnabled();
        User worker = users.findWithCenterById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado."));
        links.findByWorkerId(workerId).ifPresent(this::unlink);

        GoogleAuthorization authorization;
        String calendarId;
        try {
            authorization = google.exchangeCode(code, redirectUri);
            if (authorization.refreshToken() == null) {
                throw new BusinessRuleException("Google no ha concedido el acceso al calendario. Vuelve a activarlo y acepta todos los permisos.");
            }
            String calendarName = "Cuquora · " + worker.getCenter().getName();
            calendarId = google.createCalendar(authorization.refreshToken(), calendarName, worker.getCenter().getTimeZone());
        } catch (GoogleCalendarException e) {
            log.warn("No se ha podido conectar Google Calendar del trabajador {}", workerId, e);
            throw new BusinessRuleException("No se ha podido conectar con Google Calendar. Inténtalo de nuevo en unos minutos.");
        }

        GoogleCalendarLink link = links.save(GoogleCalendarLink.builder()
                .worker(worker)
                .googleEmail(authorization.email())
                .encryptedRefreshToken(cipher.encrypt(authorization.refreshToken()))
                .calendarId(calendarId)
                .connectedAt(LocalDateTime.now(clock))
                .build());

        int copied = 0;
        for (Appointment appointment : appointments.findByWorkerIdAndStatusAndDateTimeAfter(
                workerId, AppointmentStatus.CONFIRMED, LocalDateTime.now(clock))) {
            if (push(link, appointment)) {
                copied++;
            }
        }
        return copied;
    }

    /** Apaga el interruptor: borra el calendario «Cuquora» de su cuenta y retira el permiso. */
    @Transactional
    public void disconnect(Long workerId) {
        links.findByWorkerId(workerId).ifPresent(link -> {
            String refreshToken = cipher.decrypt(link.getEncryptedRefreshToken());
            try {
                google.deleteCalendar(refreshToken, link.getCalendarId());
                google.revoke(refreshToken);
            } catch (GoogleCalendarException e) {
                // Aunque Google falle, en Cuquora queda desconectado; el permiso se puede quitar desde la cuenta de Google
                log.warn("No se ha podido limpiar Google Calendar del trabajador {}", workerId, e);
            }
            unlink(link);
        });
    }

    /**
     * Lleva a Google el estado actual de una cita: crea o actualiza el evento si está confirmada
     * y lo borra si se ha rechazado. Lo llama {@link GoogleCalendarSyncListener} tras guardar la cita.
     */
    @Transactional
    public void syncAppointment(Long appointmentId) {
        if (!settings.isEnabled()) {
            return;
        }
        Appointment appointment = appointments.findWithDetailsById(appointmentId).orElse(null);
        if (appointment == null) {
            return;
        }
        Long workerId = appointment.getWorker() != null ? appointment.getWorker().getId() : null;
        boolean confirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;

        // El evento está en el calendario de otra persona (se cambió de profesional) o la cita ya no está confirmada:
        // se borra de donde esté
        if (appointment.getGoogleEventId() != null) {
            Long owner = appointment.getGoogleEventOwnerId() != null ? appointment.getGoogleEventOwnerId() : workerId;
            if (!confirmed || !java.util.Objects.equals(owner, workerId)) {
                links.findByWorkerId(owner).ifPresentOrElse(
                        link -> remove(link, appointment),
                        () -> forgetEvent(appointment));
            }
        }
        if (confirmed && workerId != null) {
            links.findByWorkerId(workerId).ifPresent(link -> push(link, appointment));
        }
    }

    /** Crea o actualiza el evento. Devuelve {@code false} si Google ha fallado (la cita sigue igual en Cuquora). */
    private boolean push(GoogleCalendarLink link, Appointment appointment) {
        String refreshToken = cipher.decrypt(link.getEncryptedRefreshToken());
        CalendarEvent event = toEvent(appointment);
        try {
            String eventId = appointment.getGoogleEventId();
            if (eventId == null || !google.updateEvent(refreshToken, link.getCalendarId(), eventId, event)) {
                appointment.setGoogleEventId(google.insertEvent(refreshToken, link.getCalendarId(), event));
                appointment.setGoogleEventOwnerId(link.getWorker().getId());
                appointments.save(appointment);
            }
            return true;
        } catch (GoogleCalendarException e) {
            handleFailure(link, appointment, e);
            return false;
        }
    }

    private void remove(GoogleCalendarLink link, Appointment appointment) {
        try {
            google.deleteEvent(cipher.decrypt(link.getEncryptedRefreshToken()), link.getCalendarId(), appointment.getGoogleEventId());
            forgetEvent(appointment);
        } catch (GoogleCalendarException e) {
            handleFailure(link, appointment, e);
        }
    }

    private void forgetEvent(Appointment appointment) {
        appointment.setGoogleEventId(null);
        appointment.setGoogleEventOwnerId(null);
        appointments.save(appointment);
    }

    private void handleFailure(GoogleCalendarLink link, Appointment appointment, GoogleCalendarException e) {
        if (e.isAccessRevoked()) {
            // El trabajador quitó el permiso desde Google: el interruptor del perfil vuelve a salir apagado
            log.info("El trabajador {} ha retirado el permiso de Google Calendar; se desactiva la sincronización",
                    link.getWorker().getId());
            unlink(link);
        } else {
            log.warn("No se ha podido sincronizar la cita {} con Google Calendar", appointment.getId(), e);
        }
    }

    /** Olvida la conexión y los eventos apuntados en sus citas. */
    private void unlink(GoogleCalendarLink link) {
        Long workerId = link.getWorker().getId();
        List<Appointment> synced = new ArrayList<>(appointments.findByWorkerIdAndGoogleEventIdIsNotNull(workerId));
        appointments.findByGoogleEventOwnerId(workerId).stream()
                .filter(appointment -> !synced.contains(appointment))
                .forEach(synced::add);
        synced.forEach(appointment -> {
            appointment.setGoogleEventId(null);
            appointment.setGoogleEventOwnerId(null);
        });
        appointments.saveAll(synced);
        links.delete(link);
        links.flush();
    }

    private CalendarEvent toEvent(Appointment appointment) {
        String client = appointment.getClient() != null ? appointment.getClient().getName() : appointment.getGuestName();
        String phone = appointment.getClient() != null ? appointment.getClient().getPhone() : appointment.getGuestPhone();
        var treatment = appointment.getTreatment();
        var center = appointment.getCenter();

        StringBuilder description = new StringBuilder();
        description.append("Cliente: ").append(client != null ? client : "Sin nombre");
        if (phone != null && !phone.isBlank()) {
            description.append("\nTeléfono: ").append(phone);
        }
        description.append("\nTratamiento: ").append(treatment.getName())
                .append(" (").append(treatment.getDuration()).append(" min)");
        if (appointment.getWorkerMessage() != null && !appointment.getWorkerMessage().isBlank()) {
            description.append("\nMensaje al cliente: ").append(appointment.getWorkerMessage());
        }
        if (!siteUrl.isEmpty()) {
            description.append("\n\nVer en Cuquora: ").append(siteUrl)
                    .append("/worker/calendar?date=").append(appointment.getDateTime().toLocalDate());
        }

        String location = center.getAddress() == null || center.getAddress().isBlank()
                ? center.getName()
                : center.getName() + ", " + center.getAddress();
        return new CalendarEvent(
                treatment.getName() + (client != null ? " · " + client : ""),
                description.toString(),
                location,
                appointment.getDateTime(),
                appointment.getEndDateTime(),
                center.getTimeZone());
    }

    private void ensureEnabled() {
        if (!settings.isEnabled()) {
            throw new BusinessRuleException("La sincronización con Google Calendar todavía no está disponible.");
        }
    }

    /** {@code available}: el servidor tiene las claves de Google; {@code connected}: este trabajador la ha activado. */
    public record CalendarSyncStatus(boolean available, boolean connected, String googleEmail) {
    }
}
