package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.booking.OpeningHoursPolicy;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Zona del cliente: ficha del centro, solicitud de cita e historial de citas. Solo rol CLIENT. */
@Controller
public class ClientBookingController {

    private final CenterService centerService;
    private final TreatmentService treatmentService;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final BookingService bookingService;
    private final OpeningHoursPolicy openingHours;
    private final LoyaltyService loyaltyService;

    public ClientBookingController(CenterService centerService,
                                   TreatmentService treatmentService,
                                   UserService userService,
                                   AppointmentService appointmentService,
                                   BookingService bookingService,
                                   OpeningHoursPolicy openingHours,
                                   LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
        this.centerService = centerService;
        this.treatmentService = treatmentService;
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.bookingService = bookingService;
        this.openingHours = openingHours;
    }

    /** Ruta antigua: el listado de centros es ahora público en /centers. */
    @GetMapping("/client/centers")
    public String listCenters() {
        return "redirect:/centers";
    }

    @GetMapping("/client/centers/{id}")
    public String centerDetails(@PathVariable Long id, Model model) {
        model.addAttribute("center", centerService.getPublishedCenter(id));
        model.addAttribute("treatments", treatmentService.getTreatmentsByCenter(id));
        model.addAttribute("workers", userService.getWorkersByCenter(id));
        model.addAttribute("openingHoursLabel", openingHours.describe());
        model.addAttribute("activePage", "centers");
        return "client/center_details";
    }

    @PostMapping("/client/booking")
    public String book(@AuthenticationPrincipal AppUserDetails client,
                       @RequestParam Long centerId,
                       @RequestParam Long treatmentId,
                       @RequestParam("dateTime") String dateTime,
                       RedirectAttributes redirect) {
        try {
            bookingService.requestBooking(client.getId(), centerId, treatmentId, parse(dateTime));
            return "redirect:/client/appointments";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("bookingError", e.getMessage());
            return "redirect:/client/centers/" + centerId;
        }
    }

    @GetMapping("/client/appointments")
    public String appointments(@AuthenticationPrincipal AppUserDetails client, Model model) {
        model.addAttribute("appointments", appointmentService.getClientAppointments(client.getId()));
        model.addAttribute("loyaltyCards", loyaltyService.cardsOfClient(client.getId()));
        model.addAttribute("activePage", "appointments");
        return "client/appointments";
    }

    /** El cliente elige una de las horas que le propuso el centro. */
    @PostMapping("/client/appointments/{id}/accept-proposal")
    public String acceptProposal(@AuthenticationPrincipal AppUserDetails client,
                                 @PathVariable Long id,
                                 @RequestParam Long proposalId,
                                 RedirectAttributes redirect) {
        return clientAction(redirect, "¡Cita confirmada! Te esperamos a la hora que has elegido.",
                () -> bookingService.acceptProposal(client.getId(), id, proposalId));
    }

    @PostMapping("/client/appointments/{id}/decline-proposal")
    public String declineProposal(@AuthenticationPrincipal AppUserDetails client,
                                  @PathVariable Long id,
                                  RedirectAttributes redirect) {
        return clientAction(redirect, "Hemos avisado al centro de que ninguna hora te viene bien. Puedes pedir otra cita cuando quieras.",
                () -> bookingService.declineProposal(client.getId(), id));
    }

    @PostMapping("/client/appointments/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails client,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {
        return clientAction(redirect, "Cita anulada. Hemos avisado al centro.",
                () -> bookingService.cancelByClient(client.getId(), id));
    }

    private static String clientAction(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/client/appointments";
    }

    private static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("Selecciona un día y una hora válidos.");
        }
    }
}
