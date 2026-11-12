package com.alexxlpz.productapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "product-api.key=" + ProductApiTests.KEY)
@AutoConfigureMockMvc
@Transactional
class ProductApiTests {

    static final String KEY = "clave-de-pruebas-de-la-api-de-productos-1234";
    /** Alissi Brontë Sublime: no está en Open Beauty Facts. */
    static final String SUBLIME = "8436595340863";
    static final String NIVEA = "4005808155767";
    /** Fotos de verdad (pequeñas, se guardan tal cual): la API las abre para reducirlas. */
    static final byte[] JPEG = image("jpg", 120, 160);
    static final byte[] PNG = image("png", 120, 160);

    @Autowired
    MockMvc mvc;
    @Autowired
    ProductRepository products;
    @Autowired
    ProductContributionRepository contributions;
    @MockitoBean
    OpenBeautyFacts openBeautyFacts;

    @Test
    void withoutTheKeyNothingUnderApiAnswers() throws Exception {
        mvc.perform(get("/api/products/" + SUBLIME)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/" + SUBLIME).header("X-Api-Key", "otra")).andExpect(status().isUnauthorized());
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verify(openBeautyFacts, never()).find(anyString());
    }

    @Test
    void unknownEverywhereIs404AndBadCodesAre400() throws Exception {
        when(openBeautyFacts.find(SUBLIME)).thenReturn(Optional.empty());
        mvc.perform(get("/api/products/" + SUBLIME).header("X-Api-Key", KEY)).andExpect(status().isNotFound());
        // No se le vuelve a preguntar a Open Beauty Facts por el mismo código en un rato
        mvc.perform(get("/api/products/" + SUBLIME).header("X-Api-Key", KEY)).andExpect(status().isNotFound());
        verify(openBeautyFacts, org.mockito.Mockito.times(1)).find(SUBLIME);
        mvc.perform(get("/api/products/8436595340864").header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código 8436595340864 no es válido."));
        mvc.perform(get("/api/products/abc").header("X-Api-Key", KEY)).andExpect(status().isBadRequest());
    }

    @Test
    void foundInOpenBeautyFactsIsStoredWithItsPhoto() throws Exception {
        when(openBeautyFacts.find(NIVEA)).thenReturn(Optional.of(new ImportedProduct(
                "Creme", "Nivea", "150 ml", "Cuerpo", "https://images.openbeautyfacts.org/nivea.jpg")));
        when(openBeautyFacts.image("https://images.openbeautyfacts.org/nivea.jpg")).thenReturn(Optional.of(JPEG));

        mvc.perform(get("/api/products/" + NIVEA).header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Creme"))
                .andExpect(jsonPath("$.brand").value("Nivea"))
                .andExpect(jsonPath("$.category").value("Cuerpo"))
                .andExpect(jsonPath("$.source").value("OPEN_BEAUTY_FACTS"))
                .andExpect(jsonPath("$.image").value(startsWith("/api/products/" + NIVEA + "/image?v=")));
        mvc.perform(get("/api/products/" + NIVEA + "/image").header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));

        // La segunda vez ya no sale a buscarlo
        mvc.perform(get("/api/products/" + NIVEA).header("X-Api-Key", KEY)).andExpect(status().isOk());
        verify(openBeautyFacts, org.mockito.Mockito.times(1)).find(NIVEA);
    }

    @Test
    void aCenterAddsAProductAndTheNextOneFindsIt() throws Exception {
        mvc.perform(multipart("/api/products/" + SUBLIME).file(new MockMultipartFile("image", "s.png", "image/png", PNG))
                        .param("name", "Alissi Brontë Sublime").param("category", "Cabello")
                        .param("description", "Champú de brillo para cabello teñido").header("X-Api-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("CUQUORA"));

        mvc.perform(get("/api/products/" + SUBLIME).header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alissi Brontë Sublime"))
                .andExpect(jsonPath("$.category").value("Cabello"))
                .andExpect(jsonPath("$.description").value("Champú de brillo para cabello teñido"))
                .andExpect(jsonPath("$.image").exists());
        mvc.perform(get("/api/products/" + SUBLIME + "/image").header("X-Api-Key", KEY))
                .andExpect(content().contentType("image/png"));
        verify(openBeautyFacts, never()).find(anyString());
    }

    @Test
    void anotherCenterOnlyFillsWhatIsMissing() throws Exception {
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "Sublime").header("X-Api-Key", KEY))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/products/" + SUBLIME).file(new MockMultipartFile("image", "s.jpg", "image/jpeg", JPEG))
                        .param("name", "Champú caro de mi centro").param("category", "Cabello").param("brand", "Alissi Brontë")
                        .param("description", "Para el ritual de brillo")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sublime"))
                .andExpect(jsonPath("$.brand").value("Alissi Brontë"))
                .andExpect(jsonPath("$.description").value("Para el ritual de brillo"))
                .andExpect(jsonPath("$.category").value("Cabello"))
                .andExpect(jsonPath("$.image").exists());

        // Una foto que ya está no se cambia
        mvc.perform(multipart("/api/products/" + SUBLIME).file(new MockMultipartFile("image", "s.png", "image/png", PNG))
                        .param("name", "Sublime").header("X-Api-Key", KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + SUBLIME + "/image").header("X-Api-Key", KEY))
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void eachCenterContributionIsRecorded() throws Exception {
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "Sublime").param("center", "7")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/products/" + SUBLIME).file(new MockMultipartFile("image", "s.jpg", "image/jpeg", JPEG))
                        .param("name", "Sublime").param("category", "Cabello").param("center", "9")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isOk());
        // Si no rellena nada nuevo no se apunta; sin centro (versión antigua de Cuquora) se apunta sin él
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "Sublime").param("center", "11")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "Sublime").param("brand", "Alissi Brontë")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isOk());

