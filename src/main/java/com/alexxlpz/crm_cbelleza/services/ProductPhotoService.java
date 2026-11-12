package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.ProductPhoto;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.productdata.ProductOrigin;
import com.alexxlpz.crm_cbelleza.repositories.ProductPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Fotos de los productos. Solo se cambia la de un producto creado por el propio centro (los del catálogo general
 * son de todos); se ven desde cualquier centro que pueda usar ese producto.
 */
@Service
@Transactional
public class ProductPhotoService {

    public static final int MAX_UPLOAD_MB = PhotoImages.MAX_UPLOAD_MB;
    static final int SIDE = ProductLookupService.PHOTO_SIDE;
    static final int MIN_SIDE = 160;

    private final ProductRepository products;
    private final ProductPhotoRepository photos;
    private final ProductLookupService lookup;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ProductPhotoService(ProductRepository products, ProductPhotoRepository photos, ProductLookupService lookup,
                               Clock clock, ApplicationEventPublisher events) {
        this.products = products;
        this.photos = photos;
        this.lookup = lookup;
        this.clock = clock;
        this.events = events;
    }

    /** Pone la foto subida por el centro. */
    public void save(Long centerId, Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Elige una foto para subirla.");
        }
        store(ownProduct(centerId, productId), PhotoImages.fit(PhotoImages.bytesOf(file), SIDE, MIN_SIDE, MIN_SIDE, false), null);
    }

    /** Pone la foto que tiene la base de datos pública para el código del producto. Devuelve si la ha encontrado. */
    public boolean saveFound(Long centerId, Long productId) {
        Product product = ownProduct(centerId, productId);
        if (product.getBarcode() == null) {
            return false;
        }
        Optional<ProductLookupService.FoundPhoto> found = lookup.foundPhotoWithOrigin(product.getBarcode());
        // Las de Open Beauty Facts se guardan marcadas: su licencia pide decir de dónde son
        found.ifPresent(photo -> store(product, photo.jpeg(),
                photo.origin() == ProductOrigin.OPEN_BEAUTY_FACTS ? Product.OPEN_BEAUTY_FACTS : null));
        return found.isPresent();
    }

    public void remove(Long centerId, Long productId) {
        Product product = ownProduct(centerId, productId);
        photos.findById(product.getId()).ifPresent(photos::delete);
        product.setPhotoUpdatedAt(null);
        product.setPhotoSource(null);
    }

    @Transactional(readOnly = true)
    public ProductPhoto photoOf(Long centerId, Long productId) {
        return products.findById(productId)
                .filter(p -> p.getCenterId() == null || p.getCenterId().equals(centerId))
                .flatMap(p -> photos.findById(p.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
    }

    private Product ownProduct(Long centerId, Long productId) {
        Product product = products.findById(productId)
                .filter(p -> p.getCenterId() == null || p.getCenterId().equals(centerId))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));
        if (product.getCenterId() == null) {
            throw new BusinessRuleException("Es un producto del catálogo general de Cuquora: su foto no se puede cambiar desde un centro.");
        }
        return product;
    }

    private void store(Product product, byte[] jpeg, String source) {
        photos.save(new ProductPhoto(product.getId(), "image/jpeg", jpeg));
        product.setPhotoSource(source);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        // La URL lleva la hora en segundos: dos fotos en el mismo segundo tendrían la misma URL en caché
        if (product.getPhotoUpdatedAt() != null && !now.isAfter(product.getPhotoUpdatedAt())) {
            now = product.getPhotoUpdatedAt().plusSeconds(1);
        }
        product.setPhotoUpdatedAt(now);
        products.save(product);
        if (product.getBarcode() != null) {
            events.publishEvent(new ProductSharedEvent(product.getId()));
        }
    }
}
