package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.forms.RegistrationForm;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Registro público de clientes (incluido completar una cuenta que un centro creó a mano sin contraseña) y cambio de contraseña. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PersistentTokenRepository rememberMeTokens;

    @Test
    void registeringWithEmailOfManualClientAddsPasswordToThatAccount() throws Exception {
        User manual = userService.createUser("María (martes)", "Maria@Gmail.com", "600111222", null, Role.CLIENT, null);
        long before = users.count();

        mvc.perform(post("/register").with(csrf())
                        .param("name", "María López")
                        .param("email", "maria@gmail.com")
                        .param("phone", "699999999")
                        .param("password", "secreto1")
                        .param("confirmPassword", "secreto1"))
                .andExpect(redirectedUrl("/centers"));

        User claimed = users.findById(manual.getId()).orElseThrow();
        assertEquals(before, users.count(), "no se duplica la cuenta");
        assertTrue(passwordEncoder.matches("secreto1", claimed.getPassword()));
        assertEquals("María López", claimed.getName());
        assertEquals("maria@gmail.com", claimed.getEmail());
        assertEquals("699999999", claimed.getPhone(), "prevalece el teléfono que pone el cliente");
        assertTrue(claimed.hasAccount());
    }

    @Test
    void centerPhoneIsKeptWhenClientLeavesItBlank() {
        User manual = userService.createUser("Pepa", "pepa@gmail.com", "600555666", null, Role.CLIENT, null);
        userService.registerClient(new RegistrationForm("Pepa Ruiz", "pepa@gmail.com", " ", "secreto1", "secreto1"));
        assertEquals("600555666", users.findById(manual.getId()).orElseThrow().getPhone());
    }

    @Test
    void emailWithPasswordIsRejectedAsExistingAccount() {
        var e = assertThrows(BusinessRuleException.class, () -> userService.registerClient(form("sofia@gmail.com")));
        assertTrue(e.getMessage().startsWith("Ya existe una cuenta"));
    }

    /** Una cuenta de Google no tiene contraseña, pero ya tiene dueño: no se puede registrar encima. */
    @Test
    void googleAccountCannotBeClaimedByRegistering() {
        User google = userService.signInWithGoogle("google-solo", "solo.google@gmail.com", true, "Solo Google");

        var e = assertThrows(BusinessRuleException.class,
                () -> userService.registerClient(form("solo.google@gmail.com")));
        assertTrue(e.getMessage().contains("Continuar con Google"), "el aviso explica cómo entrar");

        User unchanged = users.findById(google.getId()).orElseThrow();
        assertNull(unchanged.getPassword());
        assertEquals("Solo Google", unchanged.getName());
    }

    /** Desde dentro de la aplicación (perfil) sí puede añadir una contraseña y entrar luego con el formulario. */
    @Test
    void googleAccountAddsPasswordFromProfile() throws Exception {
        User google = userService.signInWithGoogle("google-perfil", "perfil.google@gmail.com", true, "Perfil");
        AppUserDetails session = AppUserDetails.from(google);

        mvc.perform(get("/client/profile").with(user(session)))
                .andExpect(content().string(containsString("Añadir Contraseña")));
        mvc.perform(post("/client/profile").with(csrf()).with(user(session))
                        .param("newPassword", "secreto1")
                        .param("confirmPassword", "secreto1"))
                .andExpect(redirectedUrl("/client/profile"));

        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "perfil.google@gmail.com")
                        .param("password", "secreto1"))
                .andExpect(redirectedUrl("/centers"));
        mvc.perform(get("/client/profile").with(user(AppUserDetails.from(users.findById(google.getId()).orElseThrow()))))
                .andExpect(content().string(containsString("Cambiar Contraseña")));
    }

    /** Los correos internos se deducen del teléfono: reclamarlos daría acceso a la cuenta de otra persona. */
    @Test
    void placeholderEmailsCannotBeRegistered() {
        userService.createUser("Sin correo", "cliente_600333444" + User.PLACEHOLDER_EMAIL_DOMAIN, "600333444",
                null, Role.CLIENT, null);
        assertThrows(BusinessRuleException.class,
                () -> userService.registerClient(form("CLIENTE_600333444" + User.PLACEHOLDER_EMAIL_DOMAIN)));
    }

    @Test
    void changingPasswordForgetsRememberedDevices() {
        User worker = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        rememberMeTokens.createNewToken(new PersistentRememberMeToken(
                TestUsers.WORKER_CENTER_1, "serie-1", "token-1", new Date()));

        userService.updateProfile(worker.getId(), new ProfileForm(null, null, "nueva-clave", "nueva-clave"));

        assertNull(rememberMeTokens.getTokenForSeries("serie-1"));
    }

    private static RegistrationForm form(String email) {
        return new RegistrationForm("Alguien", email, null, "secreto1", "secreto1");
    }
}
