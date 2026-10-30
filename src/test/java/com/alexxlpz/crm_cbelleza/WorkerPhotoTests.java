package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.UserPhotoRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Foto de perfil de los profesionales: la sube cada uno en «Mi Centro» y sale al reservar y en el equipo. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkerPhotoTests {

    private static final String STANDARD_WORKER = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserPhotoRepository photos;
    @Autowired
    private CenterService centerService;

    private User user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private static byte[] image(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(180, 140, 100));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void upload(String email, byte[] photo) throws Exception {
        mvc.perform(multipart("/worker/center/my-photo")
                        .file(new MockMultipartFile("photo", "yo.png", "image/png", photo))
                        .with(csrf()).with(TestUsers.as(users, email)))
                .andExpect(redirectedUrl("/worker/center#mi-foto"));
    }

    @Test
    void anyWorkerUploadsTheirPhotoCroppedToASquare() throws Exception {
        upload(STANDARD_WORKER, image(1600, 900));

        User lucia = user(STANDARD_WORKER);
        assertNotNull(lucia.getPhotoUrl());
        byte[] stored = mvc.perform(get(lucia.getPhotoUrl()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(400, result.getWidth());
        assertEquals(400, result.getHeight());
    }

    @Test
    void clientsSeeThePhotoWhenChoosingWhoToBookWith() throws Exception {
        upload(STANDARD_WORKER, image(500, 500));
        User lucia = user(STANDARD_WORKER);
        Center center = lucia.getCenter();
        String slug = centerService.shortSlug(center);

        mvc.perform(get("/client/centers/" + slug).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + lucia.getPhotoUrl().replace("&", "&amp;") + "\"")));
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(lucia.getPhotoUrl())));
    }

    @Test
    void aTinyPhotoIsRejected() throws Exception {
        mvc.perform(multipart("/worker/center/my-photo")
                        .file(new MockMultipartFile("photo", "yo.png", "image/png", image(100, 100)))
                        .with(csrf()).with(TestUsers.as(users, STANDARD_WORKER)))
                .andExpect(flash().attribute("myPhotoError", containsString("muy pequeña")));
        assertNull(user(STANDARD_WORKER).getPhotoUrl());
    }

    @Test
    void removingThePhotoBringsBackTheInitial() throws Exception {
        upload(STANDARD_WORKER, image(500, 500));
        String url = user(STANDARD_WORKER).getPhotoUrl();

        mvc.perform(post("/worker/center/my-photo/delete").with(csrf()).with(TestUsers.as(users, STANDARD_WORKER)))
                .andExpect(redirectedUrl("/worker/center#mi-foto"));
        assertNull(user(STANDARD_WORKER).getPhotoUrl());
        assertTrue(photos.findById(user(STANDARD_WORKER).getId()).isEmpty());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/worker/center").with(TestUsers.as(users, STANDARD_WORKER)))
                .andExpect(content().string(not(containsString("removeMyPhotoForm\""))));
    }

    @Test
    void clientsHaveNoProfilePhoto() throws Exception {
        User client = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        mvc.perform(get("/team/" + client.getId() + "/photo")).andExpect(status().isNotFound());
        mvc.perform(multipart("/worker/center/my-photo")
                        .file(new MockMultipartFile("photo", "yo.png", "image/png", image(500, 500)))
                        .with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isForbidden());
    }
}
