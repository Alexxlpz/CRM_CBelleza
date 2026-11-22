package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AnalyticsCsv;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService;
import com.alexxlpz.crm_cbelleza.services.AnalyticsService.AnalyticsReport;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.DashboardStats;
import com.alexxlpz.crm_cbelleza.services.DashboardService.TodayAppointment;
import com.alexxlpz.crm_cbelleza.services.ReviewService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/** Panel principal del trabajador y su pantalla de análisis. */
@Controller
public class WorkerDashboardController {

    private final DashboardService dashboardService;
    private final CenterService centerService;
    private final AnalyticsService analyticsService;
    private final ReviewService reviewService;
    private final UserService userService;
    private final Clock clock;

    public WorkerDashboardController(DashboardService dashboardService, CenterService centerService,
                                     AnalyticsService analyticsService, ReviewService reviewService,
                                     UserService userService, Clock clock) {
        this.reviewService = reviewService;
        this.userService = userService;
        this.clock = clock;
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
        Center center = centerService.getCenterById(worker.getCenterId());
        boolean owner = center.isOwnedBy(worker.getId());
        model.addAttribute("centerPhotoUrl", center.getPhotoUrl());
        model.addAttribute("rating", reviewService.rating(worker.getCenterId()));
        model.addAttribute("unansweredReviews", reviewService.countUnanswered(worker.getCenterId()));
        addToday(model, center, worker.getId(), owner);
        // Resumen del mes en el panel: las tres primeras cifras del análisis (ingresos, visitas y clientes nuevos).
        // Las cifras del negocio solo las ve el dueño del centro
        if (owner) {
            AnalyticsReport report = analyticsService.reportFor(worker.getCenterId(), 3);
            model.addAttribute("monthReport", report);
            model.addAttribute("monthKpis", report.kpis().subList(0, 3));
        }
        return "worker/dashboard";
    }

    /**
     * «Hoy» del panel: las citas del día con el filtro «Las mías» / «Todo el centro». La trabajadora empieza viendo las
     * suyas y el dueño todo el centro; con una sola persona en el equipo no hay filtro.
     */
    private void addToday(Model model, Center center, Long workerId, boolean owner) {
        List<TodayAppointment> today = dashboardService.todayFor(center);
        long mine = today.stream().filter(a -> workerId.equals(a.workerId())).count();
        boolean withFilter = userService.getWorkersByCenter(center.getId()).size() > 1;
        model.addAttribute("todayAppointments", today);
        model.addAttribute("todayMineCount", mine);
        model.addAttribute("todayFilter", withFilter);
        model.addAttribute("todayOnlyMine", withFilter && !owner);
        model.addAttribute("todayDate", CenterClock.now(clock, center).toLocalDate());
        model.addAttribute("currentWorkerId", workerId);
        model.addAttribute("pendingRequests", dashboardService.pendingFor(center));
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

    /** La misma vista de «Análisis» (?meses= o ?anio=) en CSV para Excel. */
    @GetMapping("/worker/analytics/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserDetails worker,
                                         @RequestParam(name = "meses", required = false) Integer months,
                                         @RequestParam(name = "anio", required = false) String year) {
        AnalyticsReport report = year != null
                ? analyticsService.yearReportFor(worker.getCenterId(), parseYear(year))
                : analyticsService.reportFor(worker.getCenterId(), AnalyticsService.normalizeMonths(months));
        String centerName = centerService.getCenterById(worker.getCenterId()).getName();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(AnalyticsCsv.fileName(centerName, report), StandardCharsets.UTF_8)
                        .build().toString())
                .body(AnalyticsCsv.of(centerName, report).getBytes(StandardCharsets.UTF_8));
    }

    private static Integer parseYear(String year) {
        try {
            return year.isBlank() ? null : Integer.valueOf(year.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
