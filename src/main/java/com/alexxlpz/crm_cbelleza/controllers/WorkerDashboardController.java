package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.DashboardStats;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Panel principal del trabajador. */
@Controller
public class WorkerDashboardController {

    private final DashboardService dashboardService;

    public WorkerDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/worker/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        DashboardStats stats = dashboardService.statsFor(worker.getCenterId());
        model.addAttribute("todayAppointmentsCount", stats.todayAppointments());
        model.addAttribute("tomorrowAppointmentsCount", stats.tomorrowAppointments());
        model.addAttribute("pendingAppointmentsCount", stats.pendingAppointments());
        model.addAttribute("lowStockCount", stats.lowStockCount());
        return "worker/dashboard";
    }
}
