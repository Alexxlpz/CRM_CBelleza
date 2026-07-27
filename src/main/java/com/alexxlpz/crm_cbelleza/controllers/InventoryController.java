package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
public class InventoryController {

    private final InventoryService inventoryService;
    private final TreatmentService treatmentService;
    private final NotificationService notificationService;
    private final AppointmentService appointmentService;

    public InventoryController(InventoryService inventoryService,
                               TreatmentService treatmentService,
                               NotificationService notificationService,
                               AppointmentService appointmentService) {
        this.inventoryService = inventoryService;
        this.treatmentService = treatmentService;
        this.notificationService = notificationService;
        this.appointmentService = appointmentService;
    }

    // Worker Dashboard
    @GetMapping("/worker/dashboard")
    public String dashboard(Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        List<Appointment> allAppointments = appointmentService.getAppointmentsByCenter(centerId);
        List<Inventory> allInventory = inventoryService.getInventoryByCenter(centerId);

        int todayAppointments = 0;
        int tomorrowAppointments = 0;
        int pendingAppointments = 0;
        int lowStockCount = 0;

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        
        for (Appointment app : allAppointments) {
            if (app.getStatus() == AppointmentStatus.PENDING) pendingAppointments++;
            LocalDate appDate = app.getDateTime().toLocalDate();
            if (appDate.isEqual(today) && app.getStatus() == AppointmentStatus.CONFIRMED) todayAppointments++;
            if (appDate.isEqual(tomorrow) && app.getStatus() == AppointmentStatus.CONFIRMED) tomorrowAppointments++;
        }

        for (Inventory inv : allInventory) {
            if (inv.getStock() <= 3) lowStockCount++;
        }

        model.addAttribute("todayAppointmentsCount", todayAppointments);
        model.addAttribute("tomorrowAppointmentsCount", tomorrowAppointments);
        model.addAttribute("pendingAppointmentsCount", pendingAppointments);
        model.addAttribute("lowStockCount", lowStockCount);

        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/dashboard";
    }

    // Worker Inventory List
    @GetMapping("/worker/inventory")
    public String viewInventory(Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        List<Inventory> inventoryItems = inventoryService.getInventoryByCenter(centerId);
        List<Product> availableCatalogProducts = inventoryService.getAvailableCatalogProducts(centerId);

        model.addAttribute("inventoryItems", inventoryItems);
        model.addAttribute("catalogProducts", availableCatalogProducts);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/inventory";
    }

    // Action: Adjust stock
    @PostMapping("/worker/inventory/{id}/adjust-stock")
    public String adjustStock(@PathVariable("id") Long id,
                              @RequestParam("change") Integer change,
                              HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (!"WORKER".equals(role)) {
            return "redirect:/home";
        }

        inventoryService.adjustStock(id, change);
        return "redirect:/worker/inventory";
    }

    // Action: Add product to inventory
    @PostMapping("/worker/inventory/add")
    public String addProduct(@RequestParam("mode") String mode,
                             @RequestParam(value = "productId", required = false) Long productId,
                             @RequestParam(value = "name", required = false) String name,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam(value = "category", required = false) String category,
                             @RequestParam(value = "price", required = false) Double price,
                             @RequestParam("stock") Integer stock,
                             HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        if ("existing".equals(mode) && productId != null) {
            inventoryService.addExistingProductToInventory(centerId, productId, stock);
        } else if ("new".equals(mode) && name != null && !name.trim().isEmpty() && price != null) {
            inventoryService.addNewProductToInventory(centerId, name, description, category, price, stock);
        }

        return "redirect:/worker/inventory";
    }

    // Action: Remove product
    @PostMapping("/worker/inventory/{id}/delete")
    public String deleteFromInventory(@PathVariable("id") Long id, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        if (!"WORKER".equals(role)) {
            return "redirect:/home";
        }

        inventoryService.deleteFromInventory(id);
        return "redirect:/worker/inventory";
    }

    // Worker View: Manage Treatments
    @GetMapping("/worker/treatments")
    public String viewTreatments(Model model, HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        List<Treatment> treatments = treatmentService.getTreatmentsByCenter(centerId);

        model.addAttribute("treatments", treatments);
        model.addAttribute("sessionRole", role);
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/treatments";
    }

    // Action: Add treatment
    @PostMapping("/worker/treatments/add")
    public String addTreatment(@RequestParam("name") String name,
                               @RequestParam("description") String description,
                               @RequestParam("price") Double price,
                               @RequestParam("duration") Integer duration,
                               @RequestParam("type") String type,
                               HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        if (!"WORKER".equals(role) || centerId == null) {
            return "redirect:/home";
        }

        treatmentService.addTreatment(centerId, name, description, price, duration, type);
        return "redirect:/worker/treatments";
    }
}
