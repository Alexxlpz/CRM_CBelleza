package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.TreatmentService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ofertas de tratamientos: las pone el trabajador, se cobran al reservar y salen en la reserva y en el buscador. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TreatmentOfferTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private TreatmentService treatmentService;
    @Autowired
    private BookingService bookingService;

    private Long centerId;
    private Treatment treatment;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        centerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId();
        treatment = treatments.findByCenterId(centerId).getFirst();
        today = treatmentService.todayAt(centerId);
    }

    private org.springframework.test.web.servlet.ResultActions saveOffer(Object price, String endsOn) throws Exception {
        return mvc.perform(post("/worker/treatments/" + treatment.getId() + "/offer").with(csrf())
                .param("offerPrice", String.valueOf(price))
                .param("endsOn", endsOn)
                .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)));
    }

    private static LocalDateTime farMonday(int weeksAhead, int hour) {
        LocalDate monday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.of(hour, 0));
    }

    @Test
    void workerPutsTreatmentOnOffer() throws Exception {
        double offer = treatment.getPrice() - 5;
        saveOffer(offer, today.plusDays(7).toString())
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Oferta guardada")));

        Treatment saved = treatments.findById(treatment.getId()).orElseThrow();
        assertEquals(offer, saved.getOfferPrice());
        assertEquals(today.plusDays(7), saved.getOfferEndsOn());
        assertTrue(saved.hasActiveOffer(today));
        assertFalse(saved.hasActiveOffer(today.plusDays(8)));
    }

    @Test
    void offerWithoutEndDateLastsUntilRemoved() throws Exception {
        saveOffer(treatment.getPrice() - 1, "").andExpect(status().is3xxRedirection());
        assertTrue(treatments.findById(treatment.getId()).orElseThrow().hasActiveOffer(today.plusYears(1)));

        mvc.perform(post("/worker/treatments/" + treatment.getId() + "/offer/delete").with(csrf())
                        .with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().is3xxRedirection());
        Treatment saved = treatments.findById(treatment.getId()).orElseThrow();
        assertNull(saved.getOfferPrice());
        assertNull(saved.getOfferEndsOn());
    }

    @Test
    void offerMustBeCheaperAndNotEndInThePast() throws Exception {
        saveOffer(treatment.getPrice(), "")
                .andExpect(flash().attribute("errorMessage", containsString("menor que el de carta")));
        saveOffer(0, "")
                .andExpect(flash().attribute("errorMessage", containsString("precio de oferta")));
        saveOffer(treatment.getPrice() - 1, today.minusDays(1).toString())
                .andExpect(flash().attribute("errorMessage", containsString("ya ha pasado")));
        saveOffer(treatment.getPrice() - 1, "31/12")
                .andExpect(flash().attribute("errorMessage", containsString("no es válida")));

        assertNull(treatments.findById(treatment.getId()).orElseThrow().getOfferPrice());
    }

    @Test
    void bookingChargesTheOfferPriceWhileItLasts() {
        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        treatment.setOfferPrice(treatment.getPrice() - 10);
        treatment.setOfferEndsOn(today);
        Appointment withOffer = bookingService.requestBooking(sofia.getId(), centerId, treatment.getId(), farMonday(30, 10));
        assertEquals(treatment.getOfferPrice(), withOffer.getPrice());

        treatment.setOfferEndsOn(today.minusDays(1));
        Appointment expired = bookingService.requestBooking(sofia.getId(), centerId, treatment.getId(), farMonday(31, 10));
        assertEquals(treatment.getPrice(), expired.getPrice());
    }

    @Test
    void clientSeesOffersWhenBooking() throws Exception {
        treatment.setOfferPrice(treatment.getPrice() - 6);
        treatment.setOfferEndsOn(null);
        mvc.perform(get("/client/centers/" + centerId).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("En oferta")))
                .andExpect(content().string(containsString("treatment-card-old-price")));
    }

    @Test
    void centerSearchListsActiveOffersAndFiltersByThem() throws Exception {
        treatment.setOfferPrice(treatment.getPrice() / 2);
        treatment.setOfferEndsOn(today.plusDays(3));
        treatments.flush();

        mvc.perform(get("/api/centers").param("withOffers", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(centerId.intValue())))
                .andExpect(jsonPath("$[*].offers.length()").value(everyItem(not(0))))
                .andExpect(jsonPath("$[?(@.id == " + centerId + ")].offers[0].discountPercent").value(hasItem(50)));

        treatment.setOfferEndsOn(today.minusDays(1));
        treatments.flush();
        mvc.perform(get("/api/centers").param("withOffers", "true"))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(centerId.intValue()))));
    }

    @Test
    void workerTreatmentsPageShowsOfferActions() throws Exception {
        treatment.setOfferPrice(treatment.getPrice() - 2);
        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar oferta")))
                .andExpect(content().string(containsString("Poner en oferta")))
                .andExpect(content().string(containsString("treatmentOfferModal")));
    }
}
