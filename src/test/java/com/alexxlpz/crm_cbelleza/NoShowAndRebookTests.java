package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.LoyaltyMovementRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** «No se presentó» en la agenda, «Mis reservas» en próximas e historial y «Volver a reservar». */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoShowAndRebookTests {

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
    private LoyaltyMovementRepository movements;
    @Autowired
    private LoyaltyService loyaltyService;

    private User sofia() {
        return users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
    }

    private Center center1() {
        return users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter();
    }

    /** Cita confirmada de Sofía en el centro de Carlos, {@code daysFromNow} días desde hoy a las 10:00. */
    private Appointment appointment(int daysFromNow) {
        Center center = center1();
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(LocalDateTime.now().plusDays(daysFromNow).withHour(10).withMinute(0).withSecond(0).withNano(0))
                .treatment(treatment)
                .center(center)
                .client(sofia())
                .worker(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow())
                .status(AppointmentStatus.CONFIRMED)
                .price(40.0)
                .workerMessage("")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private AppointmentStatus statusOf(Appointment app) {
        return appointments.findById(app.getId()).orElseThrow().getStatus();
    }

    @Test
    void workerMarksNoShowAndTheClientCardCountsIt() throws Exception {
        Appointment past = appointment(-3);
        assertEquals(AppointmentStatus.COMPLETED, statusOf(past));

        mvc.perform(post("/worker/appointments/" + past.getId() + "/no-show")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.clientNoShows").value(1));
        assertEquals(AppointmentStatus.NO_SHOW, statusOf(past));

        mvc.perform(get("/worker/clients/" + sofia().getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No se presentó a 1 cita")));

        // Las demás citas del cliente avisan en la agenda
        Appointment next = appointment(5);
        mvc.perform(get("/worker/calendar/data").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments[?(@.id == " + next.getId() + ")].clientNoShows").value(1));
    }

    @Test
    void undoingTheNoShowMakesTheAppointmentCompletedAgain() throws Exception {
        Appointment past = appointment(-2);
        mvc.perform(post("/worker/appointments/" + past.getId() + "/no-show")
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf())).andExpect(status().isOk());

        mvc.perform(post("/worker/appointments/" + past.getId() + "/no-show").param("undo", "true")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(AppointmentStatus.COMPLETED, statusOf(past));
    }

    @Test
    void noShowNeedsAnAppointmentThatHasStarted() throws Exception {
        Appointment future = appointment(4);
        mvc.perform(post("/worker/appointments/" + future.getId() + "/no-show")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ya ha empezado")));
        assertEquals(AppointmentStatus.CONFIRMED, statusOf(future));

        mvc.perform(post("/worker/appointments/" + future.getId() + "/no-show").param("undo", "true")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void noShowRemovesThePointsOfThatAppointment() throws Exception {
        Center center = center1();
        center.setLoyaltyMode(LoyaltyMode.PER_EURO);
        center.setLoyaltyPointsPerUnit(1);
        center.setLoyaltyRewardText("Manicura gratis");
        center.setLoyaltySince(LocalDateTime.now().minusYears(5));
        centers.saveAndFlush(center);
        Appointment past = appointment(-1);
        loyaltyService.cardFor(center, sofia());
        assertTrue(movements.findRewardedAppointmentIds(center.getId(), sofia().getId()).contains(past.getId()));

        mvc.perform(post("/worker/appointments/" + past.getId() + "/no-show")
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).with(csrf())).andExpect(status().isOk());
        loyaltyService.cardFor(center, sofia());
        assertFalse(movements.findRewardedAppointmentIds(center.getId(), sofia().getId()).contains(past.getId()));
    }

    @Test
    void anotherCenterCannotMarkNoShow() throws Exception {
        Appointment past = appointment(-3);
        mvc.perform(post("/worker/appointments/" + past.getId() + "/no-show")
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(AppointmentStatus.COMPLETED, statusOf(past));
    }

    @Test
    void myBookingsSplitsUpcomingAndHistoryWithRebookLink() throws Exception {
        Appointment upcoming = appointment(6);
        Appointment past = appointment(-6);
        String treatmentId = String.valueOf(past.getTreatment().getId());

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Próximas citas")))
                .andExpect(content().string(containsString("Tu próxima cita")))
                .andExpect(content().string(containsString("Historial")))
                .andExpect(content().string(containsString("/client/centers/" + past.getCenter().getId() + "?tratamiento=" + treatmentId)))
                .andExpect(content().string(containsString("/client/appointments/" + upcoming.getId() + "/cancel")));
    }

    @Test
    void rebookLinkOpensTheBookingWithTheTreatmentChosen() throws Exception {
        Center center = center1();
        Treatment treatment = treatments.findByCenterId(center.getId()).getFirst();
        mvc.perform(get("/client/centers/" + center.getId()).param("tratamiento", String.valueOf(treatment.getId()))
                        .with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-rebook=\"true\"")))
                .andExpect(content().string(containsString("data-duration=\"" + treatment.getDuration() + "\" checked=\"checked\"")));

        // Un tratamiento de otro centro no se elige
        Center other = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getCenter();
        Long foreign = treatments.findByCenterId(other.getId()).getFirst().getId();
        mvc.perform(get("/client/centers/" + center.getId()).param("tratamiento", String.valueOf(foreign))
                        .with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-rebook=\"false\"")))
                .andExpect(content().string(not(matchesRegex("(?s).*data-duration=\"\\d+\" checked=\"checked\".*"))));
    }
}
