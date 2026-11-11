package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.FormText;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
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

    /** A partir de estas unidades (incluidas) el stock se considera bajo. */
    public static final int LOW_STOCK_THRESHOLD = 3;

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

    /** Suma o resta unidades y devuelve el stock resultante (nunca negativo). */
    public int adjustStock(Long centerId, Long inventoryId, int change) {
        Inventory item = findInCenter(centerId, inventoryId);
        item.setStock(Math.max(0, item.getStock() + change));
        return inventoryRepository.save(item).getStock();
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
                    existing.setStock(existing.getStock() + stock);
                    return existing;
                })
                .orElseGet(() -> Inventory.builder()
                        .center(center(centerId))
                        .product(product)
                        .stock(stock)
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
                .stock(form.stockOrZero())
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

    private Inventory findInCenter(Long centerId, Long inventoryId) {
        return inventoryRepository.findByIdAndCenterId(inventoryId, centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe en el inventario de tu centro."));
    }

    private Center center(Long centerId) {
        return centerRepository.findById(centerId)
                .orElseThrow(() -> new ResourceNotFoundException("Centro no encontrado."));
    }
}
