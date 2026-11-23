package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.dto.ClientCardFieldDTO;
import com.alexxlpz.crm_cbelleza.entities.ClientCardTemplate;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.ClientCardTemplateRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ClientCardTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Centro sin plantilla de ficha técnica (un centro recién dado de alta): las pantallas de solo lectura usan la plantilla
 * por defecto sin guardarla. Antes la guardaban dentro de una transacción readOnly y en PostgreSQL el panel, la agenda y la
 * ficha del cliente daban error 500 (H2 no hace cumplir el readOnly, por eso se comprueba que no se escribe nada).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewCenterTemplateTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private ClientCardTemplateRepository templates;
    @Autowired
    private ClientCardTemplateService templateService;

    private Long centerId;
    private String savedJson;

    @BeforeEach
    void removeTheTemplateOfCenter1() {
        centerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId();
        templates.findByCenterId(centerId).ifPresent(template -> {
            savedJson = template.getFieldsJson();
            templates.delete(template);
        });
        templates.flush();
    }

    @AfterEach
    void restoreTheTemplate() {
        templates.findByCenterId(centerId).ifPresent(templates::delete);
        templates.flush();
        if (savedJson != null) {
            User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
            templates.saveAndFlush(ClientCardTemplate.builder().center(carlos.getCenter()).fieldsJson(savedJson).build());
        }
    }

    @Test
    void withoutATemplateTheDefaultFieldsAreReturnedWithoutSavingAnything() {
        List<ClientCardFieldDTO> fields = templateService.getFields(centerId);

        assertEquals(4, fields.size());
        assertEquals("alergias_sensibilidades", fields.get(1).getId());
        assertTrue(templates.findByCenterId(centerId).isEmpty());
    }

    @Test
    void dashboardCalendarAndClientCardOpenWithoutATemplate() throws Exception {
        Long sofia = users.findByEmailIgnoreCase("sofia@gmail.com").orElseThrow().getId();

        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
        mvc.perform(get("/worker/calendar").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
        mvc.perform(get("/worker/calendar/data").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());
        mvc.perform(get("/worker/clients/" + sofia).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk());

        assertTrue(templates.findByCenterId(centerId).isEmpty(), "Abrir una pantalla no debe crear la plantilla");
    }

    @Test
    void savingTheTemplateEditorStillCreatesIt() {
        templateService.saveFields(centerId, List.of("color"), List.of("Color favorito"), List.of("text"), List.of(""));

        assertTrue(templates.findByCenterId(centerId).isPresent());
        assertEquals("Color favorito", templateService.getFields(centerId).getFirst().getLabel());
    }
}
