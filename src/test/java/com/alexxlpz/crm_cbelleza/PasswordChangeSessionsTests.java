package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.PasswordResetRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambiar o restablecer la contraseña cierra las demás sesiones abiertas de la cuenta; la de quien la cambia sigue.
 * Sin @Transactional: las sesiones se cierran cuando el cambio de contraseña se guarda de verdad (al confirmar).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeSessionsTests {

    private static final String OLD = "Antigua2026!";
    private static final String NEW = "Nueva2026!x";
    private static final String MESSAGE = "Tu contraseña ha cambiado. Vuelve a iniciar sesión.";
    private static final Pattern LINK = Pattern.compile("/reset-password/([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordResetRepository resets;

    @MockitoBean
    private EmailGateway emailGateway;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : created) {
            resets.deleteAll(resets.findByUserId(id));
            users.deleteById(id);
        }
    }

    private User client() {
        String email = "sesiones." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        User user = userService.createUser("Clienta Sesiones", email, null, OLD, Role.CLIENT, null);
        created.add(user.getId());
        return user;
    }

    private User worker() {
        Center center = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter();
        String email = "equipo." + UUID.randomUUID().toString().substring(0, 8) + "@cbelleza.com";
        User user = userService.createUser("Trabajadora Sesiones", email, null, OLD, Role.WORKER, center);
        created.add(user.getId());
        return user;
    }

    /** Inicia sesión con el formulario en un «navegador» nuevo y devuelve su sesión. */
    private MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("identifier", email).param("password", password))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    @Test
    void changingThePasswordInTheProfileEndsTheOtherSessions() throws Exception {
        User user = client();
        MockHttpSession phone = login(user.getEmail(), OLD);
        MockHttpSession laptop = login(user.getEmail(), OLD);
        mvc.perform(get("/client/profile").session(phone)).andExpect(status().isOk());
        mvc.perform(get("/client/profile").session(laptop)).andExpect(status().isOk());

        mvc.perform(post("/client/profile").session(phone).with(csrf())
                        .param("name", "Clienta Sesiones")
                        .param("currentPassword", OLD).param("newPassword", NEW).param("confirmPassword", NEW))
                .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.containsString("actualizado")));

        // Quien la cambia sigue dentro; el otro navegador sale con el aviso
        mvc.perform(get("/client/profile").session(phone)).andExpect(status().isOk());
        mvc.perform(get("/client/appointments").session(laptop))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", MESSAGE));
        assertTrue(laptop.isInvalid(), "la sesión vieja se cierra");

        // Con la contraseña nueva vuelve a entrar sin problema
        MockHttpSession again = login(user.getEmail(), NEW);
        mvc.perform(get("/client/profile").session(again)).andExpect(status().isOk());
    }

    @Test
    void changingOnlyTheNameKeepsTheOtherSessions() throws Exception {
        User user = client();
        MockHttpSession phone = login(user.getEmail(), OLD);
        MockHttpSession laptop = login(user.getEmail(), OLD);
        mvc.perform(get("/client/profile").session(laptop)).andExpect(status().isOk());

        mvc.perform(post("/client/profile").session(phone).with(csrf()).param("name", "Otro Nombre"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/client/profile").session(laptop)).andExpect(status().isOk());
    }

    @Test
    void workerChangingThePasswordInMyAccountEndsTheOtherSessions() throws Exception {
        User user = worker();
        MockHttpSession reception = login(user.getEmail(), OLD);
        MockHttpSession home = login(user.getEmail(), OLD);
        mvc.perform(get("/worker/dashboard").session(home)).andExpect(status().isOk());

        mvc.perform(post("/worker/center/cuenta").session(reception).with(csrf())
                        .param("workerName", "Trabajadora Sesiones")
                        .param("currentPassword", OLD).param("newPassword", NEW).param("confirmPassword", NEW))
                .andExpect(flash().attribute("successMessage", "Datos de tu cuenta guardados."));

        mvc.perform(get("/worker/dashboard").session(reception)).andExpect(status().isOk());
        // La agenda pide datos con fetch: recibe el aviso en JSON
        mvc.perform(get("/worker/calendar/data").session(home).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value(MESSAGE));
    }

    @Test
    void resettingThePasswordFromTheEmailEndsEveryOpenSession() throws Exception {
        User user = client();
        MockHttpSession forgotten = login(user.getEmail(), OLD);
        mvc.perform(get("/client/profile").session(forgotten)).andExpect(status().isOk());

        mvc.perform(post("/forgot-password").with(csrf()).param("email", user.getEmail()));
        String token = tokenIn(sentMail());
        MockHttpSession resetBrowser = new MockHttpSession();
        mvc.perform(post("/reset-password/" + token).session(resetBrowser).with(csrf())
                        .param("password", NEW).param("confirmPassword", NEW))
                .andExpect(redirectedUrl(com.alexxlpz.crm_cbelleza.security.RoleHomes.CLIENT_HOME));

        // El navegador donde se restablece entra; el que se quedó abierto sale
        mvc.perform(get("/client/profile").session(resetBrowser)).andExpect(status().isOk());
        mvc.perform(get("/client/profile").session(forgotten))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("errorMessage", MESSAGE));
    }

    private EmailMessage sentMail() {
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, timeout(5000)).send(sent.capture());
        return sent.getValue();
    }

    private static String tokenIn(EmailMessage mail) {
        Matcher link = LINK.matcher(mail.htmlBody());
        assertTrue(link.find(), "el correo lleva el enlace");
        return link.group(1);
    }
}
