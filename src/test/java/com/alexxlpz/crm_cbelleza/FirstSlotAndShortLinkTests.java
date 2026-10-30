package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.booking.CenterClock;
import com.alexxlpz.crm_cbelleza.entities.AgendaBlock;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.repositories.AgendaBlockRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterSearchService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * «Primer hueco» en las tarjetas del buscador y enlace corto de cada centro ({@code /c/nombre-del-centro}).
 * Cada test se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FirstSlotAndShortLinkTests {

    // Lunes 20 de octubre de 2025 a mediodía
    private static final LocalDateTime MONDAY = LocalDateTime.of(2025, 10, 20, 12, 0);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private AgendaBlockRepository blocks;
    @Autowired
    private CenterService centerService;
    @Autowired
    private Clock clock;

    private Center center() {
        return centers.findById(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> card(Center center) throws Exception {
        List<Map<String, Object>> all = JsonPath.read(mvc.perform(get("/api/centers?size=50"))
                .andReturn().getResponse().getContentAsString(), "$");
        return all.stream().filter(c -> center.getId().equals(((Number) c.get("id")).longValue())).findFirst().orElseThrow();
    }

    @Test
    void searchCardsShowTheFirstFreeSlot() throws Exception {
        assertNotNull(card(center()).get("firstSlot"));
    }

    @Test
    void aCenterClosedForTheNextTwoWeeksHasNoFirstSlot() throws Exception {
        Center center = center();
        LocalDateTime now = CenterClock.now(clock, center);
        blocks.save(AgendaBlock.builder().center(center).startsAt(now.minusHours(1)).endsAt(now.plusDays(16))
                .reason("Obras").build());
        assertNull(card(center).get("firstSlot"));

        // Si abre dentro de cinco días, ese es su primer hueco
        blocks.deleteAll(blocks.findByCenterIdAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(center.getId(), now, now.plusDays(17)));
        blocks.save(AgendaBlock.builder().center(center).startsAt(now.minusHours(1))
                .endsAt(now.toLocalDate().plusDays(5).atStartOfDay()).reason("Vacaciones").build());
        String label = (String) card(center).get("firstSlot");
        assertNotNull(label);
        assertFalse(label.startsWith("hoy") || label.startsWith("mañana"), label);
    }

    @Test
    void firstSlotLabels() {
        assertEquals("hoy a las 17:30", CenterSearchService.firstSlotLabel(MONDAY.withHour(17).withMinute(30), MONDAY));
        assertEquals("mañana a las 9:00", CenterSearchService.firstSlotLabel(MONDAY.plusDays(1).withHour(9), MONDAY));
        assertEquals("el jueves a las 10:00", CenterSearchService.firstSlotLabel(MONDAY.plusDays(3).withHour(10), MONDAY));
        assertEquals("el 3 de noviembre a las 10:00", CenterSearchService.firstSlotLabel(MONDAY.plusDays(14).withHour(10), MONDAY));
    }

    @Test
    void slugsAreLowercaseWithoutAccentsOrSymbols() {
        assertEquals("centro-historico", CenterService.slugOf("Centro Histórico"));
        assertEquals("peluqueria-ana-co", CenterService.slugOf("  Peluquería Ana & Co. "));
        assertEquals("", CenterService.slugOf("¡¡!!"));
    }

    @Test
    void theShortLinkOpensTheCenterPage() throws Exception {
        Center center = center();
        String slug = centerService.shortSlug(center);
        assertEquals(CenterService.slugOf(center.getName()), slug);

        // Sin sesión se ve la ficha pública: carta, opiniones y cada tratamiento enlaza a la reserva
        mvc.perform(get("/c/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(center.getName())))
                .andExpect(content().string(containsString("Opiniones de clientes")))
                .andExpect(content().string(containsString("Para reservar te pediremos que inicies sesión")))
                .andExpect(content().string(containsString("href=\"/client/centers/" + slug + "?tratamiento=")));
        mvc.perform(get("/c/" + slug.toUpperCase()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/c/" + slug));
        mvc.perform(get("/c/no-existe")).andExpect(status().isNotFound());

        // Un centro aún sin publicar no se abre
        center.setPublished(false);
        centers.saveAndFlush(center);
        mvc.perform(get("/c/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    void theShortLinkShowsAllTheReviewsWithoutLoggingIn() throws Exception {
        String slug = centerService.shortSlug(center());
        mvc.perform(get("/c/" + slug + "/opiniones").param("orden", "peores"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/c/" + slug + "#resenas\"")))
                .andExpect(content().string(containsString("href=\"/client/centers/" + slug + "\"")));
        mvc.perform(get("/c/no-existe/opiniones")).andExpect(status().isNotFound());
    }

    @Test
    void bookingFromThePublicPageAsksToLogIn() throws Exception {
        String slug = centerService.shortSlug(center());
        mvc.perform(get("/client/centers/" + slug).param("tratamiento", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void aWorkerSeesThePublicPageWithoutBookingLinks() throws Exception {
        String slug = centerService.shortSlug(center());
        mvc.perform(get("/c/" + slug).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Has entrado como trabajador")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/client/centers/" + slug))));
    }

    @Test
    void centersWithTheSameNameGetAnOrderNotTheirId() {
        Center older = center();
        String base = CenterService.slugOf(older.getName());
        Center twin = centers.saveAndFlush(Center.builder().name(older.getName()).address("Calle Nueva 1")
                .phone("600000000").email("gemelo@example.com").published(true).build());
        Center third = centers.saveAndFlush(Center.builder().name(older.getName().toUpperCase()).address("Calle Nueva 2")
                .phone("600000001").email("trillizo@example.com").published(true).build());
        assertEquals(base, centerService.shortSlug(older));
        assertEquals(base + "-2", centerService.shortSlug(twin));
        assertEquals(base + "-3", centerService.shortSlug(third));
        assertEquals(twin.getId(), centerService.findPublishedBySlug(base + "-2").orElseThrow().getId());
    }

    @Test
    void myCenterShowsTheLinkToCopy() throws Exception {
        Center center = center();
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enlace para reservar")))
                .andExpect(content().string(containsString("/c/" + centerService.shortSlug(center))));
    }

    @Test
    void theCenterPageOpensByNameNeverByItsId() throws Exception {
        Center center = center();
        String slug = centerService.shortSlug(center);
        mvc.perform(get("/client/centers/" + slug).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compartir")))
                .andExpect(content().string(containsString("data-share-path=\"/c/" + slug + "\"")));
        mvc.perform(get("/client/centers/" + slug.toUpperCase()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk());
        mvc.perform(get("/client/centers/" + center.getId()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/client/centers/" + center.getId() + "/opiniones").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isNotFound());
    }
}
