package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.entities.Inventory;
import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.productdata.ExternalProduct;
import com.alexxlpz.crm_cbelleza.productdata.ProductDatabase;
import com.alexxlpz.crm_cbelleza.productdata.ProductOrigin;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca un producto por su código de barras al darlo de alta en el inventario: primero en Cuquora (lo que el centro
 * ya tiene o puede elegir del catálogo) y, si no está, en la base de datos de productos ({@link ProductDatabase}: la
 * API propia, que sabe lo que han dado de alta otros centros, u Open Beauty Facts) para rellenar nombre, marca,
 * categoría y foto. Las respuestas de fuera se guardan un rato en memoria para no repetir
 * la consulta al pedir la foto o al guardar.
 */
@Service
@Transactional(readOnly = true)
public class ProductLookupService {

    /** Foto encontrada: se reduce como las que se suben (lado largo) y se exige un mínimo para que no salga borrosa. */
    static final int PHOTO_SIDE = 800;
    static final int PHOTO_MIN_SIDE = 80;
    private static final int CACHE_SIZE = 200;
    /**
     * Búsquedas fuera de Cuquora por centro y hora. Sobra para dar de alta un pedido grande a mano, y evita que un
     * centro (o un script con su sesión) haga que Open Beauty Facts bloquee al servidor por exceso de peticiones.
     */
    static final int MAX_EXTERNAL_PER_CENTER_PER_HOUR = 150;
    private static final long CACHE_SECONDS = 3600;
    /** GS1 Digital Link: algunos QR de envase llevan el código dentro de una dirección (…/01/08412345678905/…). */
    private static final Pattern DIGITAL_LINK = Pattern.compile("/01/(\\d{8,14})(?:[/?#]|$)");

    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final ProductDatabase database;
    private final Clock clock;
    private final Map<Long, Window> searches = new ConcurrentHashMap<>();
    private final Map<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public ProductLookupService(ProductRepository products, InventoryRepository inventory, ProductDatabase database, Clock clock) {
        this.products = products;
        this.inventory = inventory;
        this.database = database;
        this.clock = clock;
    }

    /** Qué se ha encontrado con el código. */
    public enum Source {
        /** Ya está en el inventario del centro: basta con sumar stock. */
        INVENTORY,
        /** Está en Cuquora (catálogo general o un producto que el centro tuvo): se elige del catálogo. */
        CATALOG,
        /** No está en Cuquora pero sí en la base de datos pública: se rellena el formulario de producto nuevo. */
        FOUND,
        /** Nadie lo conoce: se rellena a mano (el código se guarda igual). */
        NOT_FOUND
    }

    /** @param origin con FOUND, quién lo conocía: otro centro de Cuquora u Open Beauty Facts */
    public record Result(Source source, String barcode, Long productId, Long inventoryId, String name,
                         String description, String category, boolean photo, ProductOrigin origin) {
    }

    public Result lookup(Long centerId, String raw) {
        String barcode = normalize(raw);
        Optional<Product> known = products.findUsableByBarcode(barcode, centerId).stream().findFirst();
        if (known.isPresent()) {
            Product product = known.get();
            Optional<Inventory> item = inventory.findByCenterIdAndProductId(centerId, product.getId());
            return new Result(item.isPresent() ? Source.INVENTORY : Source.CATALOG, barcode, product.getId(),
                    item.map(Inventory::getId).orElse(null), product.getName(), product.getDescription(),
                    product.getCategory(), product.getPhotoUrl() != null, null);
        }
        if (!isCached(barcode)) {
            checkLimit(centerId);
        }
        return external(barcode)
                .map(p -> new Result(Source.FOUND, barcode, null, null, nameOf(p), descriptionOf(p),
                        p.category(), p.imageUrl() != null, p.origin()))
                .orElseGet(() -> new Result(Source.NOT_FOUND, barcode, null, null, null, null, null, false, null));
    }

    /** Foto que tiene la base de datos pública para ese código, ya reducida a JPEG; vacía si no hay o no sirve. */
    public Optional<byte[]> foundPhoto(String raw) {
        return foundPhotoWithOrigin(raw).map(FoundPhoto::jpeg);
    }

