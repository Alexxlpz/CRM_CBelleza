package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.BookingProperties;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours.Period;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.CenterOpeningPeriod;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.OpeningHoursForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterOpeningPeriodRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Horario de apertura de cada centro: lo eligen en el alta y lo cambia el dueño en «Mi Centro».
 * Con él se calculan los huecos que ve el cliente, se validan las reservas y se pinta la agenda.
 */
@Service
@Transactional
public class OpeningHoursService {

    /** Tramos por día: mañana y tarde (jornada partida). */
    public static final int MAX_PERIODS_PER_DAY = 2;

    /** Horas que ofrece el editor: de 06:00 a 23:30. */
    private static final int FIRST_OPTION_MINUTE = 6 * 60;

    private final CenterOpeningPeriodRepository periods;
    private final CenterRepository centers;
    private final BookingProperties properties;

    public OpeningHoursService(CenterOpeningPeriodRepository periods, CenterRepository centers, BookingProperties properties) {
        this.periods = periods;
        this.centers = centers;
        this.properties = properties;
    }

    /** Horario del centro; uno que todavía no tiene ninguno usa el de por defecto. */
    @Transactional(readOnly = true)
    public OpeningHours forCenter(Long centerId) {
        List<CenterOpeningPeriod> saved = periods.findByCenterIdOrderByDayOfWeekAscOpensAtAsc(centerId);
        if (saved.isEmpty()) {
            return properties.defaultHours();
        }
        return new OpeningHours(properties.slotMinutes(), saved.stream()
                .map(p -> new Period(DayOfWeek.of(p.getDayOfWeek()), p.getOpensAt(), p.getClosesAt()))
                .toList());
    }

    /** Horario de cada centro indicado, en una sola consulta; los que no tienen ninguno usan el de por defecto. */
    @Transactional(readOnly = true)
    public Map<Long, OpeningHours> forCenters(Collection<Long> centerIds) {
        if (centerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Period>> saved = periods.findByCenterIdIn(centerIds).stream()
                .collect(Collectors.groupingBy(p -> p.getCenter().getId(), Collectors.mapping(
                        p -> new Period(DayOfWeek.of(p.getDayOfWeek()), p.getOpensAt(), p.getClosesAt()), Collectors.toList())));
        Map<Long, OpeningHours> hours = new HashMap<>();
        for (Long centerId : centerIds) {
            List<Period> centerPeriods = saved.get(centerId);
            hours.put(centerId, centerPeriods == null ? properties.defaultHours()
                    : new OpeningHours(properties.slotMinutes(), centerPeriods));
        }
        return hours;
    }

    @Transactional(readOnly = true)
    public OpeningHours defaults() {
        return properties.defaultHours();
    }

    /** Sustituye el horario del centro por el del formulario (validado). */
    public OpeningHours replace(Long centerId, OpeningHoursForm form) {
        OpeningHours hours = parse(form);
        save(centerId, hours);
        return hours;
    }

    public void save(Long centerId, OpeningHours hours) {
        Center center = centers.getReferenceById(centerId);
        periods.deleteByCenterId(centerId);
        periods.flush();
        periods.saveAll(hours.periods().stream()
                .map(p -> CenterOpeningPeriod.builder()
                        .center(center)
                        .dayOfWeek(p.day().getValue())
                        .opensAt(p.opens())
                        .closesAt(p.closes())
                        .build())
                .toList());
    }

    /** Comprueba el horario del formulario: horas de media en media hora, tramos que no se pisan y algún día abierto. */
    public OpeningHours parse(OpeningHoursForm form) {
        if (form == null || form.isEmpty()) {
            throw new BusinessRuleException("Marca al menos un día en el que abra el centro.");
        }
        List<Integer> days = form.hoursDay();
        List<String> froms = form.hoursFrom() == null ? List.of() : form.hoursFrom();
        List<String> tos = form.hoursTo() == null ? List.of() : form.hoursTo();
        if (froms.size() != days.size() || tos.size() != days.size()) {
            throw new BusinessRuleException("Revisa el horario: a algún día le falta la hora de apertura o la de cierre.");
        }
        int slot = properties.slotMinutes();
        List<Period> result = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            Integer dayValue = days.get(i);
            if (dayValue == null || dayValue < 1 || dayValue > 7) {
                throw new BusinessRuleException("Revisa el horario: hay un día de la semana que no existe.");
            }
            DayOfWeek day = DayOfWeek.of(dayValue);
            LocalTime opens = time(froms.get(i), slot);
            LocalTime closes = time(tos.get(i), slot);
            if (!closes.isAfter(opens)) {
                throw new BusinessRuleException("El " + dayLower(day) + " la hora de cierre tiene que ser posterior a la de apertura.");
            }
            result.add(new Period(day, opens, closes));
        }
        OpeningHours hours = new OpeningHours(slot, result);
        for (DayOfWeek day : DayOfWeek.values()) {
            List<Period> ofDay = hours.periods(day);
            if (ofDay.size() > MAX_PERIODS_PER_DAY) {
                throw new BusinessRuleException("Cada día puede tener como mucho dos tramos: mañana y tarde.");
            }
            for (int i = 1; i < ofDay.size(); i++) {
                if (ofDay.get(i).opens().isBefore(ofDay.get(i - 1).closes())) {
                    throw new BusinessRuleException("Los tramos del " + dayLower(day) + " se pisan: la tarde tiene que empezar cuando acaba la mañana o después.");
                }
            }
        }
        return hours;
    }

