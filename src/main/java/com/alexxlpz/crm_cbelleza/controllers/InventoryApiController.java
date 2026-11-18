package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.Inventory;
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
    public Map<String, Object> adjustStock(@AuthenticationPrincipal AppUserDetails worker,
                                           @PathVariable Long id,
                                           @RequestParam int change) {
        Inventory item = inventoryService.adjustStock(worker.getCenterId(), id, change);
        // lowStock: con el aviso que el centro puso a este producto (sin aviso, nunca es «Bajo Stock»)
        return Map.of("stock", item.getStock(), "lowStock", item.isLowStock(), "maxStock", InventoryService.MAX_STOCK);
    }
}
