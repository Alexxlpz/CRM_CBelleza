package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portada pensada para clientes (buscar un centro, tres dibujos, sin cifras inventadas ni centro de ejemplo),
 * «Mis reservas» con las citas antes que los puntos e inventario y carta sin el botón flotante.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LandingAndListsTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    @Test
    void landingSendsVisitorsToTheSearchFirst() throws Exception {
        String html = mvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pide cita en tu centro de belleza")))
                .andExpect(content().string(containsString("href=\"/centers\"")))
                .andExpect(content().string(containsString("Tengo un centro")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("CENTRO EJEMPLO"))))
                .andExpect(content().string(not(containsString("98.6%"))))
                .andExpect(content().string(not(containsString("metrics-bar"))))
                .andExpect(content().string(not(containsString("100% garantizado"))))
                .andReturn().getResponse().getContentAsString();

        assertEquals(3, html.split("class=\"fs-art\"", -1).length - 1, "Tres dibujos en «Así de fácil»");
        assertTrue(html.indexOf("Buscar un centro") < html.indexOf("Tengo un centro"), "Buscar un centro es el botón principal");
    }

    @Test
    void loggedInClientOnlySeesTheSearchButton() throws Exception {
        mvc.perform(get("/home").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Buscar un centro")))
                .andExpect(content().string(not(containsString("Tengo un centro"))))
                .andExpect(content().string(not(containsString("¿Ya tienes cuenta?"))));
    }

    @Test
    void upcomingAppointmentsGoBeforeThePoints() throws Exception {
        String html = mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"client-loyalty\"")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.indexOf("class=\"appointments-list\"") < html.indexOf("class=\"client-loyalty\""),
                "Primero las citas y el historial, luego los puntos");
    }

    @Test
    void inventoryAndMenuHaveATextButtonAndNoFloatingPlus() throws Exception {
        mvc.perform(get("/worker/inventory").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-add-product")))
                .andExpect(content().string(not(containsString("fab-add-btn"))));
        mvc.perform(get("/worker/treatments").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("btn-add-treatment")))
                .andExpect(content().string(not(containsString("fab-add-btn"))));
    }
}
