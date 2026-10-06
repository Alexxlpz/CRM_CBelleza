package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.InventoryItemForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
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

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductRepository productRepository,
                            CenterRepository centerRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.centerRepository = centerRepository;
    }

    @Transactional(readOnly = true)
    public List<Inventory> getInventoryByCenter(Long centerId) {
        return inventoryRepository.findByCenterId(centerId);
    }

    /** Productos del catálogo general que el centro todavía no tiene en su inventario. */
    @Transactional(readOnly = true)
    public List<Product> getAvailableCatalogProducts(Long centerId) {
        Set<Long> inInventory = inventoryRepository.findByCenterId(centerId).stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        return productRepository.findAll().stream()
                .filter(product -> !inInventory.contains(product.getId()))
                .toList();
    }

    /** Suma o resta unidades y devuelve el stock resultante (nunca negativo). */
    public int adjustStock(Long centerId, Long inventoryId, int change) {
        Inventory item = findInCenter(centerId, inventoryId);
        item.setStock(Math.max(0, item.getStock() + change));
        return inventoryRepository.save(item).getStock();
    }

    public void addToInventory(Long centerId, InventoryItemForm form) {
        if (form.isExistingProduct()) {
            addExistingProduct(centerId, form.productId(), form.stockOrZero());
        } else if (form.isNewProduct()) {
            addNewProduct(centerId, form);
        } else {
            throw new BusinessRuleException("Completa los datos del producto antes de añadirlo.");
        }
    }

    public void removeFromInventory(Long centerId, Long inventoryId) {
        inventoryRepository.delete(findInCenter(centerId, inventoryId));
    }

    private void addExistingProduct(Long centerId, Long productId, int stock) {
        Product product = productRepository.findById(productId)
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
    }

    private void addNewProduct(Long centerId, InventoryItemForm form) {
        Product product = productRepository.save(Product.builder()
                .name(form.name().trim())
                .description(form.description())
                .category(form.category())
                .price(form.price())
                .build());
        inventoryRepository.save(Inventory.builder()
                .center(center(centerId))
                .product(product)
                .stock(form.stockOrZero())
                .build());
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
