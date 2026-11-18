package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.forms.ProductEditForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Inventario de un centro. Todas las operaciones comprueban que el producto pertenece al centro. */
@Service
@Transactional
public class InventoryService {

    /** Tope del aviso de stock bajo de cada producto (el mismo que el stock). */
    public static final int MAX_LOW_STOCK_ALERT = 999;
    static final double MAX_PRICE = 10_000;
    static final int MAX_NAME = 120;
    static final int MAX_DESCRIPTION = 1000;
    /** Tope de unidades de un producto en el inventario: ningún centro necesita más y así el número nunca se desborda. */
    public static final int MAX_STOCK = 999;
    /** Lo más que se puede sumar o restar en un ajuste rápido (los botones envían 1). */
    public static final int MAX_ADJUSTMENT = 10;

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CenterRepository centerRepository;
    private final ApplicationEventPublisher events;

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductRepository productRepository,
                            CenterRepository centerRepository,
                            ApplicationEventPublisher events) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.centerRepository = centerRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Inventory> getInventoryByCenter(Long centerId) {
        return inventoryRepository.findByCenterId(centerId);
    }

    /** Productos del catálogo general y los creados por el centro que todavía no tiene en su inventario. */
    @Transactional(readOnly = true)
    public List<Product> getAvailableCatalogProducts(Long centerId) {
        Set<Long> inInventory = inventoryRepository.findByCenterId(centerId).stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        return productRepository.findAll().stream()
                .filter(product -> visibleTo(product, centerId))
                .filter(product -> !inInventory.contains(product.getId()))
                .toList();
    }

    /**
     * Suma o resta unidades y devuelve el stock resultante (entre 0 y {@link #MAX_STOCK}). La fila queda bloqueada
     * mientras tanto: dos ajustes a la vez (dos pestañas, dos trabajadores) se aplican los dos, uno detrás de otro.
     */
    public Inventory adjustStock(Long centerId, Long inventoryId, int change) {
        if (change == 0 || Math.abs(change) > MAX_ADJUSTMENT) {
            throw new BusinessRuleException("Cada ajuste puede sumar o restar entre 1 y " + MAX_ADJUSTMENT + " unidades.");
        }
        Inventory item = inventoryRepository.findForUpdate(inventoryId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe en el inventario de tu centro."));
        if (change > 0 && item.getStock() >= MAX_STOCK) {
            throw new BusinessRuleException("Como mucho puede haber " + MAX_STOCK + " unidades de un producto en el inventario.");
        }
        // Hacia arriba se queda en el tope; un stock antiguo por encima del tope solo baja si se resta
        item.setStock(Math.max(0, change > 0 ? Math.min(MAX_STOCK, item.getStock() + change) : item.getStock() + change));
        return inventoryRepository.save(item);
    }

    /**
     * Cambia nombre, categoría, precio y descripción de un producto creado por el centro. Los del catálogo general
     * son de todos los centros y no se tocan. Las ventas ya hechas guardan su precio.
     */
    public Product updateOwnProduct(Long centerId, Long inventoryId, ProductEditForm form) {
        Product product = findInCenter(centerId, inventoryId).getProduct();
        if (!centerId.equals(product.getCenterId())) {
            throw new BusinessRuleException("Este producto es del catálogo general de Cuquora y no se puede cambiar.");
        }
        if (FormText.isBlank(form.name()) || form.price() == null) {
            throw new BusinessRuleException("Completa el nombre y el precio del producto.");
        }
        if (form.price() < 0 || form.price().isNaN() || form.price().isInfinite() || form.price() > MAX_PRICE) {
            throw new BusinessRuleException("El precio tiene que estar entre 0 y 10.000 €.");
        }
        String name = form.name().trim();
        String description = FormText.trimToNull(form.description());
        if (name.length() > MAX_NAME) {
            throw new BusinessRuleException("El nombre puede tener como mucho " + MAX_NAME + " caracteres.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new BusinessRuleException("La descripción puede tener como mucho " + MAX_DESCRIPTION + " caracteres.");
        }
        product.setName(name);
        product.setDescription(description);
        product.setPrice(Math.round(form.price() * 100) / 100.0);
        if (ProductEditForm.CATEGORIES.contains(form.category())) {
            product.setCategory(form.category());
        }
        return product;
    }

    /** Pone o quita (nulo) el aviso de stock bajo de un producto del inventario del centro. */
    public Inventory setLowStockAlert(Long centerId, Long inventoryId, Integer units) {
        if (units != null && (units < 1 || units > MAX_LOW_STOCK_ALERT)) {
            throw new BusinessRuleException("El aviso tiene que ser de 1 a " + MAX_LOW_STOCK_ALERT
                    + " unidades. Déjalo vacío para no recibir avisos.");
        }
        Inventory item = findInCenter(centerId, inventoryId);
        item.setLowStockAlert(units);
        return item;
    }

    /** Añade el producto al inventario y devuelve el producto (nuevo o del catálogo). */
    public Product addToInventory(Long centerId, InventoryItemForm form) {
        if (form.isExistingProduct()) {
            return addExistingProduct(centerId, form.productId(), form.stockOrZero());
        } else if (form.isNewProduct()) {
            return addNewProduct(centerId, form);
        } else {
            throw new BusinessRuleException("Completa los datos del producto antes de añadirlo.");
        }
    }

    /** Id del producto de una línea del inventario del centro. */
    @Transactional(readOnly = true)
    public Long productIdOf(Long centerId, Long inventoryId) {
        return findInCenter(centerId, inventoryId).getProduct().getId();
    }

    public void removeFromInventory(Long centerId, Long inventoryId) {
        inventoryRepository.delete(findInCenter(centerId, inventoryId));
    }

    private Product addExistingProduct(Long centerId, Long productId, int stock) {
        Product product = productRepository.findById(productId)
                .filter(p -> visibleTo(p, centerId))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));
        Inventory item = inventoryRepository.findByCenterIdAndProductId(centerId, productId)
                .map(existing -> {
                    existing.setStock(checkedStock((long) existing.getStock() + stock));
                    return existing;
                })
                .orElseGet(() -> Inventory.builder()
                        .center(center(centerId))
                        .product(product)
                        .stock(checkedStock(stock))
                        .build());
        inventoryRepository.save(item);
        return product;
    }

    private Product addNewProduct(Long centerId, InventoryItemForm form) {
        if (form.price() != null && (form.price() < 0 || form.price().isNaN() || form.price().isInfinite())) {
            throw new BusinessRuleException("El precio no puede ser negativo.");
        }
        String barcode = FormText.isBlank(form.barcode()) ? null : ProductLookupService.normalize(form.barcode());
        if (barcode != null) {
            productRepository.findUsableByBarcode(barcode, centerId).stream().findFirst().ifPresent(existing -> {
                throw new BusinessRuleException("Ya tienes «" + existing.getName() + "» con ese código de barras. "
                        + "Elígelo en «Elegir del Catálogo» para sumar stock.");
            });
        }
        Product product = productRepository.save(Product.builder()
                .name(form.name().trim())
                .description(form.description())
                .category(form.category())
                .price(form.price())
                .barcode(barcode)
                // Solo para este centro: si fuese al catálogo general, el resto vería los productos y precios que crea
                .centerId(centerId)
                .build());
        inventoryRepository.save(Inventory.builder()
                .center(center(centerId))
                .product(product)
                .stock(checkedStock(form.stockOrZero()))
                .build());
        if (barcode != null) {
            // El siguiente centro que lo escanee lo encontrará relleno (ProductSharingService)
            events.publishEvent(new ProductSharedEvent(product.getId()));
        }
        return product;
    }

    /** Un producto del catálogo general (sin centro) o creado por este centro. */
    private static boolean visibleTo(Product product, Long centerId) {
        return product.getCenterId() == null || product.getCenterId().equals(centerId);
    }

    /** El stock que se guarda al dar de alta o sumar desde el catálogo: nunca por encima de {@link #MAX_STOCK}. */
    private static int checkedStock(long stock) {
        if (stock > MAX_STOCK) {
            throw new BusinessRuleException("Como mucho puede haber " + MAX_STOCK + " unidades de un producto en el inventario.");
        }
        return (int) stock;
    }

    private Inventory findInCenter(Long centerId, Long inventoryId) {
        return inventoryRepository.findByIdAndCenterId(inventoryId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe en el inventario de tu centro."));
    }

    private Center center(Long centerId) {
        return centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }
}
