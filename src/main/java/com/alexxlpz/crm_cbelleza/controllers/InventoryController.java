package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Inventario del centro del trabajador. El ajuste rápido de stock está en {@link InventoryApiController}. */
@Controller
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/worker/inventory")
    public String inventory(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        model.addAttribute("inventoryItems", inventoryService.getInventoryByCenter(worker.getCenterId()));
        model.addAttribute("catalogProducts", inventoryService.getAvailableCatalogProducts(worker.getCenterId()));
        return "worker/inventory";
    }

    @PostMapping("/worker/inventory/add")
    public String add(@AuthenticationPrincipal AppUserDetails worker,
                      @ModelAttribute InventoryItemForm form,
                      RedirectAttributes redirect) {
        try {
            inventoryService.addToInventory(worker.getCenterId(), form);
            redirect.addFlashAttribute("successMessage", "Producto añadido al inventario.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/inventory";
    }

    @PostMapping("/worker/inventory/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails worker,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {
        inventoryService.removeFromInventory(worker.getCenterId(), id);
        redirect.addFlashAttribute("successMessage", "Producto retirado del inventario del centro.");
        return "redirect:/worker/inventory";
    }
}
