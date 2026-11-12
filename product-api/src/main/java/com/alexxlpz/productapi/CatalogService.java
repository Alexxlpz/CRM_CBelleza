package com.alexxlpz.productapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los productos por código de barras. Si alguien busca uno que no está y Open Beauty Facts lo conoce, se copia
 * aquí (datos y foto) para no volver a preguntar. Lo que aportan los centros solo rellena lo que falta: nunca
 * pisa el nombre ni una foto que ya hay, así un centro no puede estropear lo que ven los demás.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    /** Un código que Open Beauty Facts no conoce no se le vuelve a preguntar en este rato (es su límite, no el nuestro). */
    static final long MISS_SECONDS = 1800;
    private static final int MISSES_SIZE = 5000;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductContributionRepository contributions;
    private final OpenBeautyFacts openBeautyFacts;
    private final boolean useOpenBeautyFacts;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Map<String, Instant> misses = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > MISSES_SIZE;
        }
    };

    public CatalogService(ProductRepository products, ProductImageRepository images,
                          ProductContributionRepository contributions, OpenBeautyFacts openBeautyFacts,
                          @Value("${product-api.open-beauty-facts:true}") boolean useOpenBeautyFacts,
                          TransactionTemplate tx, Clock clock) {
        this.products = products;
        this.images = images;
        this.contributions = contributions;
        this.openBeautyFacts = openBeautyFacts;
        this.useOpenBeautyFacts = useOpenBeautyFacts;
        this.tx = tx;
        this.clock = clock;
    }

    /** El producto con ese código; si no está, lo intenta traer de Open Beauty Facts. */
    public Optional<Product> find(String raw) {
        String gtin = Gtin.normalize(raw);
        Optional<Product> stored = products.findById(gtin);
        if (stored.isPresent() || !useOpenBeautyFacts || recentlyMissed(gtin)) return stored;
        // Las llamadas a fuera van sin transacción abierta: pueden tardar unos segundos
        Optional<ImportedProduct> imported = openBeautyFacts.find(gtin);
        if (imported.isEmpty()) {
            synchronized (misses) {
                misses.put(gtin, clock.instant().plusSeconds(MISS_SECONDS));
            }
            return Optional.empty();
        }
        ImportedProduct p = imported.get();
        Optional<byte[]> image = Optional.ofNullable(p.imageUrl()).flatMap(openBeautyFacts::image).flatMap(Images::shrink);
        try {
            return Optional.of(tx.execute(status -> {
                Product product = new Product(gtin, p.name(), Source.OPEN_BEAUTY_FACTS, now());
                product.setBrand(p.brand());
                product.setCategory(Categories.valid(p.category()));
                product.setQuantity(p.quantity());
                Product saved = products.saveAndFlush(product);
                image.ifPresent(data -> storeImage(saved, data));
                return saved;
            }));
        } catch (DataIntegrityViolationException e) {
            // Otra petición lo ha guardado a la vez: vale el suyo
            return products.findById(gtin);
        }
    }

    private boolean recentlyMissed(String gtin) {
        synchronized (misses) {
            Instant until = misses.get(gtin);
            return until != null && until.isAfter(clock.instant());
        }
    }

    public Optional<ProductImage> image(String raw) {
        return images.findById(Gtin.normalize(raw));
    }

    /** Guarda lo que aporta un centro. Devuelve el producto y si es nuevo. */
    public Saved contribute(String raw, Contribution c) {
        String gtin = Gtin.normalize(raw);
        synchronized (misses) {
            misses.remove(gtin);
        }
        String name = required(c.name(), "nombre", 200);
        String brand = optional(c.brand(), "marca", 100);
        String quantity = optional(c.quantity(), "formato", 40);
        String description = optional(c.description(), "texto de la descripción", 1000);
        String category = Categories.valid(c.category());
        byte[] image = c.image() == null || c.image().length == 0 ? null : Images.shrink(c.image())
                .orElseThrow(() -> new InvalidRequestException("La foto tiene que ser un JPEG o PNG de 5 MB como mucho."));
        try {
            return save(gtin, name, brand, category, quantity, description, image, c.centerId());
        } catch (DataIntegrityViolationException e) {
            // Dos centros a la vez con el mismo código: el segundo solo rellena huecos
            return save(gtin, name, brand, category, quantity, description, image, c.centerId());
        }
    }

    private Saved save(String gtin, String name, String brand, String category, String quantity, String description,
                       byte[] image, Long centerId) {
        return tx.execute(status -> {
            Optional<Product> existing = products.findById(gtin);
            Product product = existing.orElseGet(() -> new Product(gtin, name, Source.CUQUORA, now()));
            List<String> filled = new ArrayList<>();
            if (existing.isEmpty()) filled.add("nombre");
            if (product.getBrand() == null && brand != null) { product.setBrand(brand); filled.add("marca"); }
            if (product.getCategory() == null && category != null) { product.setCategory(category); filled.add("categoria"); }
            if (product.getQuantity() == null && quantity != null) { product.setQuantity(quantity); filled.add("formato"); }
            if (product.getDescription() == null && description != null) { product.setDescription(description); filled.add("descripcion"); }
            if (!filled.isEmpty()) product.setUpdatedAt(now());
            product = products.saveAndFlush(product);
            if (image != null && !images.existsById(gtin)) {
                storeImage(product, image);
                filled.add("foto");
            }
            // Solo se apunta si de verdad ha cambiado algo: así el registro dice de dónde salió cada dato
            if (!filled.isEmpty()) {
                contributions.save(new ProductContribution(gtin, centerId, existing.isEmpty(), String.join(",", filled), now()));
            }
            if (existing.isEmpty()) log.info("Nuevo producto aportado por {}: {}", centerId == null ? "un centro sin identificar" : "el centro " + centerId, gtin);
            return new Saved(product, existing.isEmpty());
        });
    }

    public record Saved(Product product, boolean created) {
    }

    private void storeImage(Product product, byte[] data) {
        String type = Images.contentType(data).orElseThrow();
        images.save(new ProductImage(product.getGtin(), type, data));
        product.setImageUpdatedAt(now());
        product.setUpdatedAt(now());
        products.save(product);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String required(String value, String field, int max) {
        String text = optional(value, field, max);
        if (text == null) throw new InvalidRequestException("Falta el " + field + " del producto.");
        return text;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.strip();
        if (text.length() > max) {
            throw new InvalidRequestException("El " + field + " no puede pasar de " + max + " caracteres.");
        }
        return text;
    }
}
