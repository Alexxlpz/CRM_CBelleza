package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.ProductSaleForm;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService;
import com.alexxlpz.crm_cbelleza.services.ProductSaleService.SaleResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Ventas de productos y canje de puntos desde la ficha del cliente. */
@Controller
@RequestMapping("/worker/clients/{clientId}")
public class ClientSalesController {

    private final ProductSaleService saleService;
    private final LoyaltyService loyaltyService;
    private final ClientCardService clientCards;
    private final CenterService centerService;

    public ClientSalesController(ProductSaleService saleService, LoyaltyService loyaltyService,
                                 ClientCardService clientCards, CenterService centerService) {
        this.saleService = saleService;
        this.loyaltyService = loyaltyService;
        this.clientCards = clientCards;
        this.centerService = centerService;
    }

    @PostMapping("/sales")
    public String sell(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long clientId,
                       @ModelAttribute ProductSaleForm form, RedirectAttributes redirect) {
        try {
            SaleResult result = saleService.sell(worker.getCenterId(), clientId, form, worker.getId());
            redirect.addFlashAttribute("successMessage", result.message());
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/clients/" + clientId + "#productos";
    }

    @PostMapping("/sales/{saleId}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long clientId,
                         @PathVariable Long saleId, RedirectAttributes redirect) {
        var sale = saleService.cancel(worker.getCenterId(), clientId, saleId);
        redirect.addFlashAttribute("successMessage", "Venta anulada: " + sale.getQuantity() + " × "
                + sale.getProduct().getName() + " vuelve al inventario.");
        return "redirect:/worker/clients/" + clientId + "#productos";
    }

    @PostMapping("/loyalty/redeem")
    public String redeem(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long clientId,
                         RedirectAttributes redirect) {
        var client = clientCards.requireClientOfCenter(worker.getCenterId(), clientId);
        var center = centerService.getCenterById(worker.getCenterId());
        try {
            loyaltyService.redeem(center, client, worker.getId());
            redirect.addFlashAttribute("successMessage", "Recompensa canjeada: " + center.getLoyaltyRewardText() + ".");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/worker/clients/" + clientId + "#puntos";
    }
}
