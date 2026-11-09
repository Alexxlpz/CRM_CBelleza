package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.PasswordResetRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.RoleHomes;
import com.alexxlpz.crm_cbelleza.services.PasswordResetService;
import com.alexxlpz.crm_cbelleza.services.SecureTokens;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «¿Olvidaste tu contraseña?»: enlace por correo de un solo uso que caduca a la hora. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetTests {

    private static final Pattern LINK = Pattern.compile("/reset-password/([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordResetRepository resets;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PersistentTokenRepository rememberMeTokens;

    @MockitoBean
    private EmailGateway emailGateway;

    @Test
    void loginPageLinksToThePublicForgotPasswordPage() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/forgot-password\"")))
                .andExpect(content().string(not(containsString("recoveryModal"))));
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enviarme el enlace")));
    }

    @Test
    void linkFromTheEmailSetsANewPasswordOnlyOnce() throws Exception {
        User user = userService.createUser("Lucía Olvido", "lucia.olvido@gmail.com", null, "Antigua2026!", Role.CLIENT, null);
        user.setEmailVerified(false);
        rememberMeTokens.createNewToken(new PersistentRememberMeToken("lucia.olvido@gmail.com", "serie-olvido", "t", new Date()));

        mvc.perform(post("/forgot-password").with(csrf()).param("email", " Lucia.Olvido@gmail.com "))
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attribute("sentTo", "Lucia.Olvido@gmail.com"));

        EmailMessage mail = sentMail();
        assertEquals("lucia.olvido@gmail.com", mail.to());
        assertEquals("Cambia tu contraseña de Cuquora", mail.subject());
        String token = tokenIn(mail);

        mvc.perform(get("/reset-password/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lucia.olvido@gmail.com")))
                .andExpect(content().string(containsString("Guardar la contraseña")));

        // La política de contraseñas también vale aquí
        mvc.perform(post("/reset-password/" + token).with(csrf())
                        .param("password", "123").param("confirmPassword", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-error")));

        // Con la contraseña nueva ya entra: va a su pantalla principal con la sesión iniciada
        mvc.perform(post("/reset-password/" + token).with(csrf())
                        .param("password", "Nueva2026!").param("confirmPassword", "Nueva2026!"))
                .andExpect(redirectedUrl(RoleHomes.CLIENT_HOME))
                .andExpect(flash().attribute("successMessage", containsString("Contraseña cambiada")))
                .andExpect(authenticated().withUsername("lucia.olvido@gmail.com"));

        User changed = users.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("Nueva2026!", changed.getPassword()));
        assertTrue(changed.isEmailVerified(), "el enlace llegó a su correo");
        assertNull(rememberMeTokens.getTokenForSeries("serie-olvido"), "se cierran sus sesiones recordadas");

        // El mismo enlace ya no sirve
        mvc.perform(post("/reset-password/" + token).with(csrf())
                        .param("password", "Otra2026!x").param("confirmPassword", "Otra2026!x"))
                .andExpect(content().string(containsString("ya se ha usado")));
        assertTrue(passwordEncoder.matches("Nueva2026!", users.findById(user.getId()).orElseThrow().getPassword()));
    }

    /** Un trabajador entra en su panel. */
    @Test
    void workerGoesToTheirDashboardAfterResetting() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", TestUsers.WORKER_CENTER_1));
        String token = tokenIn(sentMail());
        mvc.perform(post("/reset-password/" + token).with(csrf())
                        .param("password", "Nueva2026!").param("confirmPassword", "Nueva2026!"))
                .andExpect(redirectedUrl(RoleHomes.WORKER_HOME))
                .andExpect(authenticated().withUsername(TestUsers.WORKER_CENTER_1));
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoMail() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "nadie.aqui@gmail.com"))
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attribute("sentTo", "nadie.aqui@gmail.com"));
        verify(emailGateway, after(300).never()).send(any());
    }

    @Test
    void onlyTheLatestLinkWorksAndLinksExpireAfterAnHour() throws Exception {
        userService.createUser("Paula Dos Enlaces", "paula.enlaces@gmail.com", null, "Antigua2026!", Role.CLIENT, null);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "paula.enlaces@gmail.com"));
        String first = tokenIn(sentMail());
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "paula.enlaces@gmail.com"));
        ArgumentCaptor<EmailMessage> both = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, timeout(5000).times(2)).send(both.capture());
        String second = tokenIn(both.getAllValues().get(1));

        mvc.perform(get("/reset-password/" + first)).andExpect(content().string(containsString("ha caducado")));
        mvc.perform(get("/reset-password/" + second)).andExpect(content().string(containsString("Guardar la contraseña")));

        var reset = resets.findByTokenHash(SecureTokens.hash(second)).orElseThrow();
        reset.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        mvc.perform(post("/reset-password/" + second).with(csrf())
                        .param("password", "Nueva2026!").param("confirmPassword", "Nueva2026!"))
                .andExpect(content().string(containsString("ha caducado")));
    }

    @Test
    void anAccountCanAskForAtMostThreeLinksPerHour() throws Exception {
        userService.createUser("Marta Insistente", "marta.insiste@gmail.com", null, "Antigua2026!", Role.CLIENT, null);
        for (int i = 0; i < PasswordResetService.MAX_REQUESTS_PER_HOUR + 2; i++) {
            mvc.perform(post("/forgot-password").with(csrf()).param("email", "marta.insiste@gmail.com"))
                    .andExpect(redirectedUrl("/forgot-password"))
                    .andExpect(flash().attributeExists("sentTo"));
        }
        verify(emailGateway, after(500).times(PasswordResetService.MAX_REQUESTS_PER_HOUR)).send(any());
    }

    /** Un cliente que un centro dio de alta a mano no tiene contraseña: le llega el enlace para activar su cuenta. */
    @Test
    void clientWithoutAccountGetsTheActivationLinkInstead() throws Exception {
        userService.createUser("Rosa (jueves)", "rosa.sincuenta@gmail.com", "600123123", null, Role.CLIENT, null);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "rosa.sincuenta@gmail.com"));
        EmailMessage mail = sentMail();
        assertEquals("Activa tu cuenta de Cuquora", mail.subject());
        assertTrue(mail.htmlBody().contains("/invitation/"));
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
