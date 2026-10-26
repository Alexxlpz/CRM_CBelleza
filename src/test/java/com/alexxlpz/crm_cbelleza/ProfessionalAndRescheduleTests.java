package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Elegir profesional al reservar (y quién hace cada tratamiento) y cambiar la hora desde «Mis reservas».
 * Centro Histórico: dos cabinas, Carlos (dueño) y Lucía.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfessionalAndRescheduleTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private CenterService centerService;

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

    private User carlos;
    private User lucia;
    private User sofia;
    private Center center;
    private Treatment treatment;

    @BeforeEach
    void setUp() {
        carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        lucia = users.findByEmailIgnoreCase(LUCIA).orElseThrow();
        sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        center = centers.findById(carlos.getCenter().getId()).orElseThrow();
        treatment = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60).findFirst().orElseThrow();
    }

    /** Un miércoles lejano a la hora indicada, para no chocar con las citas de ejemplo (el selector ve unas 30 semanas). */
    private static LocalDateTime farWednesday(int weeksAhead, int hour) {
        LocalDate day = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        return LocalDateTime.of(day, LocalTime.of(hour, 0));
    }

    private Appointment confirmed(LocalDateTime at, User worker) {
        return appointments.saveAndFlush(Appointment.builder()
                .dateTime(at)
                .treatment(treatment)
                .center(center)
                .client(sofia)
                .worker(worker)
                .status(AppointmentStatus.CONFIRMED)
                .price(treatment.getPrice())
                .workerMessage("")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Appointment reload(Appointment app) {
        return appointments.findById(app.getId()).orElseThrow();
    }

    private void onlyLuciaDoesIt() throws Exception {
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/workers").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("workerIds", lucia.getId().toString()))
                .andExpect(redirectedUrl("/worker/treatments"));
    }

    private org.springframework.test.web.servlet.ResultActions book(String workerId, LocalDateTime at) throws Exception {
        return mvc.perform(post("/client/booking").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                .param("centerId", center.getId().toString())
                .param("treatmentId", treatment.getId().toString())
                .param("workerId", workerId)
                .param("dateTime", at.toString()));
    }

    private Appointment lastBookingOfSofia() {
        return appointments.findByClientIdOrderByDateTimeDesc(sofia.getId()).stream()
                .filter(app -> app.getLastChange() == AppointmentChange.REQUESTED || app.getLastChange() == AppointmentChange.BOOKED)
                .max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
    }

    @Test
    void ownerChoosesWhoDoesATreatmentAndClientsOnlySeeThem() throws Exception {
        onlyLuciaDoesIt();
        assertEquals(Set.of(lucia.getId()), treatments.findById(treatment.getId()).orElseThrow().getWorkerIds());

        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Quién lo hace")))
                .andExpect(content().string(containsString("Todo el equipo")));
        mvc.perform(get("/client/centers/" + centerService.shortSlug(center)).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("¿Con quién?")))
                .andExpect(content().string(containsString("data-workers=\"" + lucia.getId() + "\"")));

        // Marcar a todo el equipo es lo mismo que no marcar a nadie
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/workers").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                .param("workerIds", carlos.getId().toString(), lucia.getId().toString()));
        assertTrue(treatments.findById(treatment.getId()).orElseThrow().getWorkerIds().isEmpty());
    }

    @Test
    void bookingWithAWorkerWhoDoesNotDoTheTreatmentIsRejected() throws Exception {
        onlyLuciaDoesIt();
        book(carlos.getId().toString(), farWednesday(31, 10))
                .andExpect(flash().attribute("bookingError", containsString("no hace")));

        book(lucia.getId().toString(), farWednesday(31, 10))
                .andExpect(redirectedUrl("/client/appointments"));
        Appointment app = lastBookingOfSofia();
        assertEquals(AppointmentStatus.PENDING, app.getStatus());
        assertEquals(lucia.getId(), app.getWorker().getId());
    }

    @Test
    void chosenWorkerIsKeptWhenTheCenterAutoAccepts() throws Exception {
        center.setAutoAcceptBookings(true);
        centers.saveAndFlush(center);
        book(lucia.getId().toString(), farWednesday(32, 11)).andExpect(redirectedUrl("/client/appointments"));
        Appointment app = lastBookingOfSofia();
        assertEquals(AppointmentStatus.CONFIRMED, app.getStatus());
        assertEquals(lucia.getId(), app.getWorker().getId());
    }

    @Test
    void busyChosenWorkerBlocksTheSlotButAnyoneStillWorks() throws Exception {
        LocalDateTime at = farWednesday(20, 12);
        confirmed(at, carlos);

        book(carlos.getId().toString(), at)
                .andExpect(flash().attribute("bookingError", containsString("no está libre")));
        book("", at).andExpect(redirectedUrl("/client/appointments"));
        assertNull(lastBookingOfSofia().getWorker());

        String slot = at.toString();
        mvc.perform(get("/api/centers/" + center.getId() + "/availability")
                        .param("treatmentId", treatment.getId().toString()).param("workerId", carlos.getId().toString()))
                .andExpect(jsonPath("$.occupied", hasItem(slot)));
        mvc.perform(get("/api/centers/" + center.getId() + "/availability")
                        .param("treatmentId", treatment.getId().toString()).param("workerId", lucia.getId().toString()))
                .andExpect(jsonPath("$.occupied", not(hasItem(slot))));
    }

    @Test
    void restrictedTreatmentNeedsOneOfItsWorkersFree() throws Exception {
        onlyLuciaDoesIt();
        LocalDateTime at = farWednesday(21, 15);
        confirmed(at, lucia);
        // Carlos está libre, pero no hace el tratamiento
        book("", at).andExpect(flash().attribute("bookingError", containsString("ningún profesional libre")));
        mvc.perform(get("/api/centers/" + center.getId() + "/availability").param("treatmentId", treatment.getId().toString()))
                .andExpect(jsonPath("$.occupied", hasItem(at.toString())));
    }

    @Test
    void clientMovesAConfirmedAppointmentAndItStaysConfirmed() throws Exception {
        LocalDateTime before = farWednesday(35, 10);
        LocalDateTime after = before.withHour(16);
        Appointment app = confirmed(before, carlos);

        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("/client/appointments/" + app.getId() + "/cambiar")));
        mvc.perform(get("/client/appointments/" + app.getId() + "/cambiar").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cambiar la hora de tu cita")))
                .andExpect(content().string(containsString("data-changing=\"" + app.getId() + "\"")));

        mvc.perform(post("/client/appointments/" + app.getId() + "/cambiar").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("workerId", "").param("dateTime", after.toString()))
                .andExpect(redirectedUrl("/client/appointments"))
                .andExpect(flash().attribute("successMessage", containsString("Hemos avisado")));

        Appointment moved = reload(app);
        assertEquals(AppointmentStatus.CONFIRMED, moved.getStatus());
        assertEquals(after, moved.getDateTime());
        assertEquals(before, moved.getPreviousDateTime());
        assertEquals(carlos.getId(), moved.getWorker().getId(), "Con «Cualquiera» se queda quien la tenía si está libre");
        assertEquals(AppointmentChange.RESCHEDULED_BY_CLIENT, moved.getLastChange());

        // El centro lo ve en la campana
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("ha cambiado su cita")));
    }

    @Test
    void clientCanChangeTheWorkerOfAConfirmedAppointment() throws Exception {
        LocalDateTime at = farWednesday(36, 10);
        Appointment app = confirmed(at, carlos);
        mvc.perform(post("/client/appointments/" + app.getId() + "/cambiar").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("workerId", lucia.getId().toString()).param("dateTime", at.toString()))
                .andExpect(redirectedUrl("/client/appointments"));
        assertEquals(lucia.getId(), reload(app).getWorker().getId());
    }

    @Test
    void movingToAHourWhereTheWorkerIsBusyFails() throws Exception {
        LocalDateTime at = farWednesday(37, 10);
        Appointment app = confirmed(at, carlos);
        confirmed(at.withHour(13), carlos);
        mvc.perform(post("/client/appointments/" + app.getId() + "/cambiar").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("workerId", carlos.getId().toString()).param("dateTime", at.withHour(13).toString()))
                .andExpect(redirectedUrl("/client/appointments/" + app.getId() + "/cambiar"))
                .andExpect(flash().attribute("bookingError", containsString("no está libre")));
        assertEquals(at, reload(app).getDateTime());
    }

    @Test
    void aPendingRequestStaysPendingAtTheNewTime() throws Exception {
        book(lucia.getId().toString(), farWednesday(38, 10));
        Appointment app = lastBookingOfSofia();
        LocalDateTime after = farWednesday(38, 17);
        mvc.perform(post("/client/appointments/" + app.getId() + "/cambiar").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("workerId", "").param("dateTime", after.toString()))
                .andExpect(redirectedUrl("/client/appointments"));
        Appointment moved = reload(app);
        assertEquals(AppointmentStatus.PENDING, moved.getStatus());
        assertEquals(after, moved.getDateTime());
        assertNull(moved.getWorker());
    }

    @Test
    void theCenterDecidesHowLateTheClientCanChange() throws Exception {
        Appointment soon = confirmed(LocalDateTime.now().plusHours(5).withMinute(0).withSecond(0).withNano(0), carlos);
        Appointment later = confirmed(farWednesday(39, 10), carlos);

        // Por defecto, hasta 24 h antes: la de dentro de 5 h ya no
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(content().string(containsString("/client/appointments/" + later.getId() + "/cambiar")))
                .andExpect(content().string(not(containsString("/client/appointments/" + soon.getId() + "/cambiar"))))
                .andExpect(content().string(containsString("Para cambiar la hora, llama a")));
        mvc.perform(get("/client/appointments/" + soon.getId() + "/cambiar").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(redirectedUrl("/client/appointments"))
                .andExpect(flash().attribute("errorMessage", containsString("24 h antes")));

        // El dueño lo quita del todo
        mvc.perform(post("/worker/center/reschedule").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("hours", "0"))
                .andExpect(redirectedUrl("/worker/center#reservas"));
        assertEquals(0, centers.findById(center.getId()).orElseThrow().getClientRescheduleHours());
        mvc.perform(post("/client/appointments/" + later.getId() + "/cambiar").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                        .param("dateTime", later.getDateTime().withHour(12).toString()))
                .andExpect(flash().attribute("bookingError", containsString("no deja cambiar la hora")));
        assertEquals(10, reload(later).getDateTime().getHour());
    }

    @Test
    void anotherClientCannotChangeTheAppointment() throws Exception {
        Appointment app = confirmed(farWednesday(40, 10), carlos);
        User other = users.findAll().stream()
                .filter(u -> u.getRole() == Role.CLIENT && !u.getId().equals(sofia.getId()))
                .findFirst().orElseThrow();
        mvc.perform(get("/client/appointments/" + app.getId() + "/cambiar")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                                com.alexxlpz.crm_cbelleza.security.AppUserDetails.from(other))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerChangesWhoDoesTreatmentsAndTheRescheduleLimit() throws Exception {
        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/workers").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("workerIds", lucia.getId().toString()))
                .andExpect(redirectedUrl("/worker/treatments"))
                .andExpect(flash().attribute("errorMessage", containsString("Carlos Mendoza")));
        mvc.perform(post("/worker/center/reschedule").with(csrf()).with(TestUsers.as(users, LUCIA)).param("hours", "0"))
                .andExpect(redirectedUrl("/worker/center"));
        assertTrue(treatments.findById(treatment.getId()).orElseThrow().getWorkerIds().isEmpty());
        assertEquals(24, centers.findById(center.getId()).orElseThrow().getClientRescheduleHours());
    }
}
