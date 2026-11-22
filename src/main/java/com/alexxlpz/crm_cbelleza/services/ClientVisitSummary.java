package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resumen de un cliente en un centro para la cabecera de su ficha: visitas, gasto total, última visita y el tratamiento
 * que más repite. Una visita es una cita completada y el gasto es lo cobrado en ellas más sus compras, igual que en
 * el análisis del centro ({@link AnalyticsService}).
 *
 * @param favoriteTreatment el tratamiento con más visitas si ha venido al menos dos veces a él; nulo si no
 */
public record ClientVisitSummary(int visits, double totalSpent, LocalDateTime lastVisit, long daysSinceLastVisit,
                                 String favoriteTreatment, int favoriteCount) {

    /**
     * @param appointments citas del cliente en el centro
     * @param now          «ahora» en la hora local del centro: decide qué citas confirmadas ya cuentan como visita y
     *                     cuántos días hace de la última (no se usa el reloj del sistema)
     */
    public static ClientVisitSummary of(List<Appointment> appointments, List<ProductSale> sales, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Appointment> visits = appointments.stream()
                .filter(a -> a.statusAt(now) == AppointmentStatus.COMPLETED)
                .toList();
        double spent = visits.stream().mapToDouble(a -> a.getPrice() != null ? a.getPrice() : 0).sum()
                + sales.stream().mapToDouble(ProductSale::getTotal).sum();
        LocalDateTime last = visits.stream().map(Appointment::getDateTime).max(Comparator.naturalOrder()).orElse(null);
        long days = last != null ? ChronoUnit.DAYS.between(last.toLocalDate(), today) : -1;

        // El más repetido; si empatan, el que se hizo más recientemente
        Map<String, List<Appointment>> byTreatment = visits.stream()
                .filter(a -> a.getTreatment() != null)
                .sorted(Comparator.comparing(Appointment::getDateTime).reversed())
                .collect(Collectors.groupingBy(a -> a.getTreatment().getName(), LinkedHashMap::new, Collectors.toList()));
        Map.Entry<String, List<Appointment>> favorite = byTreatment.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<String, List<Appointment>> e) -> e.getValue().size())
                        .thenComparing(e -> e.getValue().getFirst().getDateTime()))
                .orElse(null);
        boolean repeats = favorite != null && favorite.getValue().size() >= 2;
        return new ClientVisitSummary(visits.size(), spent, last, days,
                repeats ? favorite.getKey() : null, repeats ? favorite.getValue().size() : 0);
    }

    public boolean hasVisits() {
        return visits > 0;
    }

    /** «Hoy», «Ayer», «Hace 5 días», «Hace 3 semanas», «Hace 2 meses» o «Hace más de un año». */
    public String lastVisitLabel() {
        if (lastVisit == null) {
            return "";
        }
        if (daysSinceLastVisit <= 0) {
            return "Hoy";
        }
        if (daysSinceLastVisit == 1) {
            return "Ayer";
        }
        if (daysSinceLastVisit < 14) {
            return "Hace " + daysSinceLastVisit + " días";
        }
        if (daysSinceLastVisit < 60) {
            return "Hace " + daysSinceLastVisit / 7 + " semanas";
        }
        if (daysSinceLastVisit < 365) {
            return "Hace " + daysSinceLastVisit / 30 + " meses";
        }
        return "Hace más de un año";
    }
}
