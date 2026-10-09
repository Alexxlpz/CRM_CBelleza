package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Inventory;
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

import java.util.List;
import java.util.Objects;

/** Inventario del centro del trabajador. El ajuste rápido de stock está en {@link InventoryApiController}. */
@Controller
public class InventoryController {

    private final InventoryService inventoryService;

    /** Mismo umbral que usa el script de la plantilla para la etiqueta «Bajo Stock». */
    private static final int LOW_STOCK_THRESHOLD = 3;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/worker/inventory")
    public String inventory(@AuthenticationPrincipal AppUserDetails worker, Model model) {
        List<Inventory> items = inventoryService.getInventoryByCenter(worker.getCenterId());
        List<Integer> stocks = items.stream().map(Inventory::getStock).filter(Objects::nonNull).toList();
        model.addAttribute("inventoryItems", items);
        model.addAttribute("totalUnits", stocks.stream().mapToInt(Integer::intValue).sum());
        model.addAttribute("lowStockCount", stocks.stream().filter(s -> s > 0 && s <= LOW_STOCK_THRESHOLD).count());
        model.addAttribute("outOfStockCount", stocks.stream().filter(s -> s == 0).count());
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
