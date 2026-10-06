package com.alexxlpz.crm_cbelleza.booking;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Horario en el que se pueden reservar citas.
 * Es una interfaz para poder sustituir la implementación (por ejemplo, un horario por centro)
 * sin tocar el servicio de reservas.
 */
public interface OpeningHoursPolicy {

    int slotMinutes();

    int openHour();

    int closeHour();

    Set<DayOfWeek> workingDays();

    /** true si una cita que empieza en {@code start} y dura {@code durationMinutes} cabe entera en el horario. */
    default boolean fits(LocalDateTime start, int durationMinutes) {
        int startMinutes = start.getHour() * 60 + start.getMinute();
        return workingDays().contains(start.getDayOfWeek())
                && start.getSecond() == 0
                && start.getNano() == 0
                && start.getMinute() % slotMinutes() == 0
                && startMinutes >= openHour() * 60
                && startMinutes + durationMinutes <= closeHour() * 60;
    }

    /** Texto para mostrar al cliente, p. ej. "Lunes a Viernes: 09:00 - 20:00". */
    default String describe() {
        List<DayOfWeek> days = workingDays().stream().sorted().toList();
        if (days.isEmpty()) {
            return "Sin horario de reservas";
        }
        String first = dayName(days.getFirst());
        String last = dayName(days.getLast());
        boolean contiguous = days.getLast().getValue() - days.getFirst().getValue() == days.size() - 1;
        String dayPart = days.size() == 1 ? first
                : contiguous ? first + " a " + last
                : String.join(", ", days.stream().map(OpeningHoursPolicy::dayName).toList());
        return "%s: %02d:00 - %02d:00".formatted(dayPart, openHour(), closeHour());
    }

    private static String dayName(DayOfWeek day) {
        String name = day.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
