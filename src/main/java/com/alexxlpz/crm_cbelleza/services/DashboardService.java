package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Contadores del panel principal del trabajador. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Set<AppointmentStatus> ATTENDED = Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointmentRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public DashboardService(AppointmentRepository appointmentRepository,
                            InventoryRepository inventoryRepository,
                            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    public DashboardStats statsFor(Long centerId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);

        int todayCount = countAttendedOn(appointments, today);
        int tomorrowCount = countAttendedOn(appointments, tomorrow);
        int pending = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING).count();
        int lowStock = (int) inventoryRepository.findByCenterId(centerId).stream()
                .filter(item -> item.getStock() <= InventoryService.LOW_STOCK_THRESHOLD)
                .count();

        return new DashboardStats(todayCount, tomorrowCount, pending, lowStock);
    }

    private static int countAttendedOn(List<Appointment> appointments, LocalDate date) {
        return (int) appointments.stream()
                .filter(a -> a.getDateTime().toLocalDate().isEqual(date))
                .filter(a -> ATTENDED.contains(a.getStatus()))
                .count();
    }

    public record DashboardStats(int todayAppointments, int tomorrowAppointments,
                                 int pendingAppointments, int lowStockCount) {
    }
}
