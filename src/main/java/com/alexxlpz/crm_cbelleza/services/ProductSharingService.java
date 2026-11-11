package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.ProductPhoto;
import com.alexxlpz.crm_cbelleza.productdata.ProductContribution;
import com.alexxlpz.crm_cbelleza.productdata.ProductDatabase;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comparte con la API de productos los productos con código de barras que dan de alta los centros, para que el
 * siguiente centro que lo escanee lo encuentre relleno: nombre, categoría, descripción y foto. El precio y el stock
 * son del centro y no salen de Cuquora. Va en segundo plano y cuando el alta ya está guardada: si la API tarda o está
 * caída, el centro no lo nota.
 * <p>
 * Lo que llega a la API lo ven todos los centros y no se puede cambiar desde Cuquora, así que solo comparten los
 * centros ya publicados (revisados por el administrador) y con un tope diario: un alta sin revisar no puede llenar
 * la base de datos de nombres o fotos falsos.
 */
@Service
public class ProductSharingService {

    private static final Logger log = LoggerFactory.getLogger(ProductSharingService.class);
    /** Productos que puede compartir un centro al día (un alta con foto cuenta dos: datos y foto). */
    static final int MAX_PER_CENTER_PER_DAY = 60;

    private final ProductRepository products;
    private final ProductPhotoRepository photos;
    private final CenterRepository centers;
    private final ProductDatabase database;
    private final Clock clock;
    private final Map<Long, DailyCount> sharedToday = new ConcurrentHashMap<>();

    public ProductSharingService(ProductRepository products, ProductPhotoRepository photos, CenterRepository centers,
                                 ProductDatabase database, Clock clock) {
        this.products = products;
        this.photos = photos;
        this.centers = centers;
        this.database = database;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onProductShared(ProductSharedEvent event) {
        try {
            contributionOf(event.productId()).ifPresent(c -> {
                if (withinDailyLimit(c.centerId())) {
                    database.contribute(c.barcode(), c.product());
                } else {
                    log.warn("El centro {} ha llegado al tope de {} productos compartidos hoy", c.centerId(), MAX_PER_CENTER_PER_DAY);
                }
            });
        } catch (RuntimeException e) {
            log.warn("No se ha podido compartir el producto {}: {}", event.productId(), e.getMessage());
        }
    }

    /**
     * Lo que se comparte de un producto: vacío si no tiene código, es del catálogo general o su centro aún no está
     * publicado.
     */
    public Optional<Shared> contributionOf(Long productId) {
        return products.findById(productId)
                .filter(p -> p.getBarcode() != null && p.getCenterId() != null)
                .filter(p -> centers.findById(p.getCenterId()).map(Center::isPublished).orElse(false))
                .map(p -> new Shared(p.getCenterId(), p.getBarcode(), new ProductContribution(
                        p.getName().length() > 200 ? p.getName().substring(0, 200).strip() : p.getName(), p.getCategory(),
                        p.getDescription() == null || p.getDescription().isBlank() ? null : p.getDescription().strip(),
                        photos.findById(p.getId()).map(ProductPhoto::getData).orElse(null))));
    }

    boolean withinDailyLimit(Long centerId) {
        LocalDate today = LocalDate.now(clock);
        DailyCount count = sharedToday.compute(centerId, (id, c) ->
                c == null || !c.day().equals(today) ? new DailyCount(today, 1) : new DailyCount(today, c.count() + 1));
        return count.count() <= MAX_PER_CENTER_PER_DAY;
    }

    public record Shared(Long centerId, String barcode, ProductContribution product) {
    }

    private record DailyCount(LocalDate day, int count) {
    }
}
