package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentChange;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Aceptar automáticamente las citas que piden los clientes: desactivado por defecto y lo activa cada centro. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutoAcceptBookingTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;

    /** Un martes lejano a la hora indicada, para no chocar con las citas de ejemplo ni con otros tests. */
    private static LocalDateTime farTuesday(int weeksAhead, int hour) {
        LocalDate tuesday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        return LocalDateTime.of(tuesday, LocalTime.of(hour, 0));
    }

    private Center centerOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getCenter();
    }

    private Center withAutoAccept(String workerEmail) {
        Center center = centerOf(workerEmail);
        center.setAutoAcceptBookings(true);
        return centers.saveAndFlush(center);
    }

    private Appointment book(Center center, LocalDateTime start) {
        User client = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        Long treatmentId = treatments.findByCenterId(center.getId()).getFirst().getId();
        return bookingService.requestBooking(client.getId(), center.getId(), treatmentId, start);
    }

    @Test
    void centersStartWithAutoAcceptOff() {
        assertTrue(centers.findAll().stream().noneMatch(Center::isAutoAcceptBookings));
        Appointment app = book(centerOf(TestUsers.WORKER_CENTER_1), farTuesday(30, 10));
        assertEquals(AppointmentStatus.PENDING, app.getStatus());
        assertNull(app.getWorker());
        assertEquals(AppointmentChange.REQUESTED, app.getLastChange());
    }

    @Test
    void bookingIsConfirmedWithAWorkerOfTheCenterWhenAutoAcceptIsOn() {
        Center center = withAutoAccept(TestUsers.WORKER_CENTER_1);
        Appointment app = book(center, farTuesday(31, 10));
        assertEquals(AppointmentStatus.CONFIRMED, app.getStatus());
        assertEquals(AppointmentChange.BOOKED, app.getLastChange());
        assertNotNull(app.getWorker());
        assertEquals(center.getId(), app.getWorker().getCenter().getId());
    }

    /** El centro 1 tiene 2 cabinas y 2 profesionales: dos citas a la misma hora van a profesionales distintos. */
    @Test
    void simultaneousBookingsGoToDifferentWorkers() {
        Center center = withAutoAccept(TestUsers.WORKER_CENTER_1);
        LocalDateTime slot = farTuesday(32, 11);
        Appointment first = book(center, slot);
        Appointment second = book(center, slot);
        assertEquals(AppointmentStatus.CONFIRMED, second.getStatus());
        assertNotEquals(first.getWorker().getId(), second.getWorker().getId());
    }

    @Test
    void workerTurnsAutoAcceptOnForOwnCenterOnly() throws Exception {
        mvc.perform(post("/worker/center/bookings").param("autoAccept", "true").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/worker/center#reservas"));

        assertTrue(centers.findById(centerOf(TestUsers.WORKER_CENTER_2).getId()).orElseThrow().isAutoAcceptBookings());
        assertFalse(centers.findById(centerOf(TestUsers.WORKER_CENTER_1).getId()).orElseThrow().isAutoAcceptBookings());

        mvc.perform(post("/worker/center/bookings").param("autoAccept", "false").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_2)))
                .andExpect(status().is3xxRedirection());
        assertFalse(centers.findById(centerOf(TestUsers.WORKER_CENTER_2).getId()).orElseThrow().isAutoAcceptBookings());
    }

    @Test
    void clientsCannotChangeTheSetting() throws Exception {
        mvc.perform(post("/worker/center/bookings").param("autoAccept", "true").with(csrf())
                        .with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void myCenterShowsTheSwitch() throws Exception {
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aceptar las citas automáticamente")));
    }
}
