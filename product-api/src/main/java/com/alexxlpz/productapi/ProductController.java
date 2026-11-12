package com.alexxlpz.productapi;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * <ul>
 *   <li>{@code GET /api/products/{gtin}}: los datos del producto, o 404 si nadie lo conoce.</li>
 *   <li>{@code GET /api/products/{gtin}/image}: la foto del envase.</li>
 *   <li>{@code POST /api/products/{gtin}} (multipart: name, brand, category, quantity, description, image, center): un
 *       centro aporta un producto. 201 si es nuevo, 200 si ya estaba (solo se rellenan los huecos). {@code center} es el
 *       número del centro en Cuquora y queda apuntado en el registro de aportaciones.</li>
 * </ul>
 */
@RestController
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/products/{gtin}")
    public ResponseEntity<?> get(@PathVariable String gtin) {
        return catalog.find(gtin)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(ProductJson.of(p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No conocemos ese producto.")));
    }

    @GetMapping("/api/products/{gtin}/image")
    public ResponseEntity<byte[]> image(@PathVariable String gtin) {
        return catalog.image(gtin)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                        .body(img.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/api/products/{gtin}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductJson> contribute(@PathVariable String gtin,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String brand,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String quantity,
                                                  @RequestParam(required = false) String description,
                                                  @RequestParam(required = false) MultipartFile image,
                                                  @RequestParam(required = false) String center) throws IOException {
        byte[] data = image == null || image.isEmpty() ? null : image.getBytes();
        CatalogService.Saved saved = catalog.contribute(gtin,
                new Contribution(name, brand, category, quantity, description, data, centerId(center)));
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ProductJson.of(saved.product()));
    }

    private static Long centerId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long id = Long.parseLong(raw.strip());
            if (id > 0) return id;
        } catch (NumberFormatException e) {
            // cae al error de abajo
        }
        throw new InvalidRequestException("El centro tiene que ser un número.");
    }
}
