package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Campana del cliente: citas de hoy y de mañana, y cambios que ha hecho el centro en sus citas
 * (confirmada, movida, propuesta de otras horas, anulada...) en los últimos {@value #RECENT_DAYS} días.
 * Lo posterior a la última vez que abrió la campana cuenta como nuevo.
 */
@Service
public class ClientNotificationService {

    static final int RECENT_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final Clock clock;

    public ClientNotificationService(AppointmentRepository appointments, UserRepository users, Clock clock) {
        this.appointments = appointments;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClientNotifications forClient(Long clientId) {
        User client = users.findById(clientId).orElse(null);
        if (client == null) {
            return new ClientNotifications(List.of(), 0);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime seenAt = client.getNotificationsSeenAt();
        List<Entry> entries = new ArrayList<>();

        for (Appointment app : appointments.findByClientIdOrderByDateTimeDesc(clientId)) {
            LocalDate today = CenterClock.now(clock, app.getCenter()).toLocalDate();
            LocalDate day = app.getDateTime().toLocalDate();
            if (app.getStatus() == AppointmentStatus.CONFIRMED && (day.equals(today) || day.equals(today.plusDays(1)))) {
                String when = day.equals(today) ? "Hoy" : "Mañana";
                boolean unseen = seenAt == null || seenAt.toLocalDate().isBefore(now.toLocalDate());
                entries.add(new Entry(true, app.getDateTime(), new ClientNotification(
                        when + " a las " + app.getDateTime().format(TIME) + " tienes " + app.getTreatment().getName()
                                + " en " + app.getCenter().getName() + ".",
                        "/client/appointments", "lucide-alarm-clock", unseen)));
            }
            String change = changeMessage(app);
            if (change != null && app.getLastChangeAt() != null && app.getLastChangeAt().isAfter(now.minusDays(RECENT_DAYS))) {
                boolean unseen = seenAt == null || app.getLastChangeAt().isAfter(seenAt);
                entries.add(new Entry(false, app.getLastChangeAt(), new ClientNotification(change, "/client/appointments",
                        icon(app.getLastChange()), unseen)));
            }
        }

        // Primero las citas de hoy y mañana, después los cambios del más reciente al más antiguo
        List<ClientNotification> items = new ArrayList<>();
        entries.stream().filter(Entry::upcoming).sorted(Comparator.comparing(Entry::at)).map(Entry::item).forEach(items::add);
        entries.stream().filter(e -> !e.upcoming()).sorted(Comparator.comparing(Entry::at).reversed()).map(Entry::item).forEach(items::add);
        int unseen = (int) items.stream().filter(ClientNotification::unseen).count();
        return new ClientNotifications(items, unseen);
    }

    /** El cliente ha abierto la campana: lo de hasta ahora deja de contar como nuevo. */
    @Transactional
    public void markSeen(Long clientId) {
        users.findById(clientId).ifPresent(user -> {
            user.setNotificationsSeenAt(LocalDateTime.now(clock));
            users.save(user);
        });
    }

    /** Texto del aviso para los cambios que hace el centro (los del propio cliente no se avisan). */
    private static String changeMessage(Appointment app) {
        AppointmentChange change = app.getLastChange();
        if (change == null || change.side() == AppointmentChange.Side.CLIENT) {
            return null;
        }
        String treatment = app.getTreatment().getName();
        String center = app.getCenter().getName();
        String when = app.getDateTime().format(DAY) + " a las " + app.getDateTime().format(TIME);
        return switch (change) {
            case CONFIRMED -> center + " ha confirmado tu cita de " + treatment + " del " + when + ".";
            case CREATED_BY_CENTER -> center + " te ha reservado " + treatment + " el " + when + ".";
            case REJECTED -> center + " no puede atender tu solicitud de " + treatment + " del " + when + ".";
            case RESCHEDULED -> "Tu cita de " + treatment + " en " + center + " ha cambiado: ahora es el " + when
                    + (app.getPreviousDateTime() != null
                    ? " (antes " + app.getPreviousDateTime().format(DAY) + " a las " + app.getPreviousDateTime().format(TIME) + ")." : ".");
            case PROPOSED -> center + " te propone otras horas para " + treatment + ". Elige la que te venga bien.";
            case PROPOSAL_WITHDRAWN -> center + " ha retirado la propuesta de " + treatment + ": tu solicitud sigue pendiente.";
            case CANCELLED_BY_CENTER -> center + " ha anulado tu cita de " + treatment + " del " + when + ".";
            case PROPOSAL_EXPIRED -> "La propuesta de " + center + " para " + treatment + " ha caducado sin respuesta.";
            default -> null;
        };
    }

    private static String icon(AppointmentChange change) {
        return switch (change) {
            case CONFIRMED, CREATED_BY_CENTER -> "lucide-calendar-check";
            case RESCHEDULED, PROPOSED -> "lucide-calendar-clock";
            case REJECTED, CANCELLED_BY_CENTER, PROPOSAL_EXPIRED -> "lucide-calendar-x";
            default -> "lucide-calendar";
        };
    }

    /** {@code upcoming}: cita de hoy o mañana; si no, un cambio hecho en {@code at}. */
    private record Entry(boolean upcoming, LocalDateTime at, ClientNotification item) {
    }

    public record ClientNotification(String message, String url, String icon, boolean unseen) {
    }

    public record ClientNotifications(List<ClientNotification> items, int unseen) {
    }
}