    /** Como {@link #foundPhoto}, y además de dónde salió (para el crédito de las de Open Beauty Facts). */
    public Optional<FoundPhoto> foundPhotoWithOrigin(String raw) {
        return external(normalize(raw))
                .filter(p -> p.imageUrl() != null)
                .flatMap(p -> database.image(p.imageUrl()).flatMap(bytes -> {
                    try {
                        return Optional.of(new FoundPhoto(
                                PhotoImages.fit(bytes, PHOTO_SIDE, PHOTO_MIN_SIDE, PHOTO_MIN_SIDE, false), p.origin()));
                    } catch (BusinessRuleException e) {
                        return Optional.empty(); // formato raro (WebP) o demasiado pequeña: mejor sin foto
                    }
                }));
    }

    /** Una foto encontrada por el código y su procedencia. */
    public record FoundPhoto(byte[] jpeg, ProductOrigin origin) {
    }

    /**
     * Deja el código en dígitos y comprueba que es un EAN-8, UPC-A, EAN-13 o GTIN-14 con su dígito de control.
     * Un UPC-A (12 dígitos) pasa a EAN-13 con un 0 delante, como lo guardan las bases de datos públicas.
     */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.strip();
        Matcher link = DIGITAL_LINK.matcher(value);
        if (link.find()) {
            value = link.group(1);
        }
        if (!value.matches("[0-9 -]+")) {
            throw new BusinessRuleException("Eso no es un código de barras de producto. Si has escaneado un QR, "
                    + "suele llevar a una web y no trae los datos: escanea el código de barras de la etiqueta.");
        }
        String digits = value.replaceAll("[ -]", "");
        if (digits.length() == 14 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 12) {
            digits = "0" + digits;
        }
        if (!(digits.length() == 8 || digits.length() == 13 || digits.length() == 14) || !validCheckDigit(digits)) {
            throw new BusinessRuleException("El código " + digits + " no es válido. Vuelve a escanearlo o revisa los números.");
        }
        return digits;
    }

    static boolean validCheckDigit(String digits) {
        int sum = 0;
        for (int i = digits.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (digits.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == digits.charAt(digits.length() - 1) - '0';
    }

    /** La primera marca: Open Beauty Facts pone detrás el grupo o el fabricante («Marca, Grupo»). */
    private static String brandOf(ExternalProduct p) {
        if (p.brand() == null) return null;
        String first = p.brand().split(",")[0].strip();
        return first.isEmpty() ? null : first;
    }

    private static String nameOf(ExternalProduct p) {
        String brand = brandOf(p);
        if (p.name() == null) return brand;
        if (brand == null || p.name().toLowerCase(Locale.ROOT).contains(brand.toLowerCase(Locale.ROOT))) {
            return p.name();
        }
        return brand + " " + p.name();
    }

    private static String descriptionOf(ExternalProduct p) {
        if (p.description() != null) return p.description();
        StringBuilder text = new StringBuilder();
        String brand = brandOf(p);
        if (brand != null) text.append("Marca: ").append(brand).append('.');
        if (p.quantity() != null) text.append(text.isEmpty() ? "" : " ").append("Formato: ").append(p.quantity()).append('.');
        return text.isEmpty() ? null : text.toString();
    }

    private boolean isCached(String barcode) {
        synchronized (cache) {
            Cached hit = cache.get(barcode);
            return hit != null && hit.until().isAfter(clock.instant());
        }
    }

    private void checkLimit(Long centerId) {
        Instant now = clock.instant();
        Window window = searches.compute(centerId, (id, w) -> w == null || !w.until().isAfter(now)
                ? new Window(now.plusSeconds(3600), 1) : new Window(w.until(), w.count() + 1));
        if (window.count() > MAX_EXTERNAL_PER_CENTER_PER_HOUR) {
            throw new BusinessRuleException("Has buscado muchos códigos seguidos. Espera un rato o rellena el producto a mano.");
        }
    }

    private record Window(Instant until, int count) {
    }

    private Optional<ExternalProduct> external(String barcode) {
        Instant now = clock.instant();
        synchronized (cache) {
            Cached hit = cache.get(barcode);
            if (hit != null && hit.until().isAfter(now)) {
                return hit.product();
            }
        }
        Optional<ExternalProduct> found = database.find(barcode);
        if (found.isPresent()) {
            // Lo que no encuentra no se guarda: puede que el servicio estuviera caído y a la próxima sí responda
            synchronized (cache) {
                cache.put(barcode, new Cached(found, now.plusSeconds(CACHE_SECONDS)));
            }
        }
        return found;
    }

    private record Cached(Optional<ExternalProduct> product, Instant until) {
    }
}
