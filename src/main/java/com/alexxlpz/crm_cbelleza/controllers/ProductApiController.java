package com.alexxlpz.crm_cbelleza.controllers;

import com.alexxlpz.crm_cbelleza.entities.ProductPhoto;
import com.alexxlpz.crm_cbelleza.exceptions.ApiEndpoint;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.CenterOwnerOnly;
import com.alexxlpz.crm_cbelleza.services.ProductLookupService;
import com.alexxlpz.crm_cbelleza.services.ProductPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Productos del inventario: búsqueda por código de barras al darlos de alta (escáner o lector) y sus fotos.
 */
@RestController
@ApiEndpoint
public class ProductApiController {

    private final ProductLookupService lookup;
    private final ProductPhotoService photos;

    public ProductApiController(ProductLookupService lookup, ProductPhotoService photos) {
        this.lookup = lookup;
        this.photos = photos;
    }

    /** Qué se sabe del producto con ese código: si ya está en el inventario, en el catálogo o en Open Beauty Facts. */
    @CenterOwnerOnly
    @GetMapping("/worker/inventory/barcode")
    public ProductLookupService.Result find(@AuthenticationPrincipal AppUserDetails worker, @RequestParam String code) {
        return lookup.lookup(worker.getCenterId(), code);
    }

    /** Vista previa de la foto encontrada por el código, antes de guardar el producto. */
    @CenterOwnerOnly
    @GetMapping("/worker/inventory/barcode/photo")
    public ResponseEntity<byte[]> foundPhoto(@RequestParam String code) {
        byte[] jpeg = lookup.foundPhoto(code).orElseThrow(() -> new ResourceNotFoundException("No hay foto para ese código."));
        return image("image/jpeg", jpeg, CacheControl.maxAge(Duration.ofHours(1)).cachePrivate());
    }

    /** La URL lleva la versión (?v=), así que el navegador puede guardarla sin volver a pedirla. */
    @GetMapping("/worker/products/{id}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AppUserDetails worker, @PathVariable Long id) {
        ProductPhoto photo = photos.photoOf(worker.getCenterId(), id);
        return image(photo.getContentType(), photo.getData(), CacheControl.maxAge(Duration.ofDays(365)).cachePrivate());
    }

    private static ResponseEntity<byte[]> image(String contentType, byte[] data, CacheControl cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(data);
    }
}