    /** Horas del desplegable del editor, de media en media hora. */
    public List<String> timeOptions() {
        List<String> options = new ArrayList<>();
        for (int minute = FIRST_OPTION_MINUTE; minute < 24 * 60; minute += properties.slotMinutes()) {
            options.add(LocalTime.of(minute / 60, minute % 60).toString());
        }
        return options;
    }

    /** Filas del editor, una por día, con lo guardado (o lo escrito, si el formulario tenía un error). */
    public List<DayRow> rows(OpeningHours hours) {
        List<DayRow> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            List<Period> ofDay = hours.periods(day);
            Period first = ofDay.isEmpty() ? null : ofDay.getFirst();
            Period second = ofDay.size() > 1 ? ofDay.get(1) : null;
            rows.add(new DayRow(day.getValue(), OpeningHours.dayName(day), first != null,
                    first != null ? first.opens().toString() : "09:00",
                    first != null ? first.closes().toString() : (second == null ? "20:00" : "14:00"),
                    second != null,
                    second != null ? second.opens().toString() : "16:00",
                    second != null ? second.closes().toString() : "20:00"));
        }
        return rows;
    }

    /** Filas del editor a partir de lo que llegó en un formulario con errores (sin volver a validarlo). */
    public List<DayRow> rows(OpeningHoursForm form) {
        if (form == null || form.isEmpty()) {
            return rows(defaults());
        }
        List<Period> raw = new ArrayList<>();
        List<String> froms = form.hoursFrom() == null ? List.of() : form.hoursFrom();
        List<String> tos = form.hoursTo() == null ? List.of() : form.hoursTo();
        for (int i = 0; i < form.hoursDay().size() && i < froms.size() && i < tos.size(); i++) {
            try {
                raw.add(new Period(DayOfWeek.of(form.hoursDay().get(i)), LocalTime.parse(froms.get(i)), LocalTime.parse(tos.get(i))));
            } catch (RuntimeException ignored) {
                // Un tramo ilegible no se vuelve a pintar
            }
        }
        return rows(new OpeningHours(properties.slotMinutes(), raw));
    }

    private static LocalTime time(String value, int slot) {
        if (FormText.isBlank(value)) {
            throw new BusinessRuleException("Revisa el horario: a algún día le falta la hora de apertura o la de cierre.");
        }
        try {
            LocalTime time = LocalTime.parse(value.trim());
            if (time.getSecond() != 0 || time.getMinute() % slot != 0) {
                throw new BusinessRuleException("Las horas del horario van de " + slot + " en " + slot + " minutos (por ejemplo, 09:00 o 09:30).");
            }
            return time;
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("Revisa el horario: «" + value + "» no es una hora.");
        }
    }

    private static String dayLower(DayOfWeek day) {
        return OpeningHours.dayName(day).toLowerCase(Locale.ROOT);
    }

    /** Un día en el editor: abierto o no, el primer tramo y, si es jornada partida, el segundo. */
    public record DayRow(int day, String name, boolean open, String from1, String to1,
                         boolean split, String from2, String to2) {
    }
}
