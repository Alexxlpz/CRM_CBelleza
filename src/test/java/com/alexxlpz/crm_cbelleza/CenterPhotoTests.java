package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Foto de presentación del centro: subida en «Mi Centro» y en el alta, validación, tamaño y quién la ve. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterPhotoTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private CenterPhotoRepository photos;
    @Autowired
    private CenterRegistrationService registrationService;

    private Center centerOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getCenter();
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(180, 140, 100));
        g.fillRect(0, 0, width, height);
        // Mitad izquierda oscura: permite comprobar que la foto no se gira por error
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** JPEG con un bloque EXIF que solo dice «orientación 6» (girar 90° a la derecha), como las fotos de móvil. */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] exif = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] result = new byte[jpeg.length + exif.length];
        result[0] = (byte) 0xFF;
        result[1] = (byte) 0xD8;
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void workerUploadsPhotoThatIsResizedAndShownInSearch() throws Exception {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);
        byte[] big = image("png", 3000, 2000);

        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "fachada.png", "image/png", big))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/center#foto"))
                .andExpect(flash().attribute("photoMessage", "Foto guardada."));

        Center saved = centers.findById(center.getId()).orElseThrow();
        assertNotNull(saved.getPhotoUpdatedAt());
        byte[] stored = mvc.perform(get(saved.getPhotoUrl()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage result = read(stored);
        assertEquals(1200, result.getWidth());
        assertEquals(800, result.getHeight());
        assertTrue(stored.length < big.length);

        mvc.perform(get("/api/centers").param("searchName", center.getName()))
                .andExpect(jsonPath("$[0].photoUrl").value(saved.getPhotoUrl()));
    }

    @Test
    void phonePhotoIsTurnedUpright() throws Exception {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);
        byte[] sideways = withExifOrientation(image("jpg", 800, 600), 6);

        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "movil.jpg", "image/jpeg", sideways))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attributeExists("photoMessage"));

        BufferedImage result = read(photos.findById(center.getId()).orElseThrow().getData());
        assertEquals(600, result.getWidth());
        assertEquals(800, result.getHeight());
        // Girada 90° a la derecha, la mitad oscura de la izquierda queda arriba
        assertTrue(new Color(result.getRGB(300, 100)).getRed() < 60);
        assertTrue(new Color(result.getRGB(300, 700)).getRed() > 120);
    }

    @Test
    void rejectsFilesThatAreNotPhotosOrTooSmall() throws Exception {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);

        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", "<html>no soy una foto</html>".getBytes()))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("photoError", "El archivo no es una imagen. Sube la foto en JPG o PNG."));

        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "icono.png", "image/png", image("png", 120, 80)))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("photoError", containsString("muy pequeña")));

        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "logo.gif", "image/gif", image("gif", 800, 600)))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("photoError", "Sube la foto en JPG o PNG."));

        byte[] heavy = Arrays.copyOf(image("jpg", 800, 600), 9 * 1024 * 1024);
        mvc.perform(multipart("/worker/center/photo")
                        .file(new MockMultipartFile("photo", "enorme.jpg", "image/jpeg", heavy))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("photoError", "La foto pesa más de 8 MB. Elige una más ligera."));

        mvc.perform(multipart("/worker/center/photo").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(flash().attribute("photoError", "Elige una foto para subirla."));

        assertNull(centers.findById(center.getId()).orElseThrow().getPhotoUpdatedAt());
        assertTrue(photos.findById(center.getId()).isEmpty());
    }

    @Test
    void workerRemovesPhoto() throws Exception {
        Center center = centerOf(TestUsers.WORKER_CENTER_1);
        mvc.perform(multipart("/worker/center/photo")
                .file(new MockMultipartFile("photo", "fachada.jpg", "image/jpeg", image("jpg", 800, 600)))
                .with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
        String url = centers.findById(center.getId()).orElseThrow().getPhotoUrl();

        mvc.perform(post("/worker/center/photo/delete").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/center#foto"));

        assertNull(centers.findById(center.getId()).orElseThrow().getPhotoUrl());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void registrationSavesPhotoThatOnlyTheTeamAndTheReviewerSeeUntilPublished() throws Exception {
        CenterRegistrationForm form = new CenterRegistrationForm("Estudio Lirio", "B87654321", "+34 911 444 555",
                "Calle Mayor 30, 28013 Madrid", 40.4155, -3.7074, "Europe/Madrid", 1, TestHours.DAYS, TestHours.FROM, TestHours.TO, "Lirio Gómez", "lirio@estudiolirio.es",
                List.of(), List.of(), "OFF", 1, 100, null, List.of(), null, true);
        var registration = registrationService.register(form,
                new MockMultipartFile("photo", "sala.jpg", "image/jpeg", image("jpg", 900, 600)));
        Center center = centers.findById(registration.centerId()).orElseThrow();
        assertNotNull(center.getPhotoUrl());

        // Centro oculto: ni el público ni otro centro ven la foto
        mvc.perform(get(center.getPhotoUrl())).andExpect(status().isNotFound());
        mvc.perform(get(center.getPhotoUrl()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());
        // Su equipo y administración (con el enlace del correo) sí
        mvc.perform(get(center.getPhotoUrl()).with(TestUsers.as(users, "lirio@estudiolirio.es")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("private")));
        mvc.perform(get("/register-center/approve/" + registration.approvalToken() + "/photo"))
                .andExpect(status().isOk());
        mvc.perform(get("/register-center/approve/" + registration.approvalToken()))
                .andExpect(content().string(containsString("/register-center/approve/" + registration.approvalToken() + "/photo")));
    }

    @Test
    void invalidPhotoInRegistrationCreatesNothing() throws Exception {
        long before = centers.count();

        mvc.perform(multipart("/register-center")
                        .file(new MockMultipartFile("photo", "sala.png", "image/png", image("png", 100, 100)))
                        .param("centerName", "Estudio Nube").param("cif", "B11223344").param("phone", "+34 911 000 111")
                        .param("address", "Calle Luna 1, Madrid").param("latitude", "40.42").param("longitude", "-3.70")
                        .param("timeZone", "Europe/Madrid").param("cabins", "1")
                        .param("hoursDay", "1").param("hoursFrom", "09:00").param("hoursTo", "20:00")
                        .param("contactName", "Nube Sanz").param("email", "nube@estudionube.es")
                        .param("mode", "OFF").param("terms", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("muy pequeña")))
                .andExpect(content().string(containsString("Vuelve a elegir la foto")));

        assertEquals(before, centers.count());
    }
}
