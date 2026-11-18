package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Contadores y citas de hoy del panel principal del trabajador. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Set<AppointmentStatus> ATTENDED = Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);
    /** Lo que sale en «Hoy»: lo que ocupa la agenda del día y las solicitudes que aún hay que contestar. */
    private static final Set<AppointmentStatus> TODAY_LIST = Set.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED,
            AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    private final AppointmentRepository appointmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ClientAllergyService allergyService;
    private final CenterRepository centerRepository;
    private final Clock clock;

    public DashboardService(AppointmentRepository appointmentRepository,
                            InventoryRepository inventoryRepository,
                            ClientAllergyService allergyService,
                            CenterRepository centerRepository,
                            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.allergyService = allergyService;
        this.centerRepository = centerRepository;
        this.clock = clock;
    }

    /** Contadores del panel; «hoy» y «mañana» son los de la hora local del centro. */
    public DashboardStats statsFor(Long centerId) {
        LocalDateTime now = CenterClock.now(clock, centerRepository.findById(centerId).orElse(null));
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(centerId);

        int todayCount = countAttendedOn(appointments, today);
        int tomorrowCount = countAttendedOn(appointments, tomorrow);
        // Las mismas que salen en «Por aprobar»: una solicitud cuya hora ya pasó no se puede aceptar
        int pending = (int) appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING && a.getDateTime().isAfter(now))
                .count();
        int lowStock = (int) inventoryRepository.findByCenterId(centerId).stream()
                .filter(item -> item.getStock() == 0 || item.isLowStock())
                .count();

        return new DashboardStats(todayCount, tomorrowCount, pending, lowStock);
    }

    /**
     * Las citas de hoy del centro por hora, para la lista «Hoy» del panel. «Hoy» y «ahora» son los de la hora local del
     * centro. Cada cita dice si ya pasó, si está en curso o si es la siguiente, y a cuántas citas del centro no se
     * presentó esa persona.
     */
    public List<TodayAppointment> todayFor(Center center) {
        LocalDateTime now = CenterClock.now(clock, center);
        LocalDate today = now.toLocalDate();
        List<Appointment> appointments = appointmentRepository.findByCenterIdOrderByDateTimeDesc(center.getId());
        Map<Long, Long> noShowsByClient = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW && a.getClient() != null)
                .collect(Collectors.groupingBy(a -> a.getClient().getId(), Collectors.counting()));
        Map<Long, String> allergies = allergyService.byClient(center.getId());

        List<Appointment> todays = appointments.stream()
                .filter(a -> a.getDateTime().toLocalDate().isEqual(today))
                .filter(a -> TODAY_LIST.contains(a.getStatus()))
                // Una solicitud de esta mañana que nadie contestó ya no se puede aceptar: no sale en «Hoy»
                .filter(a -> a.getStatus() != AppointmentStatus.PENDING || a.getDateTime().isAfter(now))
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .toList();
        Long nextId = todays.stream()
                .filter(a -> a.getDateTime().isAfter(now) && a.getStatus() != AppointmentStatus.PENDING)
                .map(Appointment::getId)
                .findFirst()
                .orElse(null);

        return todays.stream().map(a -> {
            User client = a.getClient();
            User worker = a.getWorker();
            TimeState state = !a.getEndDateTime().isAfter(now) ? TimeState.PAST
                    : !a.getDateTime().isAfter(now) ? TimeState.NOW
                    : Objects.equals(a.getId(), nextId) ? TimeState.NEXT : TimeState.LATER;
            boolean hasCard = client != null && !client.isDeleted();
            return new TodayAppointment(a.getId(), a.getDateTime(), a.getEndDateTime(),
                    client != null ? client.getId() : null,
                    client != null ? client.getName() : "Cliente",
                    hasCard,
                    a.getTreatment() != null ? a.getTreatment().getName() : "",
                    worker != null ? worker.getId() : null,
                    worker != null ? worker.getName() : null,
                    a.getStatus(),
                    client != null ? noShowsByClient.getOrDefault(client.getId(), 0L).intValue() : 0,
                    state,
                    phoneOf(a),
                    client != null ? allergies.get(client.getId()) : null);
        }).toList();
    }

    /**
     * Solicitudes de clientes que aún hay que contestar, de la más cercana a la más lejana, para «Por aprobar» del
     * panel. Solo las que aún no han empezado (hora del centro): las pasadas ya no se pueden aceptar.
     */
    public List<PendingRequest> pendingFor(Center center) {
        LocalDateTime now = CenterClock.now(clock, center);
        return appointmentRepository.findByCenterIdOrderByDateTimeDesc(center.getId()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.PENDING && a.getDateTime().isAfter(now))
                .sorted(Comparator.comparing(Appointment::getDateTime))
                .map(a -> {
                    User client = a.getClient();
                    User worker = a.getWorker();
                    return new PendingRequest(a.getId(), a.getDateTime(),
                            FriendlyDates.day(a.getDateTime().toLocalDate(), now.toLocalDate()),
                            client != null ? client.getId() : null,
                            client != null ? client.getName() : (a.getGuestName() != null ? a.getGuestName() : "Cliente"),
                            client != null && !client.isDeleted(),
                            a.getTreatment() != null ? a.getTreatment().getName() : "",
                            worker != null ? worker.getName() : null,
                            phoneOf(a));
                })
                .toList();
    }

    private static String phoneOf(Appointment a) {
        if (a.getClient() != null && a.getClient().getPhone() != null && !a.getClient().getPhone().isBlank()) {
            return a.getClient().getPhone();
        }
        return a.getGuestPhone() != null && !a.getGuestPhone().isBlank() ? a.getGuestPhone() : null;
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

    /** Una solicitud de «Por aprobar»: cuándo, quién, qué y con quién la pidió (nulo = «Cualquiera»). */
    public record PendingRequest(Long id, LocalDateTime start, String dayLabel, Long clientId, String clientName, boolean clientHasCard,
                                 String treatmentName, String workerName, String clientPhone) {
    }

    /** En qué momento del día está una cita respecto a «ahora». */
    public enum TimeState { PAST, NOW, NEXT, LATER }

    /** Una línea de «Hoy»: lo que la trabajadora necesita ver de un vistazo. */
    public record TodayAppointment(Long id, LocalDateTime start, LocalDateTime end, Long clientId, String clientName,
                                   boolean clientHasCard, String treatmentName, Long workerId, String workerName,
                                   AppointmentStatus status, int clientNoShows, TimeState timeState,
                                   String clientPhone, String clientAllergies) {

        public boolean isPast() {
            return timeState == TimeState.PAST;
        }

        public boolean isNow() {
            return timeState == TimeState.NOW;
        }

        public boolean isNext() {
            return timeState == TimeState.NEXT;
        }

        public String statusLabel() {
            return switch (status) {
                case PENDING -> "Solicitud";
                case CONFIRMED -> timeState == TimeState.PAST ? "Completada" : "Confirmada";
                case COMPLETED -> "Completada";
                case NO_SHOW -> "No vino";
                default -> "";
            };
        }

        /** Clase del badge, la misma que en la agenda; una confirmada que ya terminó se ve como completada. */
        public String statusCss() {
            if (status == AppointmentStatus.CONFIRMED && timeState == TimeState.PAST) {
                return "badge-completed";
            }
            return "badge-" + status.name().toLowerCase();
        }
    }
}
