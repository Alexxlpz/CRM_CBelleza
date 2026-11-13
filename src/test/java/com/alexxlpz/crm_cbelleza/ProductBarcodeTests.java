package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Product;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.productdata.ExternalProduct;
import com.alexxlpz.crm_cbelleza.productdata.ProductDatabase;
import com.alexxlpz.crm_cbelleza.productdata.ProductOrigin;
import com.alexxlpz.crm_cbelleza.services.ProductSharedEvent;
import com.alexxlpz.crm_cbelleza.services.ProductSharingService;
import com.alexxlpz.crm_cbelleza.repositories.InventoryRepository;
import com.alexxlpz.crm_cbelleza.repositories.ProductRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ProductLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto y código de barras de los productos: búsqueda en Cuquora y en Open Beauty Facts al darlos de alta. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ProductBarcodeTests {

    /** EAN-13 de ejemplo con su dígito de control correcto. */
    private static final String EAN = "4006381333931";
    private static final String IMAGE = "https://images.openbeautyfacts.org/images/products/400/638/133/3931/front_es.jpg";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private InventoryRepository inventory;

    @Autowired
    private ProductSharingService sharing;
    @Autowired
    private ProductLookupService lookup;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private ProductDatabase database;

    private User carlos;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        when(database.find(anyString())).thenReturn(Optional.empty());
        when(database.find(EAN)).thenReturn(Optional.of(new ExternalProduct(
                "Sérum Hidratante Ácido Hialurónico", "Lumière, Grupo Belleza", "30 ml", null, "Rostro", IMAGE,
                ProductOrigin.OPEN_BEAUTY_FACTS)));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(240, 230, 220));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private Product productWithBarcode(String barcode) {
        return products.findUsableByBarcode(barcode, carlos.getCenter().getId()).stream().findFirst().orElseThrow();
    }

    @Test
    void barcodesAreCleanedAndChecked() {
        assertEquals(EAN, ProductLookupService.normalize(" 4006381 333931 "));
        assertEquals("0036000291452", ProductLookupService.normalize("036000291452"), "UPC-A pasa a EAN-13");
        assertEquals(EAN, ProductLookupService.normalize("https://id.gs1.org/01/0" + EAN + "/10/LOTE1"), "QR GS1 Digital Link");
        assertThrows(BusinessRuleException.class, () -> ProductLookupService.normalize("4006381333932"), "dígito de control");
        BusinessRuleException qr = assertThrows(BusinessRuleException.class,
                () -> ProductLookupService.normalize("https://marca.example/promo"));
        assertTrue(qr.getMessage().contains("QR"));
    }

    @Test
    void scanningANewProductFillsTheFormAndKeepsItsPhoto() throws Exception {
        when(database.image(IMAGE)).thenReturn(Optional.of(png(600, 900)));

        mvc.perform(get("/worker/inventory/barcode").param("code", EAN).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FOUND"))
                .andExpect(jsonPath("$.name").value("Lumière Sérum Hidratante Ácido Hialurónico"))
                .andExpect(jsonPath("$.description").value("Marca: Lumière. Formato: 30 ml."))
                .andExpect(jsonPath("$.category").value("Rostro"))
                .andExpect(jsonPath("$.photo").value(true));
        mvc.perform(get("/worker/inventory/barcode/photo").param("code", EAN).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));

        mvc.perform(multipart("/worker/inventory/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Lumière Sérum Hidratante").param("category", "Rostro")
                        .param("price", "24.90").param("stock", "4").param("barcode", EAN).param("useFoundPhoto", "true"))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));

        Product created = productWithBarcode(EAN);
        assertEquals(carlos.getCenter().getId(), created.getCenterId());
        assertNotNull(created.getPhotoUrl());
        byte[] jpeg = mvc.perform(get(created.getPhotoUrl()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(800, ImageIO.read(new ByteArrayInputStream(jpeg)).getHeight(), "se reduce a 800 px por el lado largo");
        // La consulta de fuera se ha hecho una vez: la foto y el guardado tiran de la memoria
        verify(database, atMost(1)).find(EAN);

        // Escanearlo otra vez dice que ya está en el inventario
        mvc.perform(get("/worker/inventory/barcode").param("code", EAN).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(jsonPath("$.source").value("INVENTORY"))
                .andExpect(jsonPath("$.name").value("Lumière Sérum Hidratante"));
        // Y no se puede crear otro con el mismo código
        mvc.perform(multipart("/worker/inventory/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Copia").param("price", "10").param("barcode", EAN))
                .andExpect(flash().attribute("errorMessage", containsString("Ya tienes «Lumière Sérum Hidratante»")));

        // Otro centro no ve el producto ni su foto: para él es un producto nuevo
        mvc.perform(get("/worker/inventory/barcode").param("code", EAN).with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(jsonPath("$.source").value("FOUND"));
        mvc.perform(get(created.getPhotoUrl()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());

        // El inventario pinta la foto en vez de la imagen genérica, con el crédito que pide su licencia
        assertTrue(created.isPhotoFromOpenBeautyFacts());
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString(created.getPhotoUrl().replace("&", "&amp;"))))
                .andExpect(content().string(containsString("https://world.openbeautyfacts.org/product/" + EAN)))
                .andExpect(content().string(containsString("data-barcode-box")));
        // La foto de Open Beauty Facts no se comparte con la API como si fuera del centro (el resto sí)
        ProductSharingService.Shared shared = sharing.contributionOf(created.getId()).orElseThrow();
        assertNull(shared.product().photo());
        assertEquals("Lumière Sérum Hidratante", shared.product().name());
    }

    @Test
    void ownPhotoWinsAndCanBeChangedFromTheDetail() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "crema.png", "image/png", png(500, 500));
        mvc.perform(multipart("/worker/inventory/add").file(photo).with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Crema de manos casera").param("category", "Cuerpo")
                        .param("price", "9.5").param("stock", "2").param("barcode", EAN).param("useFoundPhoto", "true"))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));
        Product created = productWithBarcode(EAN);
        assertNotNull(created.getPhotoUrl());
        assertFalse(created.isPhotoFromOpenBeautyFacts(), "la foto es del centro: sin crédito");
        verify(database, times(0)).image(anyString());

        Long inventoryId = inventory.findByCenterIdAndProductId(carlos.getCenter().getId(), created.getId()).orElseThrow().getId();
        mvc.perform(multipart("/worker/inventory/" + inventoryId + "/photo/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", "Foto del producto quitada."));
        assertNull(products.findById(created.getId()).orElseThrow().getPhotoUrl());

        mvc.perform(multipart("/worker/inventory/" + inventoryId + "/photo").file(photo).with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", "Foto del producto guardada."));
        assertNotNull(products.findById(created.getId()).orElseThrow().getPhotoUrl());
    }

    @Test
    void theCreditGoesAwayWhenTheCenterPutsItsOwnPhoto() throws Exception {
        when(database.image(IMAGE)).thenReturn(Optional.of(png(600, 900)));
        mvc.perform(multipart("/worker/inventory/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Sérum").param("category", "Rostro")
                        .param("price", "20").param("stock", "1").param("barcode", EAN).param("useFoundPhoto", "true"))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));
        Product created = productWithBarcode(EAN);
        assertTrue(created.isPhotoFromOpenBeautyFacts());

        Long inventoryId = inventory.findByCenterIdAndProductId(carlos.getCenter().getId(), created.getId()).orElseThrow().getId();
        mvc.perform(multipart("/worker/inventory/" + inventoryId + "/photo")
                        .file(new MockMultipartFile("photo", "mia.png", "image/png", png(500, 500))).with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("successMessage", "Foto del producto guardada."));
        Product updated = products.findById(created.getId()).orElseThrow();
        assertFalse(updated.isPhotoFromOpenBeautyFacts());
        assertNotNull(sharing.contributionOf(created.getId()).orElseThrow().product().photo(), "la suya sí se comparte");
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("openbeautyfacts.org/product/" + EAN))));
    }

    @Test
    void unknownCodeIsSavedForTheNextTime() throws Exception {
        mvc.perform(get("/worker/inventory/barcode").param("code", "0036000291452").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(jsonPath("$.source").value("NOT_FOUND"))
                .andExpect(jsonPath("$.barcode").value("0036000291452"));
        mvc.perform(get("/worker/inventory/barcode").param("code", "no-es-un-codigo").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("no es un código de barras")));
    }

    @Test
    void aProductAnotherCenterAddedSaysSo() throws Exception {
        when(database.find("8436595340863")).thenReturn(Optional.of(new ExternalProduct(
                "Alissi Brontë Sublime", null, null, "Champú de brillo para cabello teñido", "Cabello", null,
                ProductOrigin.CUQUORA)));
        mvc.perform(get("/worker/inventory/barcode").param("code", "8436595340863").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(jsonPath("$.source").value("FOUND"))
                .andExpect(jsonPath("$.origin").value("CUQUORA"))
                .andExpect(jsonPath("$.name").value("Alissi Brontë Sublime"))
                .andExpect(jsonPath("$.category").value("Cabello"))
                .andExpect(jsonPath("$.description").value("Champú de brillo para cabello teñido"));
    }

    @Test
    void newProductsWithACodeAreSharedWithoutPriceOrStock() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "sublime.png", "image/png", png(400, 400));
        mvc.perform(multipart("/worker/inventory/add").file(photo).with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Alissi Brontë Sublime").param("category", "Cabello")
                        .param("description", "Lo usamos en el ritual de brillo").param("price", "31").param("stock", "3")
                        .param("barcode", "8436595340863"))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));
        Product created = productWithBarcode("8436595340863");

        // Se avisa al crearlo y al ponerle la foto; se manda cuando la transacción ya está guardada
        assertEquals(2, events.stream(ProductSharedEvent.class).filter(e -> e.productId().equals(created.getId())).count());
        ProductSharingService.Shared shared = sharing.contributionOf(created.getId()).orElseThrow();
        assertEquals("8436595340863", shared.barcode());
        assertEquals("Alissi Brontë Sublime", shared.product().name());
        assertEquals("Cabello", shared.product().category());
        assertEquals("Lo usamos en el ritual de brillo", shared.product().description());
        assertEquals(carlos.getCenter().getId(), shared.product().centerId(), "la API apunta qué centro lo aportó");
        assertNotNull(ImageIO.read(new ByteArrayInputStream(shared.product().photo())), "la foto ya reducida, en JPEG");

        // Un centro sin revisar todavía no comparte: lo que llega a la API lo ven todos
        carlos.getCenter().setPublished(false);
        assertTrue(sharing.contributionOf(created.getId()).isEmpty());
        carlos.getCenter().setPublished(true);

        // Sin código no se comparte nada
        long before = events.stream(ProductSharedEvent.class).count();
        mvc.perform(multipart("/worker/inventory/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Mezcla de la casa").param("price", "5").param("stock", "1"))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));
        assertEquals(before, events.stream(ProductSharedEvent.class).count());
    }

    @Test
    void searchesOutsideCuquoraHaveAnHourlyLimitPerCenter() {
        Long centerId = 999_999L; // un centro que no usa ningún otro test: el contador dura una hora
        BusinessRuleException limit = assertThrows(BusinessRuleException.class, () -> {
            for (int i = 0; i < 200; i++) lookup.lookup(centerId, "0036000291452");
        });
        assertTrue(limit.getMessage().contains("muchos códigos"));
    }

    @Test
    void openBeautyFactsAnswerIsReadAndOnlyItsImagesAreDownloaded() throws Exception {
        String body = """
                {"code":"4006381333931","status":1,"status_verbose":"product found","product":{
                  "product_name":"Hydrating Serum","product_name_es":"Sérum hidratante","brands":"Lumière,Grupo",
                  "quantity":"30 ml","categories_tags":["en:face-serums"],
                  "image_front_url":"https://images.openbeautyfacts.org/images/products/400/front_es.3.400.jpg"}}
                """;
        var method = Class.forName("com.alexxlpz.crm_cbelleza.productdata.OpenBeautyFacts")
                .getDeclaredMethod("parse", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<ExternalProduct> parsed = (Optional<ExternalProduct>) method.invoke(null, json.readTree(body));
        ExternalProduct p = parsed.orElseThrow();
        assertEquals("Sérum hidratante", p.name());
        assertEquals("Lumière", p.brand());
        assertEquals("Rostro", p.category());
        assertEquals(ProductOrigin.OPEN_BEAUTY_FACTS, p.origin());

        @SuppressWarnings("unchecked")
        Optional<ExternalProduct> missing = (Optional<ExternalProduct>) method.invoke(null,
                json.readTree("{\"code\":\"1\",\"status\":0,\"status_verbose\":\"product not found\"}"));
        assertTrue(missing.isEmpty());

        var own = Class.forName("com.alexxlpz.crm_cbelleza.productdata.OpenBeautyFacts").getDeclaredMethod("isOwnImage", String.class);
        own.setAccessible(true);
        assertEquals(true, own.invoke(null, "https://images.openbeautyfacts.org/images/x.jpg"));
        assertEquals(false, own.invoke(null, "http://images.openbeautyfacts.org/images/x.jpg"));
        assertEquals(false, own.invoke(null, "https://openbeautyfacts.org.evil.example/x.jpg"));
        assertEquals(false, own.invoke(null, "https://127.0.0.1/x.jpg"));
    }

    @Test
    void saleModalCanFindTheProductByItsBarcode() throws Exception {
        mvc.perform(multipart("/worker/inventory/add").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("mode", "new").param("name", "Champú sólido").param("category", "Cabello")
                        .param("price", "12").param("stock", "3").param("barcode", EAN))
                .andExpect(flash().attribute("successMessage", "Producto añadido al inventario."));
        assertNotNull(productWithBarcode(EAN));

        // El modal de venta lleva el buscador con la cámara y el código de cada producto, en el inventario…
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("id=\"saleSearch\"")))
                .andExpect(content().string(containsString("data-sale-scan-start")))
                .andExpect(content().string(containsString("data-barcode=\"" + EAN + "\"")))
                .andExpect(content().string(containsString("/js/barcode-camera")));
        // …y en la ficha del cliente
        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("id=\"saleSearch\"")))
                .andExpect(content().string(containsString("data-barcode=\"" + EAN + "\"")));

        // Otro centro no tiene ese producto en su lista
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-barcode=\"" + EAN + "\""))));
    }
}
