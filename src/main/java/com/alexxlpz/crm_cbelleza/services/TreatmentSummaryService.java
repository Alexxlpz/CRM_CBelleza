package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resumen del tratamiento realizado en una cita: lo que el trabajador quiere recordar para la próxima vez
 * (productos usados, reacciones, qué no le ha sentado bien...). Si se marca como aviso, sale en la ficha
 * del cliente y en sus citas de la agenda. Es interno del centro: el cliente no lo ve.
 */
@Service
@Transactional
public class TreatmentSummaryService {

    private final AppointmentRepository appointmentRepository;
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TreatmentSummaryService(AppointmentRepository appointmentRepository,
                                   CenterRepository centerRepository,
                                   UserRepository userRepository,
                                   Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Guarda el resumen (texto vacío = lo borra) y, si llega, el precio cobrado. Solo en citas confirmadas que ya han
     * empezado: es cuando se sabe qué se ha hecho y cuánto se ha cobrado.
     */
    public void save(Long centerId, Long appointmentId, Long workerId, String summary, boolean alert, Double price) {
        Appointment appointment = appointmentRepository.findByIdAndCenterId(appointmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("La cita no existe en tu centro."));
        LocalDateTime now = CenterClock.now(clock, appointment.getCenter());
        if (!appointment.isSummaryEditableAt(now)) {
            throw new BusinessRuleException("El resumen se puede escribir cuando la cita ya ha empezado.");
        }
        String text = summary == null ? "" : summary.strip();
        if (text.length() > Appointment.TREATMENT_SUMMARY_MAX_LENGTH) {
            throw new BusinessRuleException("El resumen no puede pasar de " + Appointment.TREATMENT_SUMMARY_MAX_LENGTH + " caracteres.");
        }
        if (text.isEmpty() && alert) {
            throw new BusinessRuleException("Escribe el resumen para poder marcarlo como aviso.");
        }
        if (price != null) {
            if (price.isNaN() || price < 0 || price > Appointment.MAX_PRICE) {
                throw new BusinessRuleException("El precio cobrado tiene que estar entre 0 y 10.000 €.");
            }
            appointment.setPrice(Math.round(price * 100) / 100.0);
        }
        User author = userRepository.getReferenceById(workerId);
        appointment.setTreatmentSummary(text.isEmpty() ? null : text);
        appointment.setTreatmentAlert(alert);
        appointment.setSummaryUpdatedAt(text.isEmpty() ? null : now);
        appointment.setSummaryUpdatedBy(text.isEmpty() ? null : author);
    }

    /** Avisos del centro agrupados por cliente, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public Map<Long, List<Appointment>> alertsByClient(Long centerId) {
        return appointmentRepository.findByCenterIdAndTreatmentAlertTrueOrderByDateTimeDesc(centerId).stream()
                .filter(app -> app.getClient() != null)
                .collect(Collectors.groupingBy(app -> app.getClient().getId()));
    }

    /** «Ahora» en la hora del centro: decide en qué citas se puede escribir el resumen. */
    @Transactional(readOnly = true)
    public LocalDateTime centerNow(Long centerId) {
        return CenterClock.now(clock, centerRepository.findById(centerId).orElse(null));
    }
}
