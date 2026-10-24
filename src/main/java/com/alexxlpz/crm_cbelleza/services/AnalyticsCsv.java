package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.services.AnalyticsService.AnalyticsReport;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.Kpi;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.MonthPoint;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.TeamRow;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * La pantalla «Análisis» en CSV para abrirla con Excel o Google Sheets en español: punto y coma entre columnas,
 * coma decimal y marca UTF-8 al principio (sin ella Excel rompe las tildes). Tres bloques: cifras del periodo,
 * mes a mes y rendimiento por profesional.
 */
public final class AnalyticsCsv {

    private static final String BOM = "﻿";
    private static final String LINE = "\r\n";

    private AnalyticsCsv() {
    }

    public static String of(String centerName, AnalyticsReport report) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, "Análisis de " + centerName, report.periodLabel());
        csv.append(LINE);

        row(csv, "Cifras: " + report.headline(), report.comparisonLabel() != null ? report.comparisonLabel() : "", "Cambio");
        for (Kpi kpi : report.kpis()) {
            row(csv, kpi.label(), number(kpi.value(), kpi.format() == AnalyticsService.Format.MONEY ? 2 : 0),
                    report.comparisonLabel() != null ? number(kpi.previous(), kpi.format() == AnalyticsService.Format.MONEY ? 2 : 0) : "",
                    kpi.changeLabel() != null ? kpi.changeLabel() : "");
        }
        csv.append(LINE);

        row(csv, "Mes", "Ingresos (€)", "Tratamientos (€)", "Productos (€)", "Visitas",
                "Visitas de clientes nuevos", "Visitas de clientes que repiten");
        for (MonthPoint month : report.monthly()) {
            if (!month.hasData()) {
                continue;
            }
            row(csv, month.label(), number(month.revenue(), 2), number(month.treatmentRevenue(), 2),
                    number(month.productRevenue(), 2), String.valueOf(month.visits()),
                    String.valueOf(month.newVisits()), String.valueOf(month.returningVisits()));
        }
        csv.append(LINE);

        row(csv, "Profesional", "Visitas", "Clientes", "Ingresos (€)", "Ticket medio (€)", "Ocupación (%)",
                "No vinieron", "Nota media", "Reseñas");
        for (TeamRow member : report.team()) {
            row(csv, member.name(), String.valueOf(member.visits()), String.valueOf(member.clients()),
                    number(member.revenue(), 2), number(member.averageTicket(), 2),
                    member.occupancy() == null ? "" : String.valueOf(member.occupancy()),
                    String.valueOf(member.noShows()),
                    member.rating() == null ? "" : number(member.rating(), 1),
                    String.valueOf(member.reviewCount()));
        }
        return csv.toString();
    }

    /** Nombre del fichero: «analisis-centro-historico-ultimos-12-meses.csv». */
    public static String fileName(String centerName, AnalyticsReport report) {
        return "analisis-" + slug(centerName) + "-" + slug(report.periodLabel()) + ".csv";
    }

    static String slug(String text) {
        String plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static void row(StringBuilder csv, String... cells) {
        csv.append(Stream.of(cells).map(AnalyticsCsv::cell).collect(Collectors.joining(";"))).append(LINE);
    }

    /** Entre comillas si lleva separador, comillas o saltos; y sin dejar que Excel lo lea como fórmula. */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !text.matches("[-+]?[0-9.,]+( ?%| pts)?")) {
            text = "'" + text;
        }
        return text.contains(";") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? "\"" + text.replace("\"", "\"\"") + "\""
                : text;
    }

    private static String number(double value, int decimals) {
        return String.format(Locale.forLanguageTag("es-ES"), "%." + decimals + "f", value);
    }
}
