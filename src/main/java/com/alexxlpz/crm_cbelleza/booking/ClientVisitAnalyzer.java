package com.alexxlpz.crm_cbelleza.booking;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detecta qué citas corresponden a la primera visita de un cliente al centro.
 * Trabaja en memoria sobre la lista de citas ya cargada (antes se lanzaba una consulta por cita).
 */
@Component
public class ClientVisitAnalyzer {

    private static final Set<AppointmentStatus> VISIT_STATUSES =
            Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);

    /** Devuelve, por id de cita, si el cliente no tenía ninguna visita anterior en el centro. */
    public Map<Long, Boolean> firstVisitFlags(Collection<Appointment> centerAppointments) {
        Map<String, LocalDateTime> firstVisitByClient = new HashMap<>();
        for (Appointment app : centerAppointments) {
            String key = clientKey(app);
            if (key != null && app.getDateTime() != null && VISIT_STATUSES.contains(app.getStatus())) {
                firstVisitByClient.merge(key, app.getDateTime(),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        }

        Map<Long, Boolean> result = new HashMap<>();
        for (Appointment app : centerAppointments) {
            String key = clientKey(app);
            LocalDateTime firstVisit = key == null ? null : firstVisitByClient.get(key);
            LocalDateTime when = app.getDateTime() != null ? app.getDateTime() : LocalDateTime.now();
            result.put(app.getId(), firstVisit == null || !firstVisit.isBefore(when));
        }
        return result;
    }


    private static String clientKey(Appointment app) {
        if (app.getClient() != null) {
            return "user_" + app.getClient().getId();
        }
        if (app.getGuestPhone() != null && !app.getGuestPhone().isBlank()) {
            return "guest_" + app.getGuestPhone().trim();
        }
        return null;
    }
}
