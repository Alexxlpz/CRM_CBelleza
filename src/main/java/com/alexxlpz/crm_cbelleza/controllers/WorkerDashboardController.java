package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.AnalyticsReport;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.DashboardStats;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Panel principal del trabajador y su pantalla de análisis. */
@Controller
public class WorkerDashboardController {

    private final DashboardService dashboardService;
    private final CenterService centerService;
    private final AnalyticsService analyticsService;

    public WorkerDashboardController(DashboardService dashboardService, CenterService centerService,
                                     AnalyticsService analyticsService) {
        this.dashboardService = dashboardService;
        this.centerService = centerService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/worker/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        DashboardStats stats = dashboardService.statsFor(worker.getCenterId());
        model.addAttribute("todayAppointmentsCount", stats.todayAppointments());
        model.addAttribute("tomorrowAppointmentsCount", stats.tomorrowAppointments());
        model.addAttribute("pendingAppointmentsCount", stats.pendingAppointments());
        model.addAttribute("lowStockCount", stats.lowStockCount());
        model.addAttribute("centerPhotoUrl", centerService.getCenterById(worker.getCenterId()).getPhotoUrl());
        // Resumen del mes en el panel: las tres primeras cifras del análisis (ingresos, visitas y clientes nuevos)
        AnalyticsReport report = analyticsService.reportFor(worker.getCenterId(), 3);
        model.addAttribute("monthReport", report);
        model.addAttribute("monthKpis", report.kpis().subList(0, 3));
        return "worker/dashboard";
    }

    @GetMapping("/worker/analytics")
    public String analytics(@AuthenticationPrincipal AppUserDetails worker,
                            @RequestParam(name = "meses", required = false) Integer months,
                            @RequestParam(name = "anio", required = false) String year, Model model) {
        // ?anio=2025 abre la vista por año (vacío = el actual); si no, los últimos meses
        AnalyticsReport report = year != null
                ? analyticsService.yearReportFor(worker.getCenterId(), parseYear(year))
                : analyticsService.reportFor(worker.getCenterId(), AnalyticsService.normalizeMonths(months));
        model.addAttribute("report", report);
        model.addAttribute("periodOptions", AnalyticsService.PERIOD_OPTIONS);
        model.addAttribute("pageDescription", "Ingresos, visitas, clientes nuevos y clientes fieles de tu centro, mes a mes.");
        return "worker/analytics";
    }

    private static Integer parseYear(String year) {
        try {
            return year.isBlank() ? null : Integer.valueOf(year.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
