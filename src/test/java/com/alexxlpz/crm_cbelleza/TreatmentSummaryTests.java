package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resumen del tratamiento realizado: solo en citas que ya han empezado, y los avisos salen en las demás citas del cliente. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TreatmentSummaryTests {

    private static final String SUMMARY = "El sérum de vitamina C le irritó las mejillas: no volver a usarlo.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private AppointmentRepository appointments;

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

    private org.springframework.test.web.servlet.ResultActions saveSummary(Appointment app, String summary, boolean alert) throws Exception {
        return mvc.perform(post("/worker/appointments/" + app.getId() + "/summary").with(csrf())
                .param("summary", summary)
                .param("alert", String.valueOf(alert))
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
    }

    @Test
    void alertSummaryIsSavedAndShownInTheClientsOtherAppointments() throws Exception {
        Appointment past = confirmedAt(LocalDateTime.now().minusDays(3).withHour(10).withMinute(0));
        Appointment next = confirmedAt(LocalDateTime.now().plusDays(30).withHour(10).withMinute(0));

        saveSummary(past, "  " + SUMMARY + "  ", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatmentSummary").value(SUMMARY))
                .andExpect(jsonPath("$.treatmentAlert").value(true));

        Appointment saved = appointments.findById(past.getId()).orElseThrow();
        assertEquals(carlos.getId(), saved.getSummaryUpdatedBy().getId());
        assertNotNull(saved.getSummaryUpdatedAt());

        mvc.perform(get("/worker/calendar/data").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments[?(@.id == " + next.getId() + ")].clientAlerts[*].summary", hasItem(SUMMARY)))
                .andExpect(jsonPath("$.appointments[?(@.id == " + next.getId() + ")].summaryEditable", hasItem(false)));

        mvc.perform(get("/worker/clients/" + sofia.getId()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avisos de este cliente")))
                .andExpect(content().string(containsString("no volver a usarlo")));
    }

    @Test
    void emptySummaryClearsIt() throws Exception {
        Appointment past = confirmedAt(LocalDateTime.now().minusDays(2).withHour(11).withMinute(0));
        saveSummary(past, SUMMARY, true).andExpect(status().isOk());

        saveSummary(past, "   ", false).andExpect(status().isOk());

        Appointment saved = appointments.findById(past.getId()).orElseThrow();
        assertNull(saved.getTreatmentSummary());
        assertFalse(saved.isTreatmentAlert());
    }

    @Test
    void futureAppointmentsAndEmptyAlertsAreRejected() throws Exception {
        Appointment future = confirmedAt(LocalDateTime.now().plusDays(20).withHour(12).withMinute(0));
        saveSummary(future, SUMMARY, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ya ha empezado")));

        Appointment past = confirmedAt(LocalDateTime.now().minusDays(1).withHour(12).withMinute(0));
        saveSummary(past, "", true).andExpect(status().isConflict());
        saveSummary(past, "x".repeat(Appointment.TREATMENT_SUMMARY_MAX_LENGTH + 1), false).andExpect(status().isConflict());
        assertNull(appointments.findById(past.getId()).orElseThrow().getTreatmentSummary());
    }
}
