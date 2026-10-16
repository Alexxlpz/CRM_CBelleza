package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.ProductSale;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.ProductSaleForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductSaleRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Productos que el centro vende a sus clientes: restan stock y, si está activada, suman puntos. */
@Service
@Transactional
public class ProductSaleService {

    public static final int MAX_QUANTITY = 99;

    private final ProductSaleRepository sales;
    private final InventoryRepository inventories;
    private final CenterRepository centers;
    private final UserRepository users;
    private final ClientCardService clientCards;
    private final LoyaltyService loyalty;
    private final Clock clock;

    public ProductSaleService(ProductSaleRepository sales, InventoryRepository inventories, CenterRepository centers,
                              UserRepository users, ClientCardService clientCards, LoyaltyService loyalty, Clock clock) {
        this.sales = sales;
        this.inventories = inventories;
        this.centers = centers;
        this.users = users;
        this.clientCards = clientCards;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductSale> salesOf(Long centerId, Long clientId) {
        return sales.findByCenterIdAndClientIdOrderBySoldAtDesc(centerId, clientId);
    }

    /** Registra la venta y resta las unidades del inventario. Devuelve la venta y el stock que queda. */
    public SaleResult sell(Long centerId, Long clientId, ProductSaleForm form, Long workerId) {
        if (clientId == null) {
            throw new BusinessRuleException("Elige el cliente que se lleva el producto.");
        }
        User client = clientCards.requireClientOfCenter(centerId, clientId);
        if (form.inventoryId() == null) {
            throw new BusinessRuleException("Elige el producto que se lleva el cliente.");
        }
        int quantity = form.quantity() == null ? 0 : form.quantity();
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessRuleException("La cantidad tiene que estar entre 1 y " + MAX_QUANTITY + ".");
        }
        Inventory item = inventories.findForUpdate(form.inventoryId(), centerId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe en el inventario de tu centro."));
        if (item.getStock() < quantity) {
            throw new BusinessRuleException(item.getStock() == 0
                    ? "No queda " + item.getProduct().getName() + " en el inventario."
                    : "Solo quedan " + item.getStock() + " unidades de " + item.getProduct().getName() + ".");
        }
        item.setStock(item.getStock() - quantity);
        inventories.save(item);

        Center center = centers.findById(centerId).orElseThrow();
        ProductSale sale = sales.save(ProductSale.builder()
                .center(center)
                .client(client)
                .product(item.getProduct())
                .worker(workerId != null ? users.getReferenceById(workerId) : null)
                .quantity(quantity)
                .unitPrice(item.getProduct().getPrice())
                .soldAt(CenterClock.now(clock, center))
                .build());
        loyalty.awardSale(sale, workerId);
        return new SaleResult(sale, item.getStock());
    }

    /** Anula una venta (por error al registrarla): devuelve las unidades al inventario y quita sus puntos. */
    public ProductSale cancel(Long centerId, Long clientId, Long saleId) {
        clientCards.requireClientOfCenter(centerId, clientId);
        ProductSale sale = sales.findByIdAndCenterIdAndClientId(saleId, centerId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("La venta no existe."));
        Inventory item = inventories.findByCenterIdAndProductId(centerId, sale.getProduct().getId())
                .orElseGet(() -> Inventory.builder()
                        .center(sale.getCenter())
                        .product(sale.getProduct())
                        .stock(0)
                        .build());
        item.setStock(item.getStock() + sale.getQuantity());
        inventories.save(item);
        loyalty.revokeSale(sale.getId());
        sales.delete(sale);
        return sale;
    }

    public record SaleResult(ProductSale sale, int stockLeft) {

        /** Aviso tras vender, igual desde la ficha del cliente que desde el inventario. */
        public String message() {
            return "Venta guardada: " + sale.getQuantity() + " × " + sale.getProduct().getName() + ". " + stockText();
        }

        private String stockText() {
            if (stockLeft == 0) {
                return "El producto se ha agotado.";
            }
            String units = stockLeft == 1 ? "Queda 1 unidad" : "Quedan " + stockLeft + " unidades";
            return stockLeft <= InventoryService.LOW_STOCK_THRESHOLD ? units + ": stock bajo." : units + ".";
        }
    }
}
