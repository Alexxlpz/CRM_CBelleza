package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Review;
import com.alexxlpz.crm_cbelleza.repositories.AppointmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.ReviewRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Lo que ve Google: datos estructurados de la ficha pública, sitemap.xml y robots.txt. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchEngineTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private CenterService centerService;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private ReviewRepository reviews;

    private Center center() {
        return users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter();
    }

    /** Una opinión de 5 estrellas sobre una cita del centro (que pasa a completada). */
    private void reviewed(Center center) {
        Appointment appointment = appointments.findAll().stream()
                .filter(a -> a.getCenter().getId().equals(center.getId()) && a.getClient() != null)
                .filter(a -> reviews.findAll().stream().noneMatch(r -> r.getAppointment().getId().equals(a.getId())))
                .findFirst().orElseThrow();
        appointment.setStatus(AppointmentStatus.COMPLETED);
        reviews.saveAndFlush(Review.builder().appointment(appointment).center(center).client(appointment.getClient())
                .rating(5).comment("Muy bien").createdAt(LocalDateTime.now().minusDays(1)).build());
    }

    private String jsonLd(String html) {
        Matcher m = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>", Pattern.DOTALL).matcher(html);
        assertTrue(m.find(), "La ficha no lleva datos estructurados");
        return m.group(1);
    }

    @Test
    void thePublicPageDescribesTheSalonForGoogle() throws Exception {
        Center center = center();
        reviewed(center);
        String slug = centerService.shortSlug(center);
        String html = mvc.perform(get("/c/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"http://localhost/c/" + slug + "\">")))
                .andReturn().getResponse().getContentAsString();
        String json = jsonLd(html);

        assertEquals("BeautySalon", JsonPath.read(json, "$['@type']"));
        assertEquals(center.getName(), JsonPath.read(json, "$.name"));
        assertEquals("http://localhost/c/" + slug, JsonPath.read(json, "$.url"));
        assertEquals(center.getAddress(), JsonPath.read(json, "$.address.streetAddress"));
        assertTrue(((Number) JsonPath.read(json, "$.aggregateRating.reviewCount")).intValue() > 0);
        assertEquals(5, ((Number) JsonPath.read(json, "$.aggregateRating.bestRating")).intValue());
        List<String> days = JsonPath.read(json, "$.openingHoursSpecification[*].dayOfWeek");
        assertTrue(days.contains("https://schema.org/Monday"), days.toString());
        List<String> currencies = JsonPath.read(json, "$.hasOfferCatalog.itemListElement[*].priceCurrency");
        assertFalse(currencies.isEmpty());
        assertTrue(currencies.stream().allMatch("EUR"::equals));
    }

    @Test
    void aCenterWithoutReviewsHasNoStars() throws Exception {
        Center empty = centers.saveAndFlush(Center.builder().name("Estética Nueva Prueba").address("Calle Nueva 1")
                .phone("600000000").email("nueva@example.com").published(true).build());
        String html = mvc.perform(get("/c/" + centerService.shortSlug(empty)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(jsonLd(html).contains("aggregateRating"));
    }

    @Test
    void textCannotCloseTheScriptBlock() throws Exception {
        Center center = center();
        center.setName("Centro </script><b>x");
        centers.saveAndFlush(center);
        String html = mvc.perform(get("/c/" + centerService.shortSlug(center)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("Centro </script><b>x", JsonPath.read(jsonLd(html), "$.name"));
    }

    @Test
    void theSitemapListsPublishedCentersOnly() throws Exception {
        Center hidden = centers.saveAndFlush(Center.builder().name("Centro Oculto Prueba").address("Calle 2")
                .phone("600000001").email("oculto@example.com").published(false).build());
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<loc>http://localhost/c/" + centerService.shortSlug(center()) + "</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost/centers</loc>")))
                .andExpect(content().string(not(containsString(centerService.shortSlug(hidden)))));
    }

    @Test
    void robotsPointsToTheSitemap() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /client/")))
                .andExpect(content().string(containsString("Sitemap: http://localhost/sitemap.xml")));
    }
}
