package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .andExpect(status().isNotFound());
    }
}
