package com.alexxlpz.crm_cbelleza.googlecalendar;

import com.alexxlpz.crm_cbelleza.booking.CenterTimeZones;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.GoogleCalendarLink;
import com.alexxlpz.crm_cbelleza.entities.Role;
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
import java.util.Objects;

/**
 * Sincronización de las citas con el Google Calendar de cada trabajador y de cada cliente.
 * <p>
 * No hace falta haber entrado con Google: el trabajador (desde «Mi Centro») o el cliente (desde «Mi Perfil»)
 * concede permiso con la cuenta de Google que quiera. Cuquora crea en esa cuenta un calendario propio
 * y solo puede tocar ese calendario (ámbito {@code calendar.app.created}), nunca el resto de su agenda.
 * Las citas confirmadas se copian ahí y se actualizan o borran al cambiar. Una misma cita puede estar a la vez
 * en el calendario del trabajador ({@code googleEventId}) y en el del cliente ({@code clientGoogleEventId}).
 */
@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final String AUTHORIZATION_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    /** openid + email solo para mostrar en el perfil qué cuenta de Google está conectada. */
    static final String SCOPES = "openid email https://www.googleapis.com/auth/calendar.app.created";
    static final String CLIENT_CALENDAR_NAME = "Mis citas · Cuquora";

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

    /** Estado del interruptor de «Mi Centro» (trabajador) o de «Mi Perfil» (cliente). */
    @Transactional(readOnly = true)
    public CalendarSyncStatus status(Long userId) {
        if (!settings.isEnabled()) {
            return new CalendarSyncStatus(false, false, null);
        }
        return links.findByUserId(userId)
                .map(link -> new CalendarSyncStatus(true, true, link.getGoogleEmail()))
                .orElseGet(() -> new CalendarSyncStatus(true, false, null));
    }

    /**
     * Pantalla de permiso de Google. {@code access_type=offline} y {@code prompt=consent} para recibir
     * siempre un token de actualización: las citas se sincronizan aunque la persona no tenga la sesión abierta.
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
    public int connect(Long userId, String code, String redirectUri) {
        ensureEnabled();
        User user = users.findWithCenterById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        links.findByUserId(userId).ifPresent(this::unlink);

        boolean client = user.getRole() == Role.CLIENT;
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> upcoming = client
                ? appointments.findByClientIdAndStatusAndDateTimeAfter(userId, AppointmentStatus.CONFIRMED, now)
                : appointments.findByWorkerIdAndStatusAndDateTimeAfter(userId, AppointmentStatus.CONFIRMED, now);

        GoogleAuthorization authorization;
        String calendarId;
        try {
            authorization = google.exchangeCode(code, redirectUri);
            if (authorization.refreshToken() == null) {
                throw new BusinessRuleException("Google no ha concedido el acceso al calendario. Vuelve a activarlo y acepta todos los permisos.");
            }
            calendarId = client
                    ? google.createCalendar(authorization.refreshToken(), CLIENT_CALENDAR_NAME, clientTimeZone(upcoming))
                    : google.createCalendar(authorization.refreshToken(), "Cuquora · " + user.getCenter().getName(),
                    user.getCenter().getTimeZone());
        } catch (GoogleCalendarException e) {
            log.warn("No se ha podido conectar Google Calendar del usuario {}", userId, e);
            throw new BusinessRuleException("No se ha podido conectar con Google Calendar. Inténtalo de nuevo en unos minutos.");
        }

        GoogleCalendarLink link = links.save(GoogleCalendarLink.builder()
                .user(user)
                .googleEmail(authorization.email())
                .encryptedRefreshToken(cipher.encrypt(authorization.refreshToken()))
                .calendarId(calendarId)
                .connectedAt(now)
                .build());

        int copied = 0;
        for (Appointment appointment : upcoming) {
            if (push(link, appointment)) {
                copied++;
            }
        }
        return copied;
    }

    /** Apaga el interruptor: borra el calendario de Cuquora de su cuenta y retira el permiso. */
    @Transactional
    public void disconnect(Long userId) {
        links.findByUserId(userId).ifPresent(link -> {
            String refreshToken = cipher.decrypt(link.getEncryptedRefreshToken());
            try {
                google.deleteCalendar(refreshToken, link.getCalendarId());
                google.revoke(refreshToken);
            } catch (GoogleCalendarException e) {
                // Aunque Google falle, en Cuquora queda desconectado; el permiso se puede quitar desde la cuenta de Google
                log.warn("No se ha podido limpiar Google Calendar del usuario {}", userId, e);
            }
            unlink(link);
        });
    }

    /**
     * Lleva a Google el estado actual de una cita, al calendario del trabajador y al del cliente:
     * crea o actualiza el evento si está confirmada y lo borra si no. Lo llama {@link GoogleCalendarSyncListener}
     * tras guardar la cita.
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
        boolean confirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;
        syncWorkerCalendar(appointment, confirmed);
        syncClientCalendar(appointment, confirmed);
    }

    private void syncWorkerCalendar(Appointment appointment, boolean confirmed) {
        Long workerId = appointment.getWorker() != null ? appointment.getWorker().getId() : null;

        // El evento está en el calendario de otra persona (se cambió de profesional) o la cita ya no está confirmada:
        // se borra de donde esté
        if (appointment.getGoogleEventId() != null) {
            Long owner = appointment.getGoogleEventOwnerId() != null ? appointment.getGoogleEventOwnerId() : workerId;
            if (!confirmed || !Objects.equals(owner, workerId)) {
                links.findByUserId(owner).ifPresentOrElse(
                        link -> remove(link, appointment),
                        () -> forgetEvent(appointment, false));
            }
        }
        if (confirmed && workerId != null) {
            links.findByUserId(workerId).ifPresent(link -> push(link, appointment));
        }
    }

    private void syncClientCalendar(Appointment appointment, boolean confirmed) {
        if (appointment.getClient() == null) {
            return;
        }
        var link = links.findByUserId(appointment.getClient().getId());
        if (confirmed) {
            link.ifPresent(l -> push(l, appointment));
        } else if (appointment.getClientGoogleEventId() != null) {
            link.ifPresentOrElse(l -> remove(l, appointment), () -> forgetEvent(appointment, true));
        }
    }

    /** Crea o actualiza el evento. Devuelve {@code false} si Google ha fallado (la cita sigue igual en Cuquora). */
    private boolean push(GoogleCalendarLink link, Appointment appointment) {
        boolean client = isClient(link);
        String refreshToken = cipher.decrypt(link.getEncryptedRefreshToken());
        CalendarEvent event = client ? toClientEvent(appointment) : toWorkerEvent(appointment);
        try {
            String eventId = client ? appointment.getClientGoogleEventId() : appointment.getGoogleEventId();
            if (eventId == null || !google.updateEvent(refreshToken, link.getCalendarId(), eventId, event)) {
                String newId = google.insertEvent(refreshToken, link.getCalendarId(), event);
                if (client) {
                    appointment.setClientGoogleEventId(newId);
                } else {
                    appointment.setGoogleEventId(newId);
                    appointment.setGoogleEventOwnerId(link.getUser().getId());
                }
                appointments.save(appointment);
            }
            return true;
        } catch (GoogleCalendarException e) {
            handleFailure(link, appointment, e);
            return false;
        }
    }

    private void remove(GoogleCalendarLink link, Appointment appointment) {
        boolean client = isClient(link);
        String eventId = client ? appointment.getClientGoogleEventId() : appointment.getGoogleEventId();
        try {
            google.deleteEvent(cipher.decrypt(link.getEncryptedRefreshToken()), link.getCalendarId(), eventId);
            forgetEvent(appointment, client);
        } catch (GoogleCalendarException e) {
            handleFailure(link, appointment, e);
        }
    }

    private void forgetEvent(Appointment appointment, boolean client) {
        if (client) {
            appointment.setClientGoogleEventId(null);
        } else {
            appointment.setGoogleEventId(null);
            appointment.setGoogleEventOwnerId(null);
        }
        appointments.save(appointment);
    }

    private void handleFailure(GoogleCalendarLink link, Appointment appointment, GoogleCalendarException e) {
        if (e.isAccessRevoked()) {
            // Quitó el permiso desde Google: el interruptor vuelve a salir apagado
            log.info("El usuario {} ha retirado el permiso de Google Calendar; se desactiva la sincronización",
                    link.getUser().getId());
            unlink(link);
        } else {
            log.warn("No se ha podido sincronizar la cita {} con Google Calendar", appointment.getId(), e);
        }
    }

    /** Olvida la conexión y los eventos apuntados en sus citas. */
    private void unlink(GoogleCalendarLink link) {
        Long userId = link.getUser().getId();
        if (isClient(link)) {
            List<Appointment> synced = appointments.findByClientIdAndClientGoogleEventIdIsNotNull(userId);
            synced.forEach(appointment -> appointment.setClientGoogleEventId(null));
            appointments.saveAll(synced);
        } else {
            List<Appointment> synced = new ArrayList<>(appointments.findByWorkerIdAndGoogleEventIdIsNotNull(userId));
            appointments.findByGoogleEventOwnerId(userId).stream()
                    .filter(appointment -> !synced.contains(appointment))
                    .forEach(synced::add);
            synced.forEach(appointment -> {
                appointment.setGoogleEventId(null);
                appointment.setGoogleEventOwnerId(null);
            });
            appointments.saveAll(synced);
        }
        links.delete(link);
        links.flush();
    }

    private static boolean isClient(GoogleCalendarLink link) {
        return link.getUser().getRole() == Role.CLIENT;
    }

    /** El calendario del cliente toma la zona de su próxima cita; cada evento lleva siempre la zona de su centro. */
    private static String clientTimeZone(List<Appointment> upcoming) {
        return upcoming.isEmpty() ? CenterTimeZones.DEFAULT : upcoming.getFirst().getCenter().getTimeZone();
    }

    private CalendarEvent toWorkerEvent(Appointment appointment) {
        String client = appointment.getClient() != null ? appointment.getClient().getName() : appointment.getGuestName();
        String phone = appointment.getClient() != null ? appointment.getClient().getPhone() : appointment.getGuestPhone();
        var treatment = appointment.getTreatment();

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
        return event(appointment, treatment.getName() + (client != null ? " · " + client : ""), description);
    }

    /** Lo que ve el cliente: centro, profesional y mensaje del centro. Nunca el resumen interno del tratamiento. */
    private CalendarEvent toClientEvent(Appointment appointment) {
        var treatment = appointment.getTreatment();
        var center = appointment.getCenter();

        StringBuilder description = new StringBuilder();
        description.append("Tratamiento: ").append(treatment.getName())
                .append(" (").append(treatment.getDuration()).append(" min)");
        description.append("\nCentro: ").append(center.getName());
        if (center.getPhone() != null && !center.getPhone().isBlank()) {
            description.append("\nTeléfono del centro: ").append(center.getPhone());
        }
        if (appointment.getWorker() != null) {
            description.append("\nTe atiende: ").append(appointment.getWorker().getName());
        }
        if (appointment.getWorkerMessage() != null && !appointment.getWorkerMessage().isBlank()) {
            description.append("\nMensaje del centro: ").append(appointment.getWorkerMessage());
        }
        if (!siteUrl.isEmpty()) {
            description.append("\n\nVer o anular en Cuquora: ").append(siteUrl).append("/client/appointments");
        }
        return event(appointment, treatment.getName() + " · " + center.getName(), description);
    }

    private static CalendarEvent event(Appointment appointment, String summary, StringBuilder description) {
        var center = appointment.getCenter();
        String location = center.getAddress() == null || center.getAddress().isBlank()
                ? center.getName()
                : center.getName() + ", " + center.getAddress();
        return new CalendarEvent(
                summary,
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

    /** {@code available}: el servidor tiene las claves de Google; {@code connected}: esta persona la ha activado. */
    public record CalendarSyncStatus(boolean available, boolean connected, String googleEmail) {
    }
}
