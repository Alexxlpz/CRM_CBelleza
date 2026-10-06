package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.ContactForm;
import com.alexxlpz.crm_cbelleza.mail.InquiryMailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Formularios públicos que generan correos: contacto y solicitud de alta de centro. */
@Controller
public class ContactController {

    private final InquiryMailService mailService;

    public ContactController(InquiryMailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping({"/contact", "/contacto"})
    public String contact(Model model) {
        model.addAttribute("activePage", "contact");
        return "contact";
    }

    @PostMapping({"/contact", "/contacto"})
    public String sendContact(@ModelAttribute ContactForm form, RedirectAttributes redirect) {
        try {
            form.validate();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addFlashAttribute("form", form);
            return "redirect:/contact";
        }
        mailService.sendContactInquiry(form);
        redirect.addFlashAttribute("successMessage",
                "Gracias por contactar con nosotros, " + form.name().trim()
                        + ". Hemos recibido tu mensaje y te responderemos a la mayor brevedad posible.");
        return "redirect:/contact";
    }

    @GetMapping({"/register-center", "/alta-centro"})
    public String registerCenter(Model model) {
        model.addAttribute("activePage", "register-center");
        return "register-center";
    }

    @PostMapping({"/register-center", "/alta-centro"})
    public String sendCenterRegistration(@ModelAttribute CenterRegistrationForm form, RedirectAttributes redirect) {
        try {
            form.validate();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register-center";
        }
        mailService.sendCenterRegistration(form);
        redirect.addFlashAttribute("successMessage",
                "¡Solicitud enviada con éxito! Hemos enviado un correo de confirmación a " + form.email().trim()
                        + ". Revisaremos los datos de la empresa en un plazo máximo de una semana.");
        return "redirect:/register-center";
    }
}
