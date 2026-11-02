package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.services.QrCodeService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cartel con QR de «Mi Centro»: el QR lleva al enlace corto del centro y lo descarga cualquier trabajador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterPosterTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterService centerService;
    @Autowired
    private QrCodeService qr;

    private Center center() {
        return users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter();
    }

    private String shortLink() {
        return "http://localhost/c/" + centerService.shortSlug(center());
    }

    /** Lee el QR de un PNG como lo haría la cámara de un móvil. */
    private static String decode(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
    }

    @Test
    void myCenterLinksToThePoster() throws Exception {
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/worker/center/cartel")));
    }

    @Test
    void thePosterShowsTheCenterTheQrAndTheShortLink() throws Exception {
        mvc.perform(get("/worker/center/cartel").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(center().getName())))
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("localhost/c/" + centerService.shortSlug(center()))))
                .andExpect(content().string(containsString("/worker/center/qr.png")));
    }

    @Test
    void thePngQrOpensTheShortLink() throws Exception {
        byte[] png = mvc.perform(get("/worker/center/qr.png").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition",
                        containsString("qr-" + centerService.shortSlug(center()) + ".png")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(shortLink(), decode(png));
    }

    @Test
    void theSvgQrIsADownloadableVector() throws Exception {
        mvc.perform(get("/worker/center/qr.svg").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Content-Disposition", containsString(".svg")))
                .andExpect(content().string(containsString("<path")));
    }

    @Test
    void eachWorkerGetsTheQrOfTheirOwnCenter() throws Exception {
        Center other = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getCenter();
        byte[] png = mvc.perform(get("/worker/center/qr.png").with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("http://localhost/c/" + centerService.shortSlug(other), decode(png));
        assertNotEquals(shortLink(), decode(png));
    }

    @Test
    // Un cliente que entra en /worker/** vuelve al buscador de centros (como en el resto del panel)
    void clientsCannotOpenThePoster() throws Exception {
        mvc.perform(get("/worker/center/cartel").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/centers"));
        mvc.perform(get("/worker/center/qr.png").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void theQrNeedsAShortText() {
        assertThrows(IllegalArgumentException.class, () -> qr.svg(" ", "vacío"));
        assertThrows(IllegalArgumentException.class, () -> qr.png("x".repeat(301)));
        assertTrue(qr.svg("https://cuquora.com/c/a", "Centro <A & B>").contains("Centro &lt;A &amp; B&gt;"));
    }
}
