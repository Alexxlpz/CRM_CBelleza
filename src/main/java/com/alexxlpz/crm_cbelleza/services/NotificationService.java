package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {
    private final InventoryRepository inventoryRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientCardRepository clientCardRepository;

    public NotificationService(InventoryRepository inventoryRepository,
                               AppointmentRepository appointmentRepository,
                               ClientCardRepository clientCardRepository) {
        this.inventoryRepository = inventoryRepository;
        this.appointmentRepository = appointmentRepository;
        this.clientCardRepository = clientCardRepository;
    }

    public List<NotificationItem> getNotificationsForCenter(Long centerId) {
        List<NotificationItem> notifications = new ArrayList<>();
        if (centerId == null) return notifications;

        List<Inventory> inventoryItems = inventoryRepository.findByCenterId(centerId);
        for (Inventory item : inventoryItems) {
            if (item.getStock() == 0) {
                notifications.add(new NotificationItem(
                        "¡Agotado! " + item.getProduct().getName() + " requiere reposición urgente.",
                        "/worker/inventory"));
            } else if (item.getStock() <= 3) {
                notifications.add(new NotificationItem(
                        "Stock bajo: Quedan " + item.getStock() + " ud de " + item.getProduct().getName() + ".",
                        "/worker/inventory"));
            }
        }

        appointmentRepository.updatePastConfirmedToCompleted(LocalDateTime.now());
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        int todayCount = 0;
        int tomorrowCount = 0;
        int pendingCount = 0;
        LocalDate nearlyPendingDate = null;

        for (Appointment app : appointments) {
            if (app.getStatus() == AppointmentStatus.PENDING) {
                pendingCount++;
                if (nearlyPendingDate == null || app.getDateTime().toLocalDate().isBefore(nearlyPendingDate)) {
                    nearlyPendingDate = app.getDateTime().toLocalDate();
                }
            }
            LocalDate date = app.getDateTime().toLocalDate();
            if (date.isEqual(today) && app.getStatus() == AppointmentStatus.CONFIRMED) todayCount++;
            if (date.isEqual(tomorrow) && app.getStatus() == AppointmentStatus.CONFIRMED) tomorrowCount++;
        }

        if (pendingCount > 0) {
            notifications.add(new NotificationItem(
                    "Tienes " + pendingCount + " solicitud" + (pendingCount > 1 ? "es" : "") + " de cita pendiente" + (pendingCount > 1 ? "s" : "") + " de aprobación.",
                    "/worker/calendar?date=" + nearlyPendingDate));
        }
        if (todayCount > 0) {
            notifications.add(new NotificationItem(
                    "Tienes " + todayCount + " cita" + (todayCount > 1 ? "s" : "") + " confirmada" + (todayCount > 1 ? "s" : "") + " para hoy.",
                    "/worker/calendar?date=" + today));
        }
        if (tomorrowCount > 0) {
            notifications.add(new NotificationItem(
                    "Tienes " + tomorrowCount + " cita" + (tomorrowCount > 1 ? "s" : "") + " confirmada" + (tomorrowCount > 1 ? "s" : "") + " para mañana.",
                    "/worker/calendar?date=" + tomorrow));
        }

        // Pending client cards alert
        Set<String> inspectedClients = new HashSet<>();
        int pendingCardsCount = 0;
        String samplePendingClientName = null;
        String samplePendingUrl = "/worker/clients";

        for (Appointment app : appointments) {
            if ((app.getStatus() == AppointmentStatus.CONFIRMED || app.getStatus() == AppointmentStatus.COMPLETED) && !app.getDateTime().toLocalDate().isAfter(today)) {
                String clientKey = app.getClient() != null ? "user_" + app.getClient().getId() : "guest_" + app.getGuestPhone();
                if (inspectedClients.add(clientKey)) {
                    boolean hasCardData = false;
                    if (app.getClient() != null) {
                        var card = clientCardRepository.findByCenterIdAndClientId(centerId, app.getClient().getId());
                        if (card.isPresent() && card.get().getDataJson() != null && !card.get().getDataJson().trim().isEmpty() && !card.get().getDataJson().equals("{}")) {
                            hasCardData = true;
                        }
                        if (!hasCardData) {
                            pendingCardsCount++;
                            if (samplePendingClientName == null) {
                                samplePendingClientName = app.getClient().getName();
                                samplePendingUrl = "/worker/clients/" + app.getClient().getId();
                            }
                        }
                    } else if (app.getGuestPhone() != null && !app.getGuestPhone().trim().isEmpty()) {
                        var card = clientCardRepository.findByCenterIdAndGuestPhone(centerId, app.getGuestPhone().trim());
                        if (card.isPresent() && card.get().getDataJson() != null && !card.get().getDataJson().trim().isEmpty() && !card.get().getDataJson().equals("{}")) {
                            hasCardData = true;
                        }
                        if (!hasCardData) {
                            pendingCardsCount++;
                            if (samplePendingClientName == null) {
                                samplePendingClientName = app.getGuestName() != null ? app.getGuestName() : "Invitado";
                                samplePendingUrl = "/worker/clients/guest?phone=" + app.getGuestPhone().trim();
                            }
                        }
                    }
                }
            }
        }

        if (pendingCardsCount > 0) {
            String msg = pendingCardsCount == 1
                    ? "Ficha pendiente: Recuerda rellenar la ficha de " + samplePendingClientName + " tras su cita."
                    : "Tienes " + pendingCardsCount + " fichas de clientes pendientes de rellenar tras sus citas.";
            notifications.add(new NotificationItem(msg, samplePendingUrl));
        }

        return notifications;
    }

    public record NotificationItem(String message, String url) {
    }
}
