package com.alexxlpz.crm_cbelleza.booking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Horario en el que un centro admite citas: uno o dos tramos por día de la semana (jornada partida)
 * y franjas de {@code slotMinutes}. Un día sin tramos está cerrado. Es inmutable; lo carga
 * {@code OpeningHoursService} para cada centro.
 */
public final class OpeningHours {

    /** Tramo de apertura de un día, p. ej. 09:00-14:00. */
    public record Period(DayOfWeek day, LocalTime opens, LocalTime closes) {

        public int opensMinute() {
            return opens.getHour() * 60 + opens.getMinute();
        }

        public int closesMinute() {
            return closes.getHour() * 60 + closes.getMinute();
        }

        public String label() {
            return "%s - %s".formatted(opens, closes);
        }
    }

    private final int slotMinutes;
    private final Map<DayOfWeek, List<Period>> byDay;

    public OpeningHours(int slotMinutes, List<Period> periods) {
        this.slotMinutes = slotMinutes;
        Map<DayOfWeek, List<Period>> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            days.put(day, periods.stream()
                    .filter(period -> period.day() == day)
                    .sorted(Comparator.comparing(Period::opens))
                    .toList());
        }
        this.byDay = Collections.unmodifiableMap(days);
    }

    /** El mismo tramo todos los días indicados (el horario por defecto y el de los tests). */
    public static OpeningHours uniform(int slotMinutes, Set<DayOfWeek> days, LocalTime opens, LocalTime closes) {
        return new OpeningHours(slotMinutes, days.stream().sorted().map(day -> new Period(day, opens, closes)).toList());
    }

    public int slotMinutes() {
        return slotMinutes;
    }

    public List<Period> periods(DayOfWeek day) {
        return byDay.get(day);
    }

    public List<Period> periods() {
        return byDay.values().stream().flatMap(List::stream).toList();
    }

    public boolean isOpen(DayOfWeek day) {
        return !byDay.get(day).isEmpty();
    }

    public Set<DayOfWeek> workingDays() {
        Set<DayOfWeek> days = new TreeSet<>();
        byDay.forEach((day, periods) -> {
            if (!periods.isEmpty()) days.add(day);
        });
        return days;
    }

    /** Hora en punto a la que abre antes en toda la semana (inicio de la rejilla de la agenda). */
    public int earliestHour() {
        return periods().stream().mapToInt(Period::opensMinute).min().orElse(9 * 60) / 60;
    }

    /** Hora en punto a la que cierra más tarde en toda la semana (final de la rejilla de la agenda). */
    public int latestHour() {
        int minute = periods().stream().mapToInt(Period::closesMinute).max().orElse(20 * 60);
        return (minute + 59) / 60;
    }

    /** true si una cita que empieza en {@code start} y dura {@code durationMinutes} cabe entera en un tramo. */
    public boolean fits(LocalDateTime start, int durationMinutes) {
        if (start.getSecond() != 0 || start.getNano() != 0 || start.getMinute() % slotMinutes != 0) {
            return false;
        }
        int from = start.getHour() * 60 + start.getMinute();
        int to = from + durationMinutes;
        return byDay.get(start.getDayOfWeek()).stream()
                .anyMatch(period -> from >= period.opensMinute() && to <= period.closesMinute());
    }

    /**
     * true si [start, end) cae entero dentro de un tramo, sin exigir que empiece en una franja
     * (el horario de un profesional frente a citas puestas a mano a cualquier hora).
     */
    public boolean covers(LocalDateTime start, LocalDateTime end) {
        return start.toLocalDate().equals(end.toLocalDate())
                && covers(start.getDayOfWeek(), start.toLocalTime(), end.toLocalTime());
    }

    /** true si el tramo [opens, closes) del día cabe entero dentro de alguno de los tramos de ese día. */
    public boolean covers(DayOfWeek day, java.time.LocalTime opens, java.time.LocalTime closes) {
        return byDay.get(day).stream()
                .anyMatch(period -> !opens.isBefore(period.opens()) && !closes.isAfter(period.closes()));
    }

    /** Días por delante en los que se busca la próxima apertura de un centro cerrado. */
    static final int LOOKAHEAD_DAYS = 14;

    /**
     * Si el centro está abierto en {@code now} y cuándo cambia: la hora de cierre si está abierto o la próxima
     * apertura si está cerrado (nula si no abre en las dos próximas semanas). Los cierres de todo el centro
     * (festivos, vacaciones) restan de los tramos de apertura.
     */
    public OpenState stateAt(LocalDateTime now, List<Closure> closures) {
        LocalDate today = now.toLocalDate();
        // Trozos abiertos en orden; los que se tocan (tarde que empieza al acabar la mañana) cuentan como uno
        List<Closure> open = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(today.plusDays(LOOKAHEAD_DAYS)); day = day.plusDays(1)) {
            for (Period period : byDay.get(day.getDayOfWeek())) {
                for (Closure piece : subtract(new Closure(day.atTime(period.opens()), day.atTime(period.closes())), closures)) {
                    if (!open.isEmpty() && open.getLast().endsAt().equals(piece.startsAt())) {
                        open.set(open.size() - 1, new Closure(open.getLast().startsAt(), piece.endsAt()));
                    } else {
                        open.add(piece);
                    }
                }
            }
        }
        for (Closure piece : open) {
            if (piece.endsAt().isAfter(now)) {
                return piece.startsAt().isAfter(now)
                        ? new OpenState(false, piece.startsAt())
                        : new OpenState(true, piece.endsAt());
            }
        }
        return new OpenState(false, null);
    }

    /** Lo que queda de un tramo tras quitarle los cierres que lo pisan, en orden. */
    private static List<Closure> subtract(Closure period, List<Closure> closures) {
        List<Closure> left = new ArrayList<>(List.of(period));
        for (Closure closure : closures) {
            List<Closure> next = new ArrayList<>();
            for (Closure piece : left) {
                if (!closure.startsAt().isBefore(piece.endsAt()) || !closure.endsAt().isAfter(piece.startsAt())) {
                    next.add(piece);
                    continue;
                }
                if (closure.startsAt().isAfter(piece.startsAt())) {
                    next.add(new Closure(piece.startsAt(), closure.startsAt()));
                }
                if (closure.endsAt().isBefore(piece.endsAt())) {
                    next.add(new Closure(closure.endsAt(), piece.endsAt()));
                }
            }
            left = next;
        }
        left.sort(Comparator.comparing(Closure::startsAt));
        return left;
    }

    /** Intervalo de tiempo: un cierre de todo el centro o un trozo de tramo abierto. */
    public record Closure(LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    /** Abierto o cerrado en un momento y cuándo cambia (cierre o próxima apertura). */
    public record OpenState(boolean open, LocalDateTime changesAt) {
    }

    /**
     * Texto para el cliente, una línea por grupo de días seguidos con el mismo horario,
     * p. ej. "Lunes a viernes: 09:00 - 14:00 y 16:00 - 20:00".
     */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        DayOfWeek groupStart = null;
        DayOfWeek previous = null;
        String previousHours = null;
        for (DayOfWeek day : DayOfWeek.values()) {
            String hours = hoursOf(day);
            if (hours != null && hours.equals(previousHours)) {
                previous = day;
                continue;
            }
            if (previousHours != null) {
                lines.add(line(groupStart, previous, previousHours));
            }
            groupStart = day;
            previous = day;
            previousHours = hours;
        }
        if (previousHours != null) {
            lines.add(line(groupStart, previous, previousHours));
        }
        return lines.isEmpty() ? List.of("Sin horario de reservas") : lines;
    }

    /** Horas de un día en texto ("09:00 - 14:00 y 16:00 - 20:00") o null si cierra. */
    public String hoursOf(DayOfWeek day) {
        List<Period> periods = byDay.get(day);
        return periods.isEmpty() ? null : String.join(" y ", periods.stream().map(Period::label).toList());
    }

    /**
     * Datos para los calendarios en JavaScript: franja, días abiertos, horas en punto de apertura y cierre
     * de la semana y, por día (1 = lunes), sus tramos en minutos desde medianoche.
     */
    public Map<String, Object> toJson() {
        Map<String, List<int[]>> days = new LinkedHashMap<>();
        byDay.forEach((day, periods) -> days.put(String.valueOf(day.getValue()),
                periods.stream().map(p -> new int[]{p.opensMinute(), p.closesMinute()}).toList()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("slotMinutes", slotMinutes);
        json.put("workingDays", workingDays().stream().map(DayOfWeek::getValue).toList());
        json.put("openHour", earliestHour());
        json.put("closeHour", latestHour());
        json.put("days", days);
        return json;
    }

    public static String dayName(DayOfWeek day) {
        String name = day.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String line(DayOfWeek first, DayOfWeek last, String hours) {
        String days = first == last ? dayName(first)
                : first.plus(1) == last ? dayName(first) + " y " + dayName(last).toLowerCase(Locale.ROOT)
                : dayName(first) + " a " + dayName(last).toLowerCase(Locale.ROOT);
        return days + ": " + hours;
    }
}
