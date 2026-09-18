package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final CenterRepository centerRepository;

    public InventoryService(InventoryRepository inventoryRepository, ProductRepository productRepository, CenterRepository centerRepository) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.centerRepository = centerRepository;
    }

    public List<Inventory> getInventoryByCenter(Long centerId) {
        return inventoryRepository.findByCenterId(centerId);
    }

    public List<Product> getAvailableCatalogProducts(Long centerId) {
        List<Inventory> inventoryItems = inventoryRepository.findByCenterId(centerId);
        List<Product> allProducts = productRepository.findAll();
        List<Product> availableCatalogProducts = new ArrayList<>();
        
        for (Product prod : allProducts) {
            boolean alreadyInInventory = inventoryItems.stream()
                    .anyMatch(item -> item.getProduct().getId().equals(prod.getId()));
            if (!alreadyInInventory) {
                availableCatalogProducts.add(prod);
            }
        }
        return availableCatalogProducts;
    }

    public void adjustStock(Long inventoryId, int change) {
        Inventory item = inventoryRepository.findById(inventoryId).orElse(null);
        if (item != null) {
            int newStock = item.getStock() + change;
            item.setStock(Math.max(0, newStock));
            inventoryRepository.save(item);
        }
    }

    public void addExistingProductToInventory(Long centerId, Long productId, int stock) {
        Center center = centerRepository.findById(centerId).orElseThrow(() -> new IllegalArgumentException("Invalid center ID"));
        Product product = productRepository.findById(productId).orElse(null);
        if (product != null) {
            Optional<Inventory> existingInv = inventoryRepository.findByCenterIdAndProductId(centerId, productId);
            if (existingInv.isPresent()) {
                Inventory inv = existingInv.get();
                inv.setStock(inv.getStock() + stock);
                inventoryRepository.save(inv);
            } else {
                Inventory newInv = Inventory.builder()
                        .center(center)
                        .product(product)
                        .stock(stock)
                        .build();
                inventoryRepository.save(newInv);
            }
        }
    }

    public void addNewProductToInventory(Long centerId, String name, String description, String category, Double price, int stock) {
        Center center = centerRepository.findById(centerId).orElseThrow(() -> new IllegalArgumentException("Invalid center ID"));
        Product product = Product.builder()
                .name(name)
                .description(description)
                .category(category)
                .price(price)
                .build();
        productRepository.save(product);

        Inventory newInv = Inventory.builder()
                .center(center)
                .product(product)
                .stock(stock)
                .build();
        inventoryRepository.save(newInv);
    }

    public void deleteFromInventory(Long id) {
        inventoryRepository.deleteById(id);
    }
}