        List<ProductContribution> log = contributions.findByGtinOrderById(SUBLIME);
        assertEquals(3, log.size());
        assertEquals(7L, log.get(0).getCenterId());
        assertTrue(log.get(0).isCreated());
        assertEquals("nombre", log.get(0).getFields());
        assertEquals(9L, log.get(1).getCenterId());
        assertFalse(log.get(1).isCreated());
        assertEquals("categoria,foto", log.get(1).getFields());
        assertNull(log.get(2).getCenterId());
        assertEquals("marca", log.get(2).getFields());

        // El centro tiene que ser un número
        mvc.perform(multipart("/api/products/" + NIVEA).param("name", "Creme").param("center", "abc")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El centro tiene que ser un número."));
        mvc.perform(multipart("/api/products/" + NIVEA).param("name", "Creme").param("center", "-1")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest());
        assertTrue(products.findById(NIVEA).isEmpty());

        // Borrar un producto borra también su registro
        products.deleteById(SUBLIME);
        products.flush();
        assertTrue(contributions.findByGtinOrderById(SUBLIME).isEmpty());
    }

    @Test
    void contributionsAreValidated() throws Exception {
        mvc.perform(multipart("/api/products/" + SUBLIME).header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el nombre del producto."));
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "x".repeat(201)).header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/products/" + SUBLIME)
                        .file(new MockMultipartFile("image", "s.gif", "image/jpeg", "GIF89a....".getBytes()))
                        .param("name", "Sublime").header("X-Api-Key", KEY))
                .andExpect(status().isBadRequest());
        // Una categoría que no es de Cuquora se ignora
        mvc.perform(multipart("/api/products/" + SUBLIME).param("name", "Sublime").param("category", "<b>x</b>")
                        .header("X-Api-Key", KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").doesNotExist());
        assertTrue(products.findById(SUBLIME).isPresent());
    }

    @Test
    void bigPhotosAreShrunkBeforeBeingStored() throws Exception {
        // Una foto grande de Open Beauty Facts se guarda a 800 px en JPEG
        byte[] big = image("jpg", 2400, 1800);
        assertTrue(big.length > Images.SMALL_BYTES, "la de prueba tiene que ser pesada");
        when(openBeautyFacts.find(NIVEA)).thenReturn(Optional.of(new ImportedProduct(
                "Creme", "Nivea", "150 ml", "Cuerpo", "https://images.openbeautyfacts.org/nivea.png")));
        when(openBeautyFacts.image("https://images.openbeautyfacts.org/nivea.png")).thenReturn(Optional.of(big));
        mvc.perform(get("/api/products/" + NIVEA).header("X-Api-Key", KEY)).andExpect(status().isOk());
        byte[] stored = mvc.perform(get("/api/products/" + NIVEA + "/image").header("X-Api-Key", KEY))
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage saved = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(800, saved.getWidth());
        assertEquals(600, saved.getHeight());
        assertTrue(stored.length < big.length / 4);

        // Lo mismo con la que aporta un centro, también si es alta y estrecha
        mvc.perform(multipart("/api/products/" + SUBLIME).file(new MockMultipartFile("image", "s.jpg", "image/jpeg",
                        image("jpg", 1000, 3000))).param("name", "Sublime").header("X-Api-Key", KEY))
                .andExpect(status().isCreated());
        saved = ImageIO.read(new ByteArrayInputStream(mvc.perform(get("/api/products/" + SUBLIME + "/image")
                .header("X-Api-Key", KEY)).andReturn().getResponse().getContentAsByteArray()));
        assertEquals(267, saved.getWidth());
        assertEquals(800, saved.getHeight());

        // Una pequeña se queda igual; una que no se puede abrir no se acepta
        assertArrayEquals(PNG, Images.shrink(PNG).orElseThrow());
        byte[] broken = java.util.Arrays.copyOf(JPEG, 40);
        assertTrue(Images.shrink(broken).isEmpty());
        // Una con demasiados píxeles se rechaza antes de abrirla (de un solo color: pesa poco pero ocuparía 80 MB)
        assertTrue(Images.shrink(plain(5000, 4000)).isEmpty());
    }

    static byte[] plain(int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Una foto de prueba con ruido (para que pese como una foto de verdad) del tamaño pedido. */
    static byte[] image(String format, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        java.util.Random random = new java.util.Random(width * 31L + height);
        for (int x = 0; x < width; x += 4) {
            for (int y = 0; y < height; y += 4) {
                g.setColor(new Color((x + random.nextInt(40)) % 256, (y + random.nextInt(40)) % 256, random.nextInt(256)));
                g.fillRect(x, y, 4, 4);
            }
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void upcAndShortGtin14AreTheSameProduct() {
        assertEquals("0012345678905", Gtin.normalize("012345678905"));
        assertEquals("0012345678905", Gtin.normalize("00012345678905"));
        assertEquals("96385074", Gtin.normalize("96385074"));
        assertThrows(InvalidRequestException.class, () -> Gtin.normalize("1234"));
    }

    @Test
    void openBeautyFactsAnswerIsMappedAndOnlyItsImagesAreDownloaded() {
        var fields = new OpenBeautyFactsClient.Fields("Shampoo doux", null, null, "Klorane, Pierre Fabre", "200 ml",
                List.of("en:hair-care", "en:shampoos"), "https://images.openbeautyfacts.org/k.jpg", null);
        ImportedProduct p = OpenBeautyFactsClient.toProduct(new OpenBeautyFactsClient.Response(1, fields)).orElseThrow();
        assertEquals("Shampoo doux", p.name());
        assertEquals("Klorane", p.brand());
        assertEquals("Cabello", p.category());
        assertEquals("200 ml", p.quantity());
        assertTrue(OpenBeautyFactsClient.toProduct(new OpenBeautyFactsClient.Response(0, null)).isEmpty());

        assertTrue(OpenBeautyFactsClient.isOwnImage("https://images.openbeautyfacts.org/a.jpg"));
        assertFalse(OpenBeautyFactsClient.isOwnImage("http://images.openbeautyfacts.org/a.jpg"));
        assertFalse(OpenBeautyFactsClient.isOwnImage("https://openbeautyfacts.org.evil.com/a.jpg"));
        assertFalse(OpenBeautyFactsClient.isOwnImage("https://images.openbeautyfacts.org:8443/a.jpg"));
    }
}
