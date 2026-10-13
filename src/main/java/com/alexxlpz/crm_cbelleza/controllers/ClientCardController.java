package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.NewClientForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.AppointmentService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService.ClientCardView;
import com.alexxlpz.crm_cbelleza.services.ClientCardTemplateService;
import com.alexxlpz.crm_cbelleza.services.TreatmentSummaryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cartera de clientes del centro, fichas técnicas y plantilla de la ficha. */
@Controller
@RequestMapping("/worker/clients")
public class ClientCardController {

    /** Parámetros del formulario que no son campos de la ficha. */
    private static final Set<String> NON_FIELD_PARAMS = Set.of("clientId", "redirectAfter");

    private final ClientCardService clientCardService;
    private final ClientCardTemplateService templateService;
    private final AppointmentService appointmentService;
    private final TreatmentSummaryService summaryService;
    private final ProductSaleService saleService;
    private final LoyaltyService loyaltyService;
    private final InventoryService inventoryService;
    private final CenterService centerService;

    public ClientCardController(ClientCardService clientCardService,
                                ClientCardTemplateService templateService,
                                AppointmentService appointmentService,
                                TreatmentSummaryService summaryService,
                                ProductSaleService saleService,
                                LoyaltyService loyaltyService,
                                InventoryService inventoryService,
                                CenterService centerService) {
        this.clientCardService = clientCardService;
        this.templateService = templateService;
        this.appointmentService = appointmentService;
        this.summaryService = summaryService;
        this.saleService = saleService;
        this.loyaltyService = loyaltyService;
        this.inventoryService = inventoryService;
        this.centerService = centerService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails worker,
                       @RequestParam(required = false) String search,
                       Model model) {
        List<ClientSummaryDTO> clients = clientCardService.getClientsForCenter(worker.getCenterId(), search);
        long filledCards = clients.stream().filter(ClientSummaryDTO::isHasFilledCard).count();
        model.addAttribute("clients", clients);
        model.addAttribute("filledCards", filledCards);
        model.addAttribute("pendingCards", clients.size() - filledCards);
        model.addAttribute("totalAppointments", clients.stream().mapToInt(ClientSummaryDTO::getTotalAppointments).sum());
        model.addAttribute("searchQuery", search);
        return "worker/clients";
    }

    @GetMapping("/{clientId}")
    public String detail(@AuthenticationPrincipal AppUserDetails worker,
                         @PathVariable Long clientId,
                         Model model) {
        ClientCardView view = clientCardService.getCardView(worker.getCenterId(), clientId);
        model.addAttribute("client", view.client());
        model.addAttribute("card", view.card());
        model.addAttribute("fields", view.fields());
        model.addAttribute("cardData", view.data());
        List<Appointment> appointments = appointmentService.getClientAppointmentsInCenter(worker.getCenterId(), clientId);
        model.addAttribute("appointments", appointments);
        model.addAttribute("alerts", appointments.stream().filter(Appointment::isTreatmentAlert).toList());
        model.addAttribute("centerNow", summaryService.centerNow(worker.getCenterId()));
        List<ProductSale> sales = saleService.salesOf(worker.getCenterId(), clientId);
        model.addAttribute("sales", sales);
        model.addAttribute("salesTotal", sales.stream().mapToDouble(ProductSale::getTotal).sum());
        model.addAttribute("saleItems", inventoryService.getInventoryByCenter(worker.getCenterId()));
        model.addAttribute("lowStockThreshold", InventoryService.LOW_STOCK_THRESHOLD);
        model.addAttribute("loyalty", loyaltyService.cardFor(centerService.getCenterById(worker.getCenterId()), view.client()));
        return "worker/client_detail";
    }

    @PostMapping("/{clientId}/card")
    public String saveCard(@AuthenticationPrincipal AppUserDetails worker,
                           @PathVariable Long clientId,
                           @RequestParam Map<String, String> params,
                           RedirectAttributes redirect) {
        Map<String, String> values = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (!key.startsWith("_") && !NON_FIELD_PARAMS.contains(key)) {
                values.put(key, value);
            }
        });
        clientCardService.saveCardData(worker.getCenterId(), clientId, values, worker.getId());
        redirect.addFlashAttribute("successMessage", "Ficha técnica guardada.");
        return redirectAfter(params.get("redirectAfter"), "/worker/clients/" + clientId);
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal AppUserDetails worker,
                         @ModelAttribute NewClientForm form,
                         RedirectAttributes redirect) {
        try {
            var created = clientCardService.createClientManually(worker.getCenterId(), form, worker.getId());
            redirect.addFlashAttribute("successMessage", created.emailSkipped()
                    ? "Cliente dado de alta sin correo: ese correo no se puede usar para un cliente nuevo. Ya puedes rellenar su ficha técnica."
                    : "Cliente dado de alta. Ya puedes consultar o rellenar su ficha técnica.");
            return "redirect:/worker/clients/" + created.clientId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/worker/clients";
        }
    }

    @GetMapping("/template")
    public String template(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        model.addAttribute("fields", templateService.getFields(worker.getCenterId()));
        return "worker/client_card_template";
    }

    @PostMapping("/template")
    public String saveTemplate(@AuthenticationPrincipal AppUserDetails worker,
                               @RequestParam(value = "fieldIds", required = false) List<String> ids,
                               @RequestParam(value = "fieldLabels", required = false) List<String> labels,
                               @RequestParam(value = "fieldTypes", required = false) List<String> types,
                               @RequestParam(value = "fieldPlaceholders", required = false) List<String> placeholders,
                               @RequestParam(value = "redirectAfter", required = false) String redirectAfter,
                               RedirectAttributes redirect) {
        try {
            templateService.saveFields(worker.getCenterId(), ids, labels == null ? List.of() : labels, types, placeholders);
            redirect.addFlashAttribute("successMessage", "Plantilla de la ficha guardada.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectAfter(redirectAfter, "/worker/clients/template");
    }

    /** Solo se permite volver a rutas internas del panel (evita redirecciones abiertas). */
    private static String redirectAfter(String requested, String fallback) {
        boolean safe = requested != null && requested.startsWith("/worker/") && !requested.contains("//");
        return "redirect:" + (safe ? requested : fallback);
    }
}
