package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.InventoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Ajuste rápido de stock (+/-) desde las tarjetas del inventario. Devuelve el stock resultante. */
@RestController
@ApiEndpoint
public class InventoryApiController {

    private final InventoryService inventoryService;

    public InventoryApiController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/worker/inventory/{id}/adjust-stock")
    public Map<String, Integer> adjustStock(@AuthenticationPrincipal AppUserDetails worker,
                                            @PathVariable Long id,
                                            @RequestParam int change) {
        int stock = inventoryService.adjustStock(worker.getCenterId(), id, change);
        return Map.of("stock", stock, "lowStockThreshold", InventoryService.LOW_STOCK_THRESHOLD);
    }
}
