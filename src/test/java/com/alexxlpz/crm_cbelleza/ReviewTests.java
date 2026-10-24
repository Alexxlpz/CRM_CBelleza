package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.dto.CenterRating;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Reseñas verificadas: el cliente valora una cita completada (una vez y en 90 días), la nota sale en el buscador
 * y en la ficha del centro, y solo el dueño del centro responde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private ReviewRepository reviews;

    private User sofia;
    private Center center;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        center = centers.findById(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId()).orElseThrow();
        treatment = treatments.findByCenterId(center.getId()).getFirst();
    }

    private Appointment appointment(AppointmentStatus status, LocalDateTime at) {
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(at).treatment(treatment).center(center).client(sofia)
                .status(status).price(treatment.getPrice()).createdAt(at.minusDays(5))
                .build());
    }

    private void review(Appointment appointment, int rating, String comment) throws Exception {
        mvc.perform(post("/client/appointments/" + appointment.getId() + "/review")
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf())
                        .param("rating", String.valueOf(rating)).param("comment", comment))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/client/appointments"));
    }

    @Test
    void clientReviewsACompletedAppointmentOnceAndItShowsOnTheCenterPage() throws Exception {
        Appointment done = appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(3));

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-id=\"" + done.getId() + "\"")))
                .andExpect(content().string(containsString("id=\"reviewModal\"")));

        mvc.perform(post("/client/appointments/" + done.getId() + "/review")
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf())
                        .param("rating", "4").param("comment", "  Muy buen trato.  "))
                .andExpect(flash().attribute("successMessage", containsString("Gracias")));

        Review saved = reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).getFirst();
        assertEquals(4, saved.getRating());
        assertEquals("Muy buen trato.", saved.getComment());
        assertEquals("Sofía M.", saved.getAuthorName());

        // Ya no se ofrece «Valorar»: sale su nota
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(not(containsString("data-id=\"" + done.getId() + "\""))))
                .andExpect(content().string(containsString("Tu valoración")));

        mvc.perform(get("/client/centers/" + center.getId()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Muy buen trato.")))
                .andExpect(content().string(containsString("Cita verificada")))
                .andExpect(content().string(containsString("4,0")))
                .andExpect(content().string(containsString("1 opinión verificada")));

        // Una segunda valoración de la misma cita no entra
        mvc.perform(post("/client/appointments/" + done.getId() + "/review")
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()).param("rating", "1"))
                .andExpect(flash().attribute("errorMessage", "Ya has valorado esta cita."));
        assertEquals(1, reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).size());
    }

    @Test
    void onlyCompletedRecentAppointmentsWithValidStarsCanBeReviewed() throws Exception {
        Appointment upcoming = appointment(AppointmentStatus.CONFIRMED, LocalDateTime.now().plusDays(3));
        Appointment noShow = appointment(AppointmentStatus.NO_SHOW, LocalDateTime.now().minusDays(3));
        Appointment old = appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(100));
        Appointment done = appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(1));

        for (Appointment app : List.of(upcoming, noShow)) {
            mvc.perform(post("/client/appointments/" + app.getId() + "/review")
                            .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()).param("rating", "5"))
                    .andExpect(flash().attribute("errorMessage", "Solo se pueden valorar las citas completadas."));
        }
        mvc.perform(post("/client/appointments/" + old.getId() + "/review")
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()).param("rating", "5"))
                .andExpect(flash().attribute("errorMessage", containsString("Ha pasado el plazo")));
        for (String stars : new String[]{"0", "6"}) {
            mvc.perform(post("/client/appointments/" + done.getId() + "/review")
                            .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()).param("rating", stars))
                    .andExpect(flash().attribute("errorMessage", "Elige de 1 a 5 estrellas."));
        }
        mvc.perform(post("/client/appointments/" + done.getId() + "/review")
                        .with(TestUsers.as(users, TestUsers.CLIENT)).with(csrf()).param("rating", "5")
                        .param("comment", "x".repeat(Review.MAX_TEXT + 1)))
                .andExpect(flash().attribute("errorMessage", containsString("no puede pasar de")));
        assertTrue(reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).isEmpty());
    }

    @Test
    void anotherClientCannotReviewMyAppointment() throws Exception {
        Appointment done = appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(2));
        User other = users.findAll().stream()
                .filter(u -> u.getRole() == com.alexxlpz.crm_cbelleza.entities.Role.CLIENT && !u.getId().equals(sofia.getId()))
                .findFirst().orElseThrow();

        mvc.perform(post("/client/appointments/" + done.getId() + "/review")
                        .with(TestUsers.as(users, other.getName())).with(csrf()).param("rating", "1"))
                .andExpect(status().isNotFound());
        assertFalse(reviews.existsByAppointmentId(done.getId()));
    }

    @Test
    void theOwnerRepliesAndTheClientIsNotified() throws Exception {
        review(appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(2)), 5, "Genial");
        Review saved = reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).getFirst();

        // La campana del centro avisa de la reseña nueva y el panel la cuenta como sin responder
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Sofía M. ha valorado su cita con 5 estrellas.")))
                .andExpect(content().string(containsString("1 sin responder")));

        mvc.perform(post("/worker/reviews/" + saved.getId() + "/reply")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf())
                        .param("reply", "¡Gracias, Sofía!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Respuesta publicada")));
        assertEquals("¡Gracias, Sofía!", reviews.findById(saved.getId()).orElseThrow().getReply());

        mvc.perform(get("/client/centers/" + center.getId()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("¡Gracias, Sofía!")));
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString(center.getName() + " ha respondido a tu valoración")));

        // Una respuesta vacía la quita
        mvc.perform(post("/worker/reviews/" + saved.getId() + "/reply")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()).param("reply", " "))
                .andExpect(flash().attribute("successMessage", "Respuesta quitada."));
        Review cleared = reviews.findById(saved.getId()).orElseThrow();
        assertNull(cleared.getReply());
        assertNull(cleared.getRepliedAt());
    }

    @Test
    void aStandardWorkerReadsReviewsButCannotReply() throws Exception {
        review(appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(2)), 3, "Normal");
        Review saved = reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).getFirst();

        mvc.perform(get("/worker/reviews").with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Normal")))
                .andExpect(content().string(not(containsString("Publicar respuesta"))));

        mvc.perform(post("/worker/reviews/" + saved.getId() + "/reply")
                        .with(TestUsers.as(users, LUCIA)).with(csrf()).param("reply", "Hola"))
                .andExpect(redirectedUrl("/worker/reviews"))
                .andExpect(flash().attribute("errorMessage", containsString("Carlos Mendoza")));
        assertNull(reviews.findById(saved.getId()).orElseThrow().getReply());
    }

    @Test
    void aWorkerOfAnotherCenterCannotReply() throws Exception {
        review(appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(2)), 5, "Bien");
        Review saved = reviews.findByCenterIdOrderByCreatedAtDesc(center.getId()).getFirst();

        mvc.perform(post("/worker/reviews/" + saved.getId() + "/reply")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)).with(csrf()).param("reply", "Intruso"))
                .andExpect(status().isNotFound());
        assertNull(reviews.findById(saved.getId()).orElseThrow().getReply());
    }

    @Test
    void theSearchShowsTheRatingAndFiltersTopRatedCenters() throws Exception {
        review(appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(2)), 5, "");
        review(appointment(AppointmentStatus.COMPLETED, LocalDateTime.now().minusDays(4)), 4, "");

        List<Map<String, Object>> all = JsonPath.read(mvc.perform(get("/api/centers?size=50"))
                .andReturn().getResponse().getContentAsString(), "$");
        Map<String, Object> card = all.stream()
                .filter(c -> center.getId().equals(((Number) c.get("id")).longValue())).findFirst().orElseThrow();
        assertEquals(4.5, ((Number) card.get("rating")).doubleValue());
        assertEquals(2, ((Number) card.get("reviewCount")).intValue());

        List<Map<String, Object>> top = JsonPath.read(mvc.perform(get("/api/centers?size=50&topRated=true"))
                .andReturn().getResponse().getContentAsString(), "$");
        assertEquals(1, top.size());
        assertEquals(center.getId(), ((Number) top.getFirst().get("id")).longValue());
    }

    @Test
    void centerRatingLabels() {
        CenterRating rating = new CenterRating(4.26, 23);
        assertEquals("4,3", rating.averageLabel());
        assertEquals("23 reseñas", rating.countLabel());
        assertEquals(4.5, rating.roundedToHalf());
        assertEquals("1 reseña", new CenterRating(5, 1).countLabel());
        assertEquals(4.0, new CenterRating(4.2, 3).roundedToHalf());
        assertFalse(CenterRating.NONE.hasReviews());
    }
}
