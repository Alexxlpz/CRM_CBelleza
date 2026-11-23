package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.DashboardService;
import com.alexxlpz.crm_cbelleza.services.DashboardService.FirstSteps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Primeros pasos» del panel de un centro recién dado de alta y el estado vacío de la carta con su botón. */
@SpringBootTest
@AutoConfigureMockMvc
class FirstStepsTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private DashboardService dashboardService;

    private record NewCenter(Center center, User owner, User worker) {
    }

    /** Como lo deja el alta: sin publicar, sin tratamientos ni foto, con su dueña y una persona del equipo. */
    private NewCenter newCenter() {
        Center center = centers.saveAndFlush(Center.builder().name("Centro " + UUID.randomUUID().toString().substring(0, 8))
                .address("Calle Mayor 1, Madrid").published(false).build());
        User owner = users.saveAndFlush(worker(center, "Dueña"));
        User worker = users.saveAndFlush(worker(center, "Equipo"));
        center.setOwnerId(owner.getId());
        return new NewCenter(centers.saveAndFlush(center), owner, worker);
    }

    private static User worker(Center center, String name) {
        return User.builder().name(name + " " + UUID.randomUUID()).email(UUID.randomUUID() + "@ejemplo.com")
                .password("{noop}no-se-usa").role(Role.WORKER).center(center).build();
    }

    private void addTreatment(Center center, boolean hidden) {
        Treatment template = treatments.findAll().getFirst();
        treatments.saveAndFlush(Treatment.builder().name("Manicura").description("Prueba").price(10.0)
                .duration(30).type(template.getType()).center(center).hidden(hidden).build());
    }

    @Test
    void ownerOfANewCenterSeesTheThreeSteps() throws Exception {
        NewCenter c = newCenter();

        mvc.perform(get("/worker/dashboard").with(user(AppUserDetails.from(c.owner()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primeros pasos")))
                .andExpect(content().string(containsString("Crea tu carta")))
                .andExpect(content().string(containsString("Pon tu foto")))
                .andExpect(content().string(containsString("En revisión")))
                .andExpect(content().string(containsString("href=\"/worker/center/centro#foto\"")));
    }

    @Test
    void theTeamOnlySeesWhetherTheCenterIsPublished() throws Exception {
        NewCenter c = newCenter();

        mvc.perform(get("/worker/dashboard").with(user(AppUserDetails.from(c.worker()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu centro")))
                .andExpect(content().string(containsString("En revisión")))
                .andExpect(content().string(not(containsString("Crea tu carta"))))
                .andExpect(content().string(not(containsString("Pon tu foto"))));
    }

    @Test
    void theCardGoesAwayWhenPublishedWithSomethingOnTheMenu() throws Exception {
        NewCenter c = newCenter();
        addTreatment(c.center(), false);
        Center center = centers.findById(c.center().getId()).orElseThrow();
        center.setPublished(true);
        centers.saveAndFlush(center);

        mvc.perform(get("/worker/dashboard").with(user(AppUserDetails.from(c.owner()))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("first-steps"))));
        mvc.perform(get("/worker/dashboard").with(user(AppUserDetails.from(c.worker()))))
                .andExpect(content().string(not(containsString("first-steps"))));
    }

    @Test
    void hiddenTreatmentsDoNotCountAsAMenu() {
        NewCenter c = newCenter();
        addTreatment(c.center(), true);
        assertFalse(dashboardService.firstStepsFor(c.center()).hasTreatments());

        addTreatment(c.center(), false);
        FirstSteps steps = dashboardService.firstStepsFor(c.center());
        assertTrue(steps.hasTreatments());
        assertFalse(steps.hasPhoto());
        assertFalse(steps.done(), "Sin publicar todavía no ha terminado");
    }

    @Test
    void demoCentersDoNotShowTheCard() throws Exception {
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("first-steps"))));
    }

    @Test
    void emptyMenuHasADrawingAndAButtonWithText() throws Exception {
        NewCenter c = newCenter();

        mvc.perform(get("/worker/treatments").with(user(AppUserDetails.from(c.owner()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu carta está vacía")))
                .andExpect(content().string(containsString("class=\"fs-art\"")))
                .andExpect(content().string(containsString("<i class=\"lucide-plus\" aria-hidden=\"true\"></i> Añadir tratamiento")));
        mvc.perform(get("/worker/treatments").with(user(AppUserDetails.from(c.worker()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no hay tratamientos")))
                .andExpect(content().string(not(containsString("Tu carta está vacía"))));
    }
}
