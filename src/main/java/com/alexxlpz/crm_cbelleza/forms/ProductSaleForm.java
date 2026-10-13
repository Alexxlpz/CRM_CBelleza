package com.alexxlpz.crm_cbelleza.forms;

/** Venta de un producto del inventario del centro a un cliente desde su ficha. */
public record ProductSaleForm(Long inventoryId, Integer quantity) {
}
