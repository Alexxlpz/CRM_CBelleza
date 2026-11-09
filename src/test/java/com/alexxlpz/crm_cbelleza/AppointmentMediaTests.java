package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentMediaRepository;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.AppointmentMediaService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fotos y vídeos en el resumen del tratamiento: solo en citas empezadas y solo para el equipo del centro. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentMediaTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private AppointmentMediaRepository media;

    private User carlos;
    private User sofia;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        treatment = treatments.findByCenterId(carlos.getCenter().getId()).getFirst();
    }

    private Appointment confirmedAt(LocalDateTime dateTime) {
        return appointments.saveAndFlush(Appointment.builder()
                .center(carlos.getCenter())
                .treatment(treatment)
                .client(sofia)
                .worker(carlos)
                .status(AppointmentStatus.CONFIRMED)
                .dateTime(dateTime)
                .createdAt(dateTime.minusDays(7))
                .build());
    }

    private Appointment started() {
        return confirmedAt(LocalDateTime.now().minusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0));
    }

    private ResultActions upload(Appointment app, MockMultipartFile file, String worker) throws Exception {
        return mvc.perform(multipart("/worker/appointments/" + app.getId() + "/media").file(file).with(csrf())
                .with(TestUsers.as(users, worker)));
    }

    private static MockMultipartFile photo() throws Exception {
        BufferedImage image = new BufferedImage(2400, 1800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(220, 120, 110));
        g.fillRect(0, 0, 2400, 1800);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "rojez.png", "image/png", out.toByteArray());
    }

    /** Lo justo para que parezca un MP4: tamaño de la caja + «ftyp» + marca. */
    private static MockMultipartFile mp4() {
        byte[] data = new byte[4096];
        byte[] head = "\0\0\0\u0018ftypisom".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(head, 0, data, 0, head.length);
        return new MockMultipartFile("file", "zona.mp4", "video/mp4", data);
    }

    @Test
    void photoIsReducedAndOnlyItsCenterCanSeeIt() throws Exception {
        Appointment app = started();
        String body = upload(app, photo(), TestUsers.WORKER_CENTER_1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media", hasSize(1)))
                .andExpect(jsonPath("$.media[0].video").value(false))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.media[0].url");

        byte[] jpeg = mvc.perform(get(url).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage saved = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
        assertEquals(1600, saved.getWidth(), "se reduce a 1.600 px por el lado largo");

        // Otro centro no la ve ni sabe si existe
        mvc.perform(get(url).with(TestUsers.as(users, TestUsers.WORKER_CENTER_2))).andExpect(status().isNotFound());
        // Un cliente tampoco (es interno del centro)
        mvc.perform(get(url).with(TestUsers.as(users, TestUsers.CLIENT))).andExpect(status().is3xxRedirection());

        // Sale en la agenda y en la ficha del cliente
        mvc.perform(get("/worker/calendar/data").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(jsonPath("$.appointments[?(@.id == " + app.getId() + ")].media[0].url").value(url));
        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-media-view=\"" + url + "\"")));
    }

    @Test
    void videoIsKeptAsIsAndServedByRanges() throws Exception {
        Appointment app = started();
        String body = upload(app, mp4(), TestUsers.WORKER_CENTER_1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].video").value(true))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.media[0].url");

        // Safari pide los vídeos por partes
        byte[] part = mvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=0-11").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(Arrays.copyOf(mp4().getBytes(), 12), part);
    }

    @Test
    void somethingThatIsNotAVideoIsRejected() throws Exception {
        Appointment app = started();
        upload(app, new MockMultipartFile("file", "virus.mp4", "video/mp4", "MZ esto no es un vídeo".getBytes()), TestUsers.WORKER_CENTER_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("MP4, MOV o WebM")));
        assertEquals(0, media.countByAppointmentId(app.getId()));
    }

    @Test
    void onlyOnceTheAppointmentHasStartedAndUpToTheLimit() throws Exception {
        Appointment future = confirmedAt(LocalDateTime.now().plusDays(5).withHour(10).withMinute(0));
        upload(future, mp4(), TestUsers.WORKER_CENTER_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("ya ha empezado")));

        Appointment app = started();
        for (int i = 0; i < AppointmentMediaService.MAX_PER_APPOINTMENT; i++) {
            upload(app, mp4(), TestUsers.WORKER_CENTER_1).andExpect(status().isOk());
        }
        upload(app, mp4(), TestUsers.WORKER_CENTER_1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Quita alguno")));

        // Otro centro no puede añadir nada a esta cita
        upload(started(), mp4(), TestUsers.WORKER_CENTER_2).andExpect(status().isNotFound());
    }

    @Test
    void removingDeletesTheFile() throws Exception {
        Appointment app = started();
        String body = upload(app, mp4(), TestUsers.WORKER_CENTER_1).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.media[0].id");

        // Desde otro centro no se puede quitar
        mvc.perform(post("/worker/appointments/" + app.getId() + "/media/" + id + "/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/worker/appointments/" + app.getId() + "/media/" + id + "/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media", hasSize(0)));
        mvc.perform(get("/worker/media/" + id).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isNotFound());
    }
}
