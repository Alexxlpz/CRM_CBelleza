package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Avisos de la campana del panel del trabajador (stock, citas y fichas pendientes). */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Set<AppointmentStatus> VISIT_STATUSES = Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final InventoryRepository inventoryRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientCardRepository clientCardRepository;
    private final ClientCardJson json;
    private final Clock clock;

    public NotificationService(InventoryRepository inventoryRepository,
                               AppointmentRepository appointmentRepository,
                               ClientCardRepository clientCardRepository,
                               ClientCardJson json,
                               Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientCardRepository = clientCardRepository;
        this.json = json;
        this.clock = clock;
    }

    public List<NotificationItem> getNotificationsForCenter(Long centerId) {
        if (centerId == null) {
            return List.of();
        }
        List<NotificationItem> notifications = new ArrayList<>(stockAlerts(centerId));
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
        notifications.addAll(appointmentAlerts(appointments));
        pendingCardsAlert(centerId, appointments).ifPresent(notifications::add);
        return notifications;
    }

    private List<NotificationItem> stockAlerts(Long centerId) {
        List<NotificationItem> alerts = new ArrayList<>();
        for (Inventory item : inventoryRepository.findByCenterId(centerId)) {
            String product = item.getProduct().getName();
            if (item.getStock() == 0) {
                alerts.add(new NotificationItem("¡Agotado! " + product + " requiere reposición urgente.", "/worker/inventory"));
            } else if (item.getStock() <= InventoryService.LOW_STOCK_THRESHOLD) {
                alerts.add(new NotificationItem("Stock bajo: quedan " + item.getStock() + " ud de " + product + ".", "/worker/inventory"));
            }
        }
        return alerts;
    }

    private List<NotificationItem> appointmentAlerts(List<Appointment> appointments) {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        List<Appointment> pending = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                .toList();
        long todayCount = countConfirmedOn(appointments, today);
        long tomorrowCount = countConfirmedOn(appointments, tomorrow);

        List<NotificationItem> alerts = new ArrayList<>();
        if (!pending.isEmpty()) {
            LocalDate earliest = pending.stream().map(a -> a.getDateTime().toLocalDate()).min(LocalDate::compareTo).orElse(today);
            int n = pending.size();
            alerts.add(new NotificationItem(
                    "Tienes " + n + plural(n, " solicitud", " solicitudes") + " de cita " + plural(n, "pendiente", "pendientes") + " de aprobación.",
                    "/worker/calendar?date=" + earliest));
        }
        if (todayCount > 0) {
            alerts.add(new NotificationItem(
                    "Tienes " + todayCount + plural(todayCount, " cita confirmada", " citas confirmadas") + " para hoy.",
                    "/worker/calendar?date=" + today));
        }
        if (tomorrowCount > 0) {
            alerts.add(new NotificationItem(
                    "Tienes " + tomorrowCount + plural(tomorrowCount, " cita confirmada", " citas confirmadas") + " para mañana.",
                    "/worker/calendar?date=" + tomorrow));
        }
        return alerts;
    }

    /** Clientes atendidos (hasta hoy) cuya ficha sigue vacía. */
    private java.util.Optional<NotificationItem> pendingCardsAlert(Long centerId, List<Appointment> appointments) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, String> cardsData = clientCardRepository.findByCenterId(centerId).stream()
                .filter(card -> card.getClient() != null)
                .collect(Collectors.toMap(card -> card.getClient().getId(), ClientCard::getDataJson, (a, b) -> a));

        Map<Long, User> pendingClients = new LinkedHashMap<>();
        for (Appointment app : appointments) {
            User client = app.getClient();
            if (client != null
                    && VISIT_STATUSES.contains(app.getStatus())
                    && !app.getDateTime().toLocalDate().isAfter(today)
                    && !json.hasAnyValue(cardsData.get(client.getId()))) {
                pendingClients.putIfAbsent(client.getId(), client);
            }
        }
        if (pendingClients.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (pendingClients.size() == 1) {
            User client = pendingClients.values().iterator().next();
            return java.util.Optional.of(new NotificationItem(
                    "Ficha pendiente: recuerda rellenar la ficha de " + client.getName() + " tras su cita.",
                    "/worker/clients/" + client.getId()));
        }
        return java.util.Optional.of(new NotificationItem(
                "Tienes " + pendingClients.size() + " fichas de clientes pendientes de rellenar tras sus citas.",
                "/worker/clients"));
    }

    private static long countConfirmedOn(List<Appointment> appointments, LocalDate date) {
        return appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(a -> a.getDateTime().toLocalDate().isEqual(date))
                .count();
    }

    private static String plural(long n, String singular, String pluralForm) {
        return n == 1 ? singular : pluralForm;
    }

    public record NotificationItem(String message, String url) {
    }
}
