package com.alexxlpz.crm_cbelleza.forms;

/**
 * Alta de un producto en el inventario del centro.
 * mode = "existing" (producto del catálogo general) o "new" (producto nuevo).
 */
public record InventoryItemForm(String mode, Long productId, String name, String description,
                                String category, Double price, Integer stock) {

    public boolean isExistingProduct() {
        return "existing".equals(mode) && productId != null;
    }

    public boolean isNewProduct() {
        return "new".equals(mode) && !FormText.isBlank(name) && price != null;
    }

    public int stockOrZero() {
        return stock == null ? 0 : Math.max(0, stock);
    }
}
