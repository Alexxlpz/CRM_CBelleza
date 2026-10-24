package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Horario propio de cada centro y reparto entre el dueño del centro (Carlos en Centro Histórico) y los
 * trabajadores estándar (Lucía). Cada test se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterOwnershipTests {

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
    private AgendaBlockRepository blocks;
    @Autowired
    private OpeningHoursService openingHours;

    private User carlos;
    private User lucia;
    private Center center;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        lucia = users.findByEmailIgnoreCase(LUCIA).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
    }

    @Test
    void ownerChangesTheOpeningHoursAndBookingsFollowThem() throws Exception {
        mvc.perform(post("/worker/center/hours").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        // Lunes con jornada partida; el resto de la semana, cerrado
                        .param("hoursDay", "1", "1")
                        .param("hoursFrom", "10:00", "17:00")
                        .param("hoursTo", "14:00", "21:00"))
                .andExpect(redirectedUrl("/worker/center#horario"))
                .andExpect(flash().attributeExists("hoursMessage"));

        assertEquals(List.of("Lunes: 10:00 - 14:00 y 17:00 - 21:00"), openingHours.forCenter(center.getId()).describe());
        mvc.perform(get("/api/centers/" + center.getId() + "/availability"))
                .andExpect(jsonPath("$.workingDays[0]").value(1))
                .andExpect(jsonPath("$.workingDays.length()").value(1))
                .andExpect(jsonPath("$.days['1'][1][0]").value(17 * 60))
                .andExpect(jsonPath("$.openHour").value(10))
                .andExpect(jsonPath("$.closeHour").value(21));
        mvc.perform(get("/client/centers/" + center.getId()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("Lunes: 10:00 - 14:00 y 17:00 - 21:00")));

        // A mediodía ya no se puede reservar
        LocalDate monday = LocalDate.now().plusWeeks(30).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Long treatmentId = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60).findFirst().orElseThrow().getId();
        mvc.perform(post("/client/booking").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("centerId", center.getId().toString())
                        .param("treatmentId", treatmentId.toString())
                        .param("dateTime", monday.atTime(14, 0).toString()))
                .andExpect(flash().attribute("bookingError", containsString("horario del centro")));
    }

    @Test
    void wrongOpeningHoursAreRejectedAndTheOldOnesStay() throws Exception {
        List<String> before = openingHours.forCenter(center.getId()).describe();
        mvc.perform(post("/worker/center/hours").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("hoursDay", "2", "2")
                        .param("hoursFrom", "09:00", "13:00")
                        .param("hoursTo", "14:00", "20:00"))
                .andExpect(redirectedUrl("/worker/center#horario"))
                .andExpect(flash().attribute("hoursError", containsString("se pisan")));
        assertEquals(before, openingHours.forCenter(center.getId()).describe());
    }

    @Test
    void standardWorkerCannotChangeTheCenter() throws Exception {
        List<String> hoursBefore = openingHours.forCenter(center.getId()).describe();
        long treatmentsBefore = treatments.findByCenterId(center.getId()).size();

        mvc.perform(post("/worker/center/hours").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("hoursDay", "1").param("hoursFrom", "09:00").param("hoursTo", "10:00"))
                .andExpect(redirectedUrl("/worker/center"))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño del centro")));
        mvc.perform(post("/worker/treatments/add").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("name", "Tratamiento nuevo").param("price", "20").param("duration", "30").param("type", "FACIAL"))
                .andExpect(redirectedUrl("/worker/treatments"))
                .andExpect(flash().attribute("errorMessage", containsString("Carlos Mendoza")));
        mvc.perform(post("/worker/center/bookings").with(csrf()).with(TestUsers.as(users, LUCIA)).param("autoAccept", "true"))
                .andExpect(redirectedUrl("/worker/center"));
        mvc.perform(get("/worker/analytics").with(TestUsers.as(users, LUCIA)))
                .andExpect(redirectedUrl("/worker/dashboard"));
        mvc.perform(get("/worker/clients/template").with(TestUsers.as(users, LUCIA)))
                .andExpect(redirectedUrl("/worker/clients"));
        mvc.perform(post("/worker/reviews/1/reply").with(csrf()).with(TestUsers.as(users, LUCIA)).param("reply", "Gracias"))
                .andExpect(redirectedUrl("/worker/reviews"));

        // Los datos del centro tampoco entran por el formulario de su cuenta
        mvc.perform(post("/worker/center").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("centerName", "Otro nombre").param("workerName", "Lucía Romero"))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño del centro")));

        Center after = centers.findById(center.getId()).orElseThrow();
        assertEquals(center.getName(), after.getName());
        assertFalse(after.isAutoAcceptBookings());
        assertEquals(hoursBefore, openingHours.forCenter(center.getId()).describe());
        assertEquals(treatmentsBefore, treatments.findByCenterId(center.getId()).size());
    }

    @Test
    void standardWorkerManagesHisOwnAccountAndSeesTheCenterReadOnly() throws Exception {
        mvc.perform(post("/worker/center").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("workerName", "Lucía Romero Gil").param("workerPhone", "600999888"))
                .andExpect(flash().attribute("successMessage", containsString("tu cuenta")));
        assertEquals("Lucía Romero Gil", users.findById(lucia.getId()).orElseThrow().getName());

        mvc.perform(get("/worker/center").with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("los gestiona Carlos Mendoza, dueño del centro")))
                .andExpect(content().string(containsString("Lunes a viernes: 09:00 - 20:00")))
                .andExpect(content().string(not(containsString("id=\"hoursForm\""))))
                .andExpect(content().string(not(containsString("id=\"centerName\""))))
                .andExpect(content().string(containsString("Guardar mis citas en Google Calendar")));
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("id=\"hoursForm\"")))
                .andExpect(content().string(containsString("id=\"centerName\"")));
        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(not(containsString("data-offer-edit"))))
                .andExpect(content().string(containsString("dueño del centro")));
    }

    @Test
    void standardWorkerBlocksOnlyHisOwnAgenda() throws Exception {
        LocalDate day = LocalDate.now().plusWeeks(31).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        String start = day.atStartOfDay().toString();
        String end = day.plusDays(1).atStartOfDay().toString();

        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("startsAt", start).param("endsAt", end))
                .andExpect(status().isOk());
        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("wholeCenter", "true").param("startsAt", start).param("endsAt", end))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Solo el dueño del centro puede cerrarlo")));
        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("workerId", carlos.getId().toString()).param("startsAt", start).param("endsAt", end))
                .andExpect(status().isConflict());

        // El cierre que pone el dueño solo lo quita él
        mvc.perform(post("/worker/agenda-blocks").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("wholeCenter", "true").param("startsAt", start).param("endsAt", end))
                .andExpect(status().isOk());
        AgendaBlock closure = blocks.findByCenterIdAndEndsAtAfterOrderByStartsAtAsc(center.getId(), day.atStartOfDay()).stream()
                .filter(AgendaBlock::isWholeCenter).findFirst().orElseThrow();
        mvc.perform(post("/worker/agenda-blocks/" + closure.getId() + "/delete").with(csrf()).with(TestUsers.as(users, LUCIA)))
                .andExpect(status().isConflict());
        mvc.perform(post("/worker/agenda-blocks/" + closure.getId() + "/delete").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerHandsTheCenterOverToAnotherWorker() throws Exception {
        mvc.perform(post("/worker/center/owner").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("newOwnerId", lucia.getId().toString()))
                .andExpect(redirectedUrl("/worker/center"))
                .andExpect(flash().attribute("successMessage", containsString("Lucía Romero")));
        assertTrue(centers.findById(center.getId()).orElseThrow().isOwnedBy(lucia.getId()));

        // Al momento: Carlos ya no puede y Lucía sí
        mvc.perform(post("/worker/center/bookings").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("autoAccept", "true"))
                .andExpect(flash().attribute("errorMessage", containsString("Lucía Romero")));
        mvc.perform(post("/worker/center/bookings").with(csrf()).with(TestUsers.as(users, LUCIA)).param("autoAccept", "true"))
                .andExpect(flash().attributeExists("bookingsMessage"));

        // Nadie puede ceder un centro que no es suyo ni dárselo a alguien de otro centro
        Long elenaId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getId();
        mvc.perform(post("/worker/center/owner").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("newOwnerId", elenaId.toString()))
                .andExpect(status().isNotFound());
    }
}
