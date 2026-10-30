package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.TreatmentForm;
import com.alexxlpz.crm_cbelleza.forms.TreatmentOfferForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Carta de tratamientos de cada centro. */
@Service
@Transactional
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TreatmentService(TreatmentRepository treatmentRepository, CenterRepository centerRepository,
                            UserRepository userRepository, Clock clock) {
        this.treatmentRepository = treatmentRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Hoy en la zona del centro: decide si una oferta sigue en vigor. */
    @Transactional(readOnly = true)
    public LocalDate todayAt(Long centerId) {
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        return CenterClock.now(clock, center).toLocalDate();
    }

    @Transactional(readOnly = true)
    public List<Treatment> getTreatmentsByCenter(Long centerId) {
        return treatmentRepository.findByCenterId(centerId);
    }

    @Transactional(readOnly = true)
    public long countByCenter(Long centerId) {
        return treatmentRepository.countByCenterId(centerId);
    }

    public void addTreatment(Long centerId, TreatmentForm form) {
        if (FormText.isBlank(form.name()) || form.price() == null || form.duration() == null || form.duration() <= 0) {
            throw new BusinessRuleException("Completa nombre, precio y duración del tratamiento.");
        }
        if (form.price() < 0 || form.price().isNaN() || form.price().isInfinite()) {
            throw new BusinessRuleException("El precio no puede ser negativo.");
        }
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        treatmentRepository.save(Treatment.builder()
                .name(form.name().trim())
                .description(form.description())
                .price(form.price())
                .duration(form.duration())
                .type(parseType(form.type()))
                .center(center)
                .workerIds(teamSelection(centerId, form.workerIds()))
                .build());
    }

    /** Quién hace el tratamiento. Sin nadie marcado, o con todo el equipo, lo hace todo el equipo. */
    public void setWorkers(Long centerId, Long treatmentId, List<Long> workerIds) {
        Treatment treatment = treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));
        treatment.getWorkerIds().clear();
        treatment.getWorkerIds().addAll(teamSelection(centerId, workerIds));
    }

    /** Los marcados que son del equipo; vacío si no hay ninguno o están todos (= todo el equipo, también los que lleguen). */
    private Set<Long> teamSelection(Long centerId, Collection<Long> workerIds) {
        if (workerIds == null || workerIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> team = userRepository.findByCenterIdAndRole(centerId, Role.WORKER).stream().map(User::getId).toList();
        Set<Long> selected = new HashSet<>(workerIds);
        if (!team.containsAll(selected)) {
            throw new ResourceNotFoundException("Ese profesional no es de tu centro.");
        }
        return selected.size() == team.size() ? new HashSet<>() : selected;
    }

    /** Pone o cambia la oferta de un tratamiento del centro. */
    public void saveOffer(Long centerId, Long treatmentId, TreatmentOfferForm form) {
        Treatment treatment = treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));
        Double offerPrice = form.offerPrice();
        if (offerPrice == null || offerPrice <= 0) {
            throw new BusinessRuleException("Escribe el precio de oferta.");
        }
        if (offerPrice >= treatment.getPrice()) {
            throw new BusinessRuleException("El precio de oferta tiene que ser menor que el de carta ("
                    + String.format(Locale.forLanguageTag("es"), "%.2f", treatment.getPrice()) + " €).");
        }
        LocalDate endsOn = parseDate(form.endsOn());
        if (endsOn != null && endsOn.isBefore(todayAt(centerId))) {
            throw new BusinessRuleException("La fecha de fin ya ha pasado. Elige hoy o un día posterior.");
        }
        treatment.setOfferPrice(Math.round(offerPrice * 100) / 100.0);
        treatment.setOfferEndsOn(endsOn);
    }

    public void removeOffer(Long centerId, Long treatmentId) {
        Treatment treatment = treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));
        treatment.setOfferPrice(null);
        treatment.setOfferEndsOn(null);
    }

    private static LocalDate parseDate(String value) {
        if (FormText.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("La fecha de fin no es válida.");
        }
    }

    private static TreatmentType parseType(String type) {
        try {
            return TreatmentType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return TreatmentType.OTRO;
        }
    }
}
