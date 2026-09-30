package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.ClientCard;
import com.alexxlpz.crm_cbelleza.entities.ClientCardTemplate;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.NotificationService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/worker/clients")
public class ClientCardController {

    private final ClientCardService clientCardService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.alexxlpz.crm_cbelleza.services.AppointmentService appointmentService;
    private final com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository clientCardRepository;

    public ClientCardController(ClientCardService clientCardService,
                                UserService userService,
                                NotificationService notificationService,
                                com.alexxlpz.crm_cbelleza.services.AppointmentService appointmentService,
                                com.alexxlpz.crm_cbelleza.repositories.ClientCardRepository clientCardRepository) {
        this.clientCardService = clientCardService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.appointmentService = appointmentService;
        this.clientCardRepository = clientCardRepository;
    }

    private boolean isWorkerSessionValid(HttpSession session) {
        String role = (String) session.getAttribute("sessionRole");
        Long centerId = (Long) session.getAttribute("sessionCenterId");
        return "WORKER".equals(role) && centerId != null;
    }

    // List all clients for the center
    @GetMapping
    public String listClients(@RequestParam(value = "search", required = false) String search,
                              Model model, HttpSession session) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        List<ClientSummaryDTO> clients = clientCardService.getClientsForCenter(centerId, search);

        model.addAttribute("clients", clients);
        model.addAttribute("searchQuery", search);
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/clients";
    }

    // View registered client detail & card
    @GetMapping("/{clientId}")
    public String clientDetail(@PathVariable("clientId") Long clientId,
                               Model model, HttpSession session) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        User client = userService.getUserById(clientId);
        if (client == null) {
            return "redirect:/worker/clients";
        }

        boolean hasConfirmed = appointmentService.hasConfirmedAppointment(centerId, clientId, null);
        boolean hasCard = clientCardRepository.findByCenterIdAndClientId(centerId, clientId).isPresent();

        if (!hasConfirmed && !hasCard) {
            return "redirect:/worker/clients";
        }

        ClientCard card = clientCardService.getOrCreateCard(centerId, clientId, null, null);
        ClientCardTemplate template = clientCardService.getOrCreateTemplateForCenter(centerId);
        List<ClientCardFieldDTO> fields = clientCardService.parseTemplateFields(template.getFieldsJson());
        Map<String, String> cardData = clientCardService.parseCardData(card.getDataJson());
        List<Appointment> appointments = clientCardService.getClientAppointmentsInCenter(centerId, clientId, null);

        model.addAttribute("client", client);
        model.addAttribute("isGuest", false);
        model.addAttribute("card", card);
        model.addAttribute("fields", fields);
        model.addAttribute("cardData", cardData);
        model.addAttribute("appointments", appointments);
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/client_detail";
    }

    // View guest client detail & card
    @GetMapping("/guest")
    public String guestClientDetail(@RequestParam("phone") String phone,
                                    Model model, HttpSession session) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        boolean hasConfirmed = appointmentService.hasConfirmedAppointment(centerId, null, phone);
        Optional<ClientCard> cardOpt = clientCardRepository.findByCenterIdAndGuestPhone(centerId, phone);

        if (!hasConfirmed && cardOpt.isEmpty()) {
            return "redirect:/worker/clients";
        }

        List<Appointment> appointments = clientCardService.getClientAppointmentsInCenter(centerId, null, phone);
        String guestName = cardOpt.map(ClientCard::getGuestName)
                .orElseGet(() -> !appointments.isEmpty() && appointments.get(0).getGuestName() != null ? appointments.get(0).getGuestName() : "Invitado");

        ClientCard card = cardOpt.orElseGet(() -> clientCardService.getOrCreateCard(centerId, null, phone, guestName));
        ClientCardTemplate template = clientCardService.getOrCreateTemplateForCenter(centerId);
        List<ClientCardFieldDTO> fields = clientCardService.parseTemplateFields(template.getFieldsJson());
        Map<String, String> cardData = clientCardService.parseCardData(card.getDataJson());

        model.addAttribute("guestName", guestName);
        model.addAttribute("guestPhone", phone);
        model.addAttribute("isGuest", true);
        model.addAttribute("card", card);
        model.addAttribute("fields", fields);
        model.addAttribute("cardData", cardData);
        model.addAttribute("appointments", appointments);
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/client_detail";
    }

    // Save registered client card data
    @PostMapping("/{clientId}/card")
    public String saveClientCard(@PathVariable("clientId") Long clientId,
                                 @RequestParam Map<String, String> allParams,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");
        Long workerUserId = (Long) session.getAttribute("sessionUserId");
        User worker = workerUserId != null ? userService.getUserById(workerUserId) : null;

        boolean hasConfirmed = appointmentService.hasConfirmedAppointment(centerId, clientId, null);
        boolean hasCard = clientCardRepository.findByCenterIdAndClientId(centerId, clientId).isPresent();

        if (!hasConfirmed && !hasCard) {
            redirectAttributes.addFlashAttribute("errorMessage", "El cliente debe tener al menos una cita confirmada o estar dado de alta para disponer de ficha.");
            return "redirect:/worker/clients";
        }

        Map<String, String> fieldValues = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (!k.startsWith("_") && !k.equals("clientId") && !k.equals("redirectAfter")) {
                fieldValues.put(k, v);
            }
        });

        clientCardService.updateCardData(centerId, clientId, null, null, fieldValues, worker);
        redirectAttributes.addFlashAttribute("successMessage", "Ficha técnica del cliente guardada con éxito.");

        String redirectAfter = allParams.get("redirectAfter");
        if (redirectAfter != null && !redirectAfter.trim().isEmpty() && redirectAfter.startsWith("/worker/")) {
            return "redirect:" + redirectAfter;
        }

        return "redirect:/worker/clients/" + clientId;
    }

    // Save guest client card data
    @PostMapping("/guest/card")
    public String saveGuestCard(@RequestParam("guestPhone") String guestPhone,
                                @RequestParam(value = "guestName", required = false) String guestName,
                                @RequestParam Map<String, String> allParams,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");
        Long workerUserId = (Long) session.getAttribute("sessionUserId");
        User worker = workerUserId != null ? userService.getUserById(workerUserId) : null;

        boolean hasConfirmed = appointmentService.hasConfirmedAppointment(centerId, null, guestPhone);
        boolean hasCard = clientCardRepository.findByCenterIdAndGuestPhone(centerId, guestPhone).isPresent();

        if (!hasConfirmed && !hasCard) {
            redirectAttributes.addFlashAttribute("errorMessage", "El cliente debe tener al menos una cita confirmada o estar dado de alta para disponer de ficha.");
            return "redirect:/worker/clients";
        }

        Map<String, String> fieldValues = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (!k.startsWith("_") && !k.equals("guestPhone") && !k.equals("guestName") && !k.equals("redirectAfter")) {
                fieldValues.put(k, v);
            }
        });

        clientCardService.updateCardData(centerId, null, guestPhone, guestName, fieldValues, worker);
        redirectAttributes.addFlashAttribute("successMessage", "Ficha técnica del cliente guardada con éxito.");

        String redirectAfter = allParams.get("redirectAfter");
        if (redirectAfter != null && !redirectAfter.trim().isEmpty() && redirectAfter.startsWith("/worker/")) {
            return "redirect:" + redirectAfter;
        }

        return "redirect:/worker/clients/guest?phone=" + URLEncoder.encode(guestPhone, StandardCharsets.UTF_8);
    }

    // Worker Action: Manually create a new client
    @PostMapping("/create")
    public String createClient(@RequestParam("name") String name,
                               @RequestParam("phone") String phone,
                               @RequestParam(value = "email", required = false) String email,
                               @RequestParam(value = "initialNotes", required = false) String initialNotes,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");
        Long workerUserId = (Long) session.getAttribute("sessionUserId");
        User worker = workerUserId != null ? userService.getUserById(workerUserId) : null;

        try {
            String redirectUrl = clientCardService.createClientManually(centerId, name, phone, email, initialNotes, worker);
            redirectAttributes.addFlashAttribute("successMessage", "Cliente dado de alta con éxito. Ya puedes consultar o rellenar su ficha técnica.");
            return "redirect:" + redirectUrl;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/worker/clients";
        }
    }

    // View template configuration
    @GetMapping("/template")
    public String viewTemplate(Model model, HttpSession session) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        ClientCardTemplate template = clientCardService.getOrCreateTemplateForCenter(centerId);
        List<ClientCardFieldDTO> fields = clientCardService.parseTemplateFields(template.getFieldsJson());

        model.addAttribute("template", template);
        model.addAttribute("fields", fields);
        model.addAttribute("sessionRole", session.getAttribute("sessionRole"));
        model.addAttribute("sessionUserName", session.getAttribute("sessionUserName"));
        model.addAttribute("sessionCenterName", session.getAttribute("sessionCenterName"));
        model.addAttribute("notifications", notificationService.getNotificationsForCenter(centerId));

        return "worker/client_card_template";
    }

    // Save template configuration
    @PostMapping("/template")
    public String saveTemplate(@RequestParam("fieldIds") List<String> fieldIds,
                               @RequestParam("fieldLabels") List<String> fieldLabels,
                               @RequestParam("fieldTypes") List<String> fieldTypes,
                               @RequestParam(value = "fieldPlaceholders", required = false) List<String> fieldPlaceholders,
                               @RequestParam(value = "redirectAfter", required = false) String redirectAfter,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        if (!isWorkerSessionValid(session)) {
            return "redirect:/home";
        }
        Long centerId = (Long) session.getAttribute("sessionCenterId");

        List<ClientCardFieldDTO> fields = new ArrayList<>();
        for (int i = 0; i < fieldLabels.size(); i++) {
            String label = fieldLabels.get(i).trim();
            if (label.isEmpty()) continue;

            String id = (fieldIds != null && i < fieldIds.size() && !fieldIds.get(i).trim().isEmpty())
                    ? fieldIds.get(i).trim().toLowerCase().replaceAll("[^a-z0-9_]", "_")
                    : label.toLowerCase().replaceAll("[^a-z0-9_]", "_");

            String type = (fieldTypes != null && i < fieldTypes.size()) ? fieldTypes.get(i) : "text";
            String placeholder = (fieldPlaceholders != null && i < fieldPlaceholders.size()) ? fieldPlaceholders.get(i) : "";

            fields.add(ClientCardFieldDTO.builder()
                    .id(id)
                    .label(label)
                    .type(type)
                    .placeholder(placeholder)
                    .required(false)
                    .build());
        }

        clientCardService.saveTemplateFields(centerId, fields);
        redirectAttributes.addFlashAttribute("successMessage", "Estructura de la ficha del centro actualizada con éxito.");

        if (redirectAfter != null && !redirectAfter.trim().isEmpty() && redirectAfter.startsWith("/worker/")) {
            return "redirect:" + redirectAfter;
        }

        return "redirect:/worker/clients/template";
    }
}
