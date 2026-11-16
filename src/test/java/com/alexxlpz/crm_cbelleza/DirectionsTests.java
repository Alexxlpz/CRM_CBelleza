package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.mail.AppointmentMailService;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Cómo llegar»: enlace al mapa en la ficha del centro, en «Mis citas» y en los correos de cita confirmada. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectionsTests {

    private static final String GOOGLE = "https://www.google.com/maps/dir/?api=1&amp;destination=";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private CenterService centerService;
    @Autowired
    private BookingService booking;
    @Autowired
    private AppointmentMailService mail;

    @MockitoBean
    private EmailGateway emailGateway;

    private Center centerOfCarlos() {
        return centers.findById(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId()).orElseThrow();
    }

    /* ---------- Enlaces ---------- */

    @Test
    void coordinatesAreUsedWhenTheCenterHasThem() {
        Center center = Center.builder().name("Prueba").address("Calle Mayor 45, Madrid").latitude(40.4155).longitude(-3.7074).build();
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=40.415500,-3.707400", center.getDirectionsUrl());
        assertEquals("https://maps.apple.com/?daddr=40.415500,-3.707400", center.getAppleMapsUrl());
    }

    @Test
    void theAddressIsEncodedWhenThereAreNoCoordinates() {
        Center center = Center.builder().name("Prueba").address(" Calle Mayor 45 & 2º, Madrid ").build();
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=Calle+Mayor+45+%26+2%C2%BA%2C+Madrid",
                center.getDirectionsUrl());
        assertEquals("https://maps.apple.com/?daddr=Calle+Mayor+45+%26+2%C2%BA%2C+Madrid", center.getAppleMapsUrl());
    }

    @Test
    void noLinkWithoutCoordinatesOrAddress() {
        Center center = Center.builder().name("Prueba").address("   ").latitude(40.4).build();
        assertNull(center.getDirectionsUrl());
        assertNull(center.getAppleMapsUrl());
    }

    /* ---------- Pantallas ---------- */

    @Test
    void centerPageShowsTheButton() throws Exception {
        Center center = centerOfCarlos();
        mvc.perform(get("/client/centers/" + centerService.shortSlug(center)).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero-directions")))
                .andExpect(content().string(containsString(GOOGLE)))
                .andExpect(content().string(containsString("data-apple-maps-url=\"https://maps.apple.com/?daddr=")));
    }

    @Test
    void publicCenterPageShowsTheButtonWithoutLogin() throws Exception {
        Center center = centerOfCarlos();
        mvc.perform(get("/c/" + centerService.shortSlug(center)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero-directions")));
    }

    @Test
    void centerWithoutLocationHasNoButton() throws Exception {
        Center center = centerOfCarlos();
        center.setAddress(null);
        center.setLatitude(null);
        center.setLongitude(null);
        centers.saveAndFlush(center);
        mvc.perform(get("/client/centers/" + centerService.shortSlug(center)).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("hero-directions"))))
                .andExpect(content().string(containsString("Compartir")));
    }

    @Test
    void upcomingAppointmentsShowTheButtonNextToCancel() throws Exception {
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("appointment-directions")))
                .andExpect(content().string(containsString("aria-label=\"Cómo llegar a ")));
    }

    /* ---------- Correos ---------- */

    private Appointment pendingRequest(Center center, int weeksAhead) {
        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        Treatment treatment = treatments.findByCenterId(center.getId()).stream()
                .filter(t -> t.getDuration() <= 60).findFirst().orElseThrow();
        LocalDate day = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return booking.requestBooking(sofia.getId(), center.getId(), treatment.getId(), LocalDateTime.of(day, LocalTime.of(10, 0)));
    }

    @Test
    void confirmationEmailToTheClientHasTheLink() {
        Center center = centerOfCarlos();
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        Appointment request = pendingRequest(center, 70);
        booking.approve(center.getId(), request.getId(), carlos.getId(), null, null);
        clearInvocations(emailGateway);
        mail.notifyChange(request.getId(), AppointmentChange.CONFIRMED);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, atLeastOnce()).send(sent.capture());
        EmailMessage toClient = sent.getAllValues().stream().filter(m -> m.subject().startsWith("Cita confirmada")).findFirst().orElseThrow();
        assertTrue(toClient.htmlBody().contains("Cómo llegar"), toClient.htmlBody());
        assertTrue(toClient.htmlBody().contains(GOOGLE));
    }

    @Test
    void requestEmailToTheCenterHasNoLink() {
        Center center = centerOfCarlos();
        Appointment request = pendingRequest(center, 71);
        clearInvocations(emailGateway);
        mail.notifyChange(request.getId(), AppointmentChange.REQUESTED);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, atLeastOnce()).send(sent.capture());
        sent.getAllValues().forEach(m -> assertFalse(m.htmlBody().contains("Cómo llegar"), m.subject()));
    }
}
