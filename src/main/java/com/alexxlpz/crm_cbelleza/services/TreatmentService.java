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
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Carta de tratamientos de cada centro. */
@Service
@Transactional
public class TreatmentService {

    static final double MAX_PRICE = 10_000;
    static final int MIN_DURATION = 5;
    static final int MAX_DURATION = 720;
    static final int MAX_NAME = 120;
    static final int MAX_DESCRIPTION = 1000;

    private final TreatmentRepository treatmentRepository;
    private final CenterRepository centerRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public TreatmentService(TreatmentRepository treatmentRepository, CenterRepository centerRepository,
                            UserRepository userRepository, AppointmentRepository appointmentRepository, Clock clock) {
        this.treatmentRepository = treatmentRepository;
        this.centerRepository = centerRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
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

    /** Los de la carta, sin los ocultos: lo que ve el cliente y lo que se puede elegir al dar cita. */
    @Transactional(readOnly = true)
    public List<Treatment> getVisibleTreatmentsByCenter(Long centerId) {
        return treatmentRepository.findByCenterId(centerId).stream().filter(t -> !t.isHidden()).toList();
    }

    @Transactional(readOnly = true)
    public long countByCenter(Long centerId) {
        return treatmentRepository.countByCenterId(centerId);
    }

    /** Citas de cada tratamiento del centro (de cualquier estado). Un tratamiento sin citas no está en el mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Long> appointmentCounts(Long centerId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : appointmentRepository.countByTreatmentOfCenter(centerId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public void addTreatment(Long centerId, TreatmentForm form) {
        validate(form);
        Center center = centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
        treatmentRepository.save(Treatment.builder()
                .name(form.name().trim())
                .description(FormText.trimToNull(form.description()))
                .price(roundPrice(form.price()))
                .duration(form.duration())
                .type(parseType(form.type()))
                .center(center)
                .workerIds(teamSelection(centerId, form.workerIds()))
                .build());
    }

    /**
     * Cambia nombre, tipo, descripción, precio y duración. Las citas ya pedidas no cambian: guardan su precio y su
     * duración. Quién lo hace y la oferta se cambian aparte; el precio de carta no puede bajar de la oferta.
     */
    public Treatment updateTreatment(Long centerId, Long treatmentId, TreatmentForm form) {
        Treatment treatment = find(centerId, treatmentId);
        validate(form);
        double price = roundPrice(form.price());
        if (treatment.hasOffer() && treatment.getOfferPrice() >= price) {
            throw new BusinessRuleException("El precio de carta tiene que ser mayor que el de la oferta ("
                    + String.format(Locale.forLanguageTag("es"), "%.2f", treatment.getOfferPrice())
                    + " €). Cambia o quita antes la oferta.");
        }
        treatment.setName(form.name().trim());
        treatment.setDescription(FormText.trimToNull(form.description()));
        treatment.setPrice(price);
        treatment.setDuration(form.duration());
        treatment.setType(parseType(form.type()));
        return treatment;
    }

    /** Oculta o vuelve a mostrar un tratamiento en la carta. Sus citas no cambian. */
    public Treatment setHidden(Long centerId, Long treatmentId, boolean hidden) {
        Treatment treatment = find(centerId, treatmentId);
        treatment.setHidden(hidden);
        return treatment;
    }

    /**
     * Borra un tratamiento que nunca ha tenido citas (con su oferta y quién lo hace). Si tiene alguna, de cualquier
     * estado, no se borra: desaparecería del historial de los clientes y del análisis. Entonces se oculta.
     */
    public String deleteTreatment(Long centerId, Long treatmentId) {
        Treatment treatment = find(centerId, treatmentId);
        long appointments = appointmentRepository.countByTreatmentId(treatmentId);
        if (appointments > 0) {
            throw new BusinessRuleException("«" + treatment.getName() + "» tiene " + appointments
                    + (appointments == 1 ? " cita" : " citas") + " y no se puede borrar sin quitarlas del historial "
                    + "de sus clientes y del análisis. Ocúltalo: deja de salir en la carta y lo puedes volver a mostrar.");
        }
        treatmentRepository.delete(treatment);
        return treatment.getName();
    }

    private Treatment find(Long centerId, Long treatmentId) {
        return treatmentRepository.findByIdAndCenterId(treatmentId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));
    }

    private static void validate(TreatmentForm form) {
        if (FormText.isBlank(form.name()) || form.price() == null || form.duration() == null || form.duration() <= 0) {
            throw new BusinessRuleException("Completa nombre, precio y duración del tratamiento.");
        }
        if (form.price() < 0 || form.price().isNaN() || form.price().isInfinite()) {
            throw new BusinessRuleException("El precio no puede ser negativo.");
        }
        if (form.price() > MAX_PRICE) {
            throw new BusinessRuleException("El precio no puede pasar de 10.000 €.");
        }
        if (form.duration() < MIN_DURATION || form.duration() > MAX_DURATION) {
            throw new BusinessRuleException("La duración tiene que estar entre 5 minutos y 12 horas.");
        }
        if (form.name().trim().length() > MAX_NAME) {
            throw new BusinessRuleException("El nombre puede tener como mucho " + MAX_NAME + " caracteres.");
        }
        if (form.description() != null && form.description().trim().length() > MAX_DESCRIPTION) {
            throw new BusinessRuleException("La descripción puede tener como mucho " + MAX_DESCRIPTION + " caracteres.");
        }
    }

    private static double roundPrice(Double price) {
        return Math.round(price * 100) / 100.0;
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
