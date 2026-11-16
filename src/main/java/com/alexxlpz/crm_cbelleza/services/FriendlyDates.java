package com.alexxlpz.crm_cbelleza.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Fechas de citas dichas como se dicen en voz alta: «Hoy · 11:00», «Mañana, lun 16 nov · 09:00» o «vie 20 nov · 10:30»
 * (con el año si no es el de hoy). {@code today} es el día de hoy en la hora local del centro.
 */
public final class FriendlyDates {

    private static final String[] DAYS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final String[] MONTHS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private FriendlyDates() {
    }

    /** «Hoy», «Mañana», «Ayer» o «vie 20 nov» (y el año si no es el de hoy). */
    public static String day(LocalDate date, LocalDate today) {
        if (date.isEqual(today)) return "Hoy";
        if (date.isEqual(today.plusDays(1))) return "Mañana";
        if (date.isEqual(today.minusDays(1))) return "Ayer";
        return shortDate(date, today);
    }

    /** «Hoy · 11:00», «Mañana, lun 16 nov · 09:00» o «vie 20 nov · 10:30». */
    public static String dateTime(LocalDateTime dateTime, LocalDate today) {
        LocalDate date = dateTime.toLocalDate();
        String time = TIME.format(dateTime);
        if (date.isEqual(today)) return "Hoy · " + time;
        if (date.isEqual(today.plusDays(1))) return "Mañana, " + shortDate(date, today) + " · " + time;
        return shortDate(date, today) + " · " + time;
    }

    private static String shortDate(LocalDate date, LocalDate today) {
        String text = DAYS[date.getDayOfWeek().getValue() - 1] + " " + date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
        return date.getYear() == today.getYear() ? text : text + " " + date.getYear();
    }
}
