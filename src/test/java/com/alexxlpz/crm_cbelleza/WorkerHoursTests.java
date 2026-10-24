package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.AgendaSnapshot;
import com.alexxlpz.crm_cbelleza.booking.OpeningHours;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.WorkerHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
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
 * Horario de cada profesional: el dueño le pone uno propio en «Mi Centro» y las reservas, el selector de hora
 * y la agenda solo le dan citas dentro de él. Centro Histórico abre de lunes a viernes de 9 a 20 y el sábado de 10 a 14.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkerHoursTests {

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
    private WorkerHoursService workerHours;

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

    /** Un miércoles lejano a la hora indicada (el selector del cliente ve unas 30 semanas). */
    private static LocalDateTime farWednesday(int weeksAhead, int hour) {
        LocalDate day = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        return LocalDateTime.of(day, LocalTime.of(hour, 0));
    }

    /** Lucía, de lunes a viernes de 9 a 14. */
    private void luciaWorksMornings() throws Exception {
        MockHttpServletRequestBuilder request = post("/worker/center/team/" + lucia.getId() + "/horario").with(csrf())
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("custom", "true");
        for (int day = 1; day <= 5; day++) {
            request.param("hoursDay", String.valueOf(day)).param("hoursFrom", "09:00").param("hoursTo", "14:00");
        }
        mvc.perform(request)
                .andExpect(redirectedUrl("/worker/center#equipo"))
                .andExpect(flash().attribute("teamMessage", containsString("Horario de Lucía Romero guardado")));
    }

    private org.springframework.test.web.servlet.ResultActions book(String workerId, LocalDateTime at) throws Exception {
        return mvc.perform(post("/client/booking").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT))
                .param("centerId", center.getId().toString())
                .param("treatmentId", treatment.getId().toString())
                .param("workerId", workerId)
                .param("dateTime", at.toString()));
    }

    @Test
    void ownerGivesAWorkerHerOwnHoursAndTheTeamListShowsThem() throws Exception {
        mvc.perform(get("/worker/center/team/" + lucia.getId() + "/horario").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Horario de Lucía Romero")))
                .andExpect(content().string(containsString("Todo el horario del centro")));

        luciaWorksMornings();
        OpeningHours saved = workerHours.forWorker(lucia.getId()).orElseThrow();
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                saved.workingDays());
        assertEquals(List.of("Lunes a viernes: 09:00 - 14:00"), saved.describe());

        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("Lunes a viernes: 09:00 - 14:00")))
                .andExpect(content().string(containsString("/worker/center/team/" + lucia.getId() + "/horario")));

        // Vuelve a trabajar todo el horario del centro
        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/horario").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("custom", "false"))
                .andExpect(flash().attribute("teamMessage", containsString("vuelve a trabajar todo el horario")));
        assertTrue(workerHours.forWorker(lucia.getId()).isEmpty());
    }

    @Test
    void hoursOutsideTheCenterOrEmptyAreRejected() throws Exception {
        String url = "/worker/center/team/" + lucia.getId() + "/horario";
        // El sábado el centro abre de 10 a 14
        mvc.perform(post(url).with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("custom", "true")
                        .param("hoursDay", "6").param("hoursFrom", "09:00").param("hoursTo", "14:00"))
                .andExpect(redirectedUrl(url))
                .andExpect(flash().attribute("errorMessage", "El sábado Lucía Romero no puede trabajar de 09:00 - 14:00: el centro abre de 10:00 - 14:00."));
        mvc.perform(post(url).with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("custom", "true")
                        .param("hoursDay", "7").param("hoursFrom", "10:00").param("hoursTo", "12:00"))
                .andExpect(flash().attribute("errorMessage", containsString("el centro cierra ese día")));
        mvc.perform(post(url).with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)).param("custom", "true"))
                .andExpect(flash().attribute("errorMessage", "Marca al menos un día en el que trabaje Lucía Romero."));
        assertTrue(workerHours.forWorker(lucia.getId()).isEmpty());
    }

    @Test
    void onlyTheOwnerChangesTheHoursAndOnlyOfHisTeam() throws Exception {
        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/horario").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("custom", "true").param("hoursDay", "1").param("hoursFrom", "09:00").param("hoursTo", "10:00"))
                .andExpect(redirectedUrl("/worker/center"))
                .andExpect(flash().attribute("errorMessage", containsString("Carlos Mendoza")));
        mvc.perform(get("/worker/center/team/" + lucia.getId() + "/horario").with(TestUsers.as(users, LUCIA)))
                .andExpect(redirectedUrl("/worker/center"));
        assertTrue(workerHours.forWorker(lucia.getId()).isEmpty());

        // Elena (dueña de otro centro) no toca el horario de Lucía
        mvc.perform(post("/worker/center/team/" + lucia.getId() + "/horario").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2))
                        .param("custom", "true").param("hoursDay", "1").param("hoursFrom", "09:00").param("hoursTo", "10:00"))
                .andExpect(status().isNotFound());
        assertTrue(workerHours.forWorker(lucia.getId()).isEmpty());
    }

    @Test
    void clientsCanOnlyBookTheWorkerInsideHerHours() throws Exception {
        luciaWorksMornings();

        book(lucia.getId().toString(), farWednesday(22, 16))
                .andExpect(flash().attribute("bookingError", "Lucía Romero no trabaja a esa hora. Elige otra hora u otro profesional."));
        book(lucia.getId().toString(), farWednesday(22, 10))
                .andExpect(redirectedUrl("/client/appointments"));

        // El selector de hora marca como lleno lo que cae fuera de su horario
        mvc.perform(get("/api/centers/" + center.getId() + "/availability")
                        .param("treatmentId", treatment.getId().toString()).param("workerId", lucia.getId().toString()))
                .andExpect(jsonPath("$.occupied", hasItem(farWednesday(23, 16).toString())))
                .andExpect(jsonPath("$.occupied", not(hasItem(farWednesday(23, 11).toString()))));
        mvc.perform(get("/api/centers/" + center.getId() + "/availability")
                        .param("treatmentId", treatment.getId().toString()).param("workerId", carlos.getId().toString()))
                .andExpect(jsonPath("$.occupied", not(hasItem(farWednesday(23, 16).toString()))));
    }

    @Test
    void withAnyoneTheWorkerOffDutyDoesNotCount() throws Exception {
        luciaWorksMornings();
        LocalDateTime afternoon = farWednesday(24, 17);
        appointments.saveAndFlush(Appointment.builder()
                .dateTime(afternoon).treatment(treatment).center(center).client(sofia).worker(carlos)
                .status(AppointmentStatus.CONFIRMED).price(treatment.getPrice()).workerMessage("")
                .createdAt(LocalDateTime.now()).build());

        // Dos cabinas, pero por la tarde solo trabaja Carlos y ya está ocupado
        book("", afternoon).andExpect(flash().attribute("bookingError", containsString("ningún profesional libre")));
        mvc.perform(get("/api/centers/" + center.getId() + "/availability").param("treatmentId", treatment.getId().toString()))
                .andExpect(jsonPath("$.occupied", hasItem(afternoon.toString())));
    }

    @Test
    void theAgendaAsksBeforeGivingHerAnAppointmentOutsideHerHours() throws Exception {
        luciaWorksMornings();
        String evening = farWednesday(40, 17).toString();

        mvc.perform(post("/worker/appointments").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("clientId", sofia.getId().toString())
                        .param("treatmentId", treatment.getId().toString())
                        .param("workerId", lucia.getId().toString())
                        .param("dateTime", evening))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_HOURS"))
                .andExpect(jsonPath("$.message").value("Lucía Romero no trabaja a esa hora según su horario."));

        mvc.perform(post("/worker/appointments").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("clientId", sofia.getId().toString())
                        .param("treatmentId", treatment.getId().toString())
                        .param("workerId", lucia.getId().toString())
                        .param("dateTime", evening)
                        .param("allowOutsideHours", "true"))
                .andExpect(status().isOk());

        // La agenda enseña su horario en la cabecera de cada día
        mvc.perform(get("/worker/calendar").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("\"shifts\"")))
                .andExpect(content().string(containsString("09:00 - 14:00")));
    }

    @Test
    void snapshotLeavesOutWorkersOffDuty() {
        OpeningHours mornings = OpeningHours.uniform(30, Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(14, 0));
        AgendaSnapshot agenda = new AgendaSnapshot(2, List.of(carlos, lucia), List.of(), List.of(), List.of(),
                Map.of(lucia.getId(), mornings));
        LocalDateTime wednesday = farWednesday(5, 0);

        assertFalse(agenda.offDuty(lucia.getId(), wednesday.withHour(10), wednesday.withHour(11)));
        assertTrue(agenda.offDuty(lucia.getId(), wednesday.withHour(13).withMinute(30), wednesday.withHour(14).withMinute(30)));
        assertTrue(agenda.offDuty(lucia.getId(), wednesday.plusDays(1).withHour(10), wednesday.plusDays(1).withHour(11)));
        assertFalse(agenda.offDuty(carlos.getId(), wednesday.withHour(19), wednesday.withHour(20)));

        AgendaSnapshot.Request evening = new AgendaSnapshot.Request(wednesday.withHour(17), 60, lucia.getId(), null, false);
        assertEquals("Lucía Romero no trabaja a esa hora.", agenda.problem(evening).orElseThrow());
        assertTrue(agenda.ignoringWorkerHours().fits(evening));
        assertEquals(carlos.getId(), agenda.freeWorker(new AgendaSnapshot.Request(wednesday.withHour(17), 60, null, null, false))
                .orElseThrow().getId());
    }
}
