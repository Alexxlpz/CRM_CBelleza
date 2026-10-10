package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Renderiza todas las vistas autenticadas. Detecta errores de plantilla y accesos perezosos
 * a relaciones JPA fuera de transacción (open-in-view está desactivado).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageRenderingTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @ParameterizedTest
    @ValueSource(strings = {"/worker/dashboard", "/worker/calendar", "/worker/inventory", "/worker/treatments",
            "/worker/clients", "/worker/clients/template", "/worker/center", "/centers", "/home"})
    void workerPagesRender(String path) throws Exception {
        mvc.perform(get(path).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
    }

    @Test
    void calendarIncludesWeekViewAndOpeningHours() throws Exception {
        mvc.perform(get("/worker/calendar").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"weekView\"")))
                .andExpect(content().string(containsString("data-calendar-view=\"week\"")))
                .andExpect(content().string(containsString("\"openHour\":9")));
    }

    @Test
    void workerCanOpenClientCardOfOwnCenter() throws Exception {
        Long sofiaId = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow().getId();
        mvc.perform(get("/worker/clients/" + sofiaId).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/centers", "/client/appointments", "/client/profile", "/home", "/features"})
    void clientPagesRender(String path) throws Exception {
        mvc.perform(get(path).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk());
    }

    @Test
    void clientCanOpenCenterDetails() throws Exception {
        Long centerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId();
        mvc.perform(get("/client/centers/" + centerId).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk());
    }

    @Test
    void unknownCenterShowsNotFoundPage() throws Exception {
        mvc.perform(get("/client/centers/999999").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string(containsString("No encontramos esta página")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/terms", "/terminos", "/cookies", "/politica-cookies"})
    void legalPagesRenderWithoutLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cuquora@hotmail.com")));
    }

    @Test
    void pagesIncludeOpenGraphTagsAndCookieNotice() throws Exception {
        mvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("property=\"og:image\" content=\"http://localhost/images/og-image.jpg\"")))
                .andExpect(content().string(containsString("id=\"cookieBanner\"")))
                .andExpect(content().string(containsString("href=\"/terms\"")));
    }
}
