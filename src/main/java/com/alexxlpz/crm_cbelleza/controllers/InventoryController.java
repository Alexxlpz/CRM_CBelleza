package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.security.CenterOwnerOnly;
import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.forms.ProductSaleForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Inventario del centro del trabajador. El ajuste rápido de stock está en {@link InventoryApiController}. */
@Controller
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductSaleService saleService;
    private final ClientCardService clientCards;
    private final LoyaltyService loyaltyService;
    private final CenterService centerService;

    /** Mismo umbral que usa el script de la plantilla para la etiqueta «Bajo Stock». */
    private static final int LOW_STOCK_THRESHOLD = 3;

    public InventoryController(InventoryService inventoryService, ProductSaleService saleService,
                               ClientCardService clientCards, LoyaltyService loyaltyService, CenterService centerService) {
        this.inventoryService = inventoryService;
        this.saleService = saleService;
        this.clientCards = clientCards;
        this.loyaltyService = loyaltyService;
        this.centerService = centerService;
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
        model.addAttribute("saleClients", clientCards.getClientsForCenter(worker.getCenterId(), null).stream()
                .sorted(Comparator.comparing(ClientSummaryDTO::getName, Comparator.nullsLast(Collator.getInstance(Locale.of("es")))))
                .toList());
        model.addAttribute("loyalty", loyaltyService.settingsOf(centerService.getCenterById(worker.getCenterId())));
        return "worker/inventory";
    }

    /** Venta desde el inventario: igual que desde la ficha, pero eligiendo el cliente en el modal. */
    @PostMapping("/worker/inventory/sales")
    public String sell(@AuthenticationPrincipal AppUserDetails worker,
                       @RequestParam(required = false) Long clientId,
                       @ModelAttribute ProductSaleForm form,
                       RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("successMessage", saleService.sell(worker.getCenterId(), clientId, form, worker.getId()).message());
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/inventory";
    }

    @CenterOwnerOnly
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

    @CenterOwnerOnly
    @PostMapping("/worker/inventory/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails worker,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {
        inventoryService.removeFromInventory(worker.getCenterId(), id);
        redirect.addFlashAttribute("successMessage", "Producto retirado del inventario del centro.");
        return "redirect:/worker/inventory";
    }
}
