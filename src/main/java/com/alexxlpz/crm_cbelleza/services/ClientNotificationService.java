package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Campana del cliente: citas de hoy y de mañana, y cambios que ha hecho el centro en sus citas
 * (confirmada, movida, propuesta de otras horas, anulada...) en los últimos {@value #RECENT_DAYS} días.
 * También recuerda valorar las citas completadas de esos días y avisa cuando el centro responde a una valoración.
 * Lo posterior a la última vez que abrió la campana cuenta como nuevo.
 */
@Service
public class ClientNotificationService {

    static final int RECENT_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final ReviewRepository reviews;
    private final NotificationDismissalService dismissals;
    private final CenterService centers;
    private final Clock clock;

    public ClientNotificationService(AppointmentRepository appointments, UserRepository users, ReviewRepository reviews,
                                     NotificationDismissalService dismissals, CenterService centers, Clock clock) {
        this.centers = centers;
        this.appointments = appointments;
        this.users = users;
        this.reviews = reviews;
        this.dismissals = dismissals;
        this.clock = clock;
    }

    /** Sin los avisos que el cliente ha quitado de la campana. */
    @Transactional
    public ClientNotifications forClient(Long clientId) {
        User client = users.findById(clientId).orElse(null);
        if (client == null) {
            return new ClientNotifications(List.of(), 0);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime seenAt = client.getNotificationsSeenAt();
        List<Entry> entries = new ArrayList<>();
        Set<Long> reviewed = reviews.findRatingsByClient(clientId).stream()
                .map(row -> (Long) row[0])
                .collect(Collectors.toSet());

        for (Appointment app : appointments.findByClientIdOrderByDateTimeDesc(clientId)) {
            LocalDateTime centerNow = CenterClock.now(clock, app.getCenter());
            LocalDate today = centerNow.toLocalDate();
            LocalDate day = app.getDateTime().toLocalDate();
            if (app.getStatus() == AppointmentStatus.CONFIRMED && (day.equals(today) || day.equals(today.plusDays(1)))) {
                String when = day.equals(today) ? "Hoy" : "Mañana";
                boolean unseen = seenAt == null || seenAt.toLocalDate().isBefore(now.toLocalDate());
                entries.add(new Entry(true, app.getDateTime(), new ClientNotification(
                        when + " a las " + app.getDateTime().format(TIME) + " tienes " + app.getTreatment().getName()
                                + " en " + app.getCenter().getName() + ".",
                        "/client/appointments", "lucide-alarm-clock", unseen)));
            }
            if (app.getStatus() == AppointmentStatus.COMPLETED && !reviewed.contains(app.getId())
                    && app.getDateTime().isAfter(centerNow.minusDays(RECENT_DAYS)) && app.getDateTime().isBefore(centerNow)) {
                boolean unseen = seenAt == null || app.getDateTime().isAfter(seenAt);
                entries.add(new Entry(false, app.getDateTime(), new ClientNotification(
                        "¿Qué tal tu " + app.getTreatment().getName() + " en " + app.getCenter().getName()
                                + "? Valóralo desde tu historial.",
                        "/client/appointments", "lucide-star", unseen)));
            }
            String change = changeMessage(app);
            if (change != null && app.getLastChangeAt() != null && app.getLastChangeAt().isAfter(now.minusDays(RECENT_DAYS))) {
                boolean unseen = seenAt == null || app.getLastChangeAt().isAfter(seenAt);
                entries.add(new Entry(false, app.getLastChangeAt(), new ClientNotification(change, "/client/appointments",
                        icon(app.getLastChange()), unseen)));
            }
        }

        Map<Long, String> slugs = centers.shortSlugs();
        for (Review review : reviews.findByClientIdAndRepliedAtAfter(clientId, now.minusDays(RECENT_DAYS))) {
            boolean unseen = seenAt == null || review.getRepliedAt().isAfter(seenAt);
            entries.add(new Entry(false, review.getRepliedAt(), new ClientNotification(
                    review.getCenter().getName() + " ha respondido a tu valoración de "
                            + review.getAppointment().getTreatment().getName() + ".",
                    "/client/centers/" + slugs.get(review.getCenter().getId()) + "#resenas", "lucide-message-square-reply", unseen)));
        }

        // Primero las citas de hoy y mañana, después los cambios del más reciente al más antiguo
        List<ClientNotification> items = new ArrayList<>();
        entries.stream().filter(Entry::upcoming).sorted(Comparator.comparing(Entry::at)).map(Entry::item).forEach(items::add);
        entries.stream().filter(e -> !e.upcoming()).sorted(Comparator.comparing(Entry::at).reversed()).map(Entry::item).forEach(items::add);
        List<ClientNotification> visible = dismissals.visible(clientId, items, ClientNotification::key);
        int unseen = (int) visible.stream().filter(ClientNotification::unseen).count();
        return new ClientNotifications(visible, unseen);
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

    /** {@code key}: huella para poder quitarlo de la campana ({@link NotificationKeys}). */
    public record ClientNotification(String key, String message, String url, String icon, boolean unseen) {
        public ClientNotification(String message, String url, String icon, boolean unseen) {
            this(NotificationKeys.of(message, url), message, url, icon, unseen);
        }
    }

    public record ClientNotifications(List<ClientNotification> items, int unseen) {
    }
}
