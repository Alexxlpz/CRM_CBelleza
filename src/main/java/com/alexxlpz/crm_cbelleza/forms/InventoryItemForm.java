package com.alexxlpz.crm_cbelleza.forms;

/**
 * Alta de un producto en el inventario del centro.
 * mode = "existing" (producto del catálogo general) o "new" (producto nuevo).
 * barcode: código escaneado o escrito (opcional). useFoundPhoto: poner la foto que encontró la búsqueda por código
 * si no se sube otra.
 */
public record InventoryItemForm(String mode, Long productId, String name, String description,
                                String category, Double price, Integer stock, String barcode, Boolean useFoundPhoto) {

    public InventoryItemForm(String mode, Long productId, String name, String description,
                             String category, Double price, Integer stock) {
        this(mode, productId, name, description, category, price, stock, null, null);
    }

    public boolean isExistingProduct() {
        return "existing".equals(mode) && productId != null;
    }

    public boolean isNewProduct() {
        return "new".equals(mode) && !FormText.isBlank(name) && price != null;
    }

    public boolean wantsFoundPhoto() {
        return Boolean.TRUE.equals(useFoundPhoto);
    }

    public int stockOrZero() {
        return stock == null ? 0 : Math.max(0, stock);
    }
}
