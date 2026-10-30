package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.config.DatabaseSeeder;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.forms.RegistrationForm;
import com.alexxlpz.crm_cbelleza.mail.EmailGateway;
import com.alexxlpz.crm_cbelleza.mail.EmailMessage;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
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

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Registro público de clientes (incluido activar una cuenta que un centro creó a mano sin contraseña) y cambio de contraseña. */
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

    @MockitoBean
    private EmailGateway emailGateway;

    /**
     * Si un centro ya dio de alta a mano a un cliente con ese correo, registrarse no pone la contraseña al momento:
     * llega un enlace a ese correo. Si no, cualquiera que supiera el correo se quedaría con las citas de esa persona.
     */
    @Test
    void registeringWithEmailOfManualClientSendsActivationLinkInstead() throws Exception {
        User manual = userService.createUser("María (martes)", "Maria@Gmail.com", "600111222", null, Role.CLIENT, null);
        long before = users.count();

        mvc.perform(post("/register").with(csrf())
                        .param("name", "María López")
                        .param("email", "maria@gmail.com")
                        .param("phone", "699999999")
                        .param("password", "secreto1")
                        .param("confirmPassword", "secreto1"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", containsString("para activar tu cuenta")));

        User untouched = users.findById(manual.getId()).orElseThrow();
        assertEquals(before, users.count(), "no se duplica la cuenta");
        assertNull(untouched.getPassword(), "la contraseña escrita en el registro no vale");
        assertEquals("María (martes)", untouched.getName(), "los datos del centro no cambian hasta que entre");
        assertEquals("600111222", untouched.getPhone());

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway, timeout(5000)).send(sent.capture());
        assertEquals("Maria@Gmail.com", sent.getValue().to());
        assertEquals("Activa tu cuenta de Cuquora", sent.getValue().subject());
        Matcher link = Pattern.compile("/invitation/([A-Za-z0-9_-]+)").matcher(sent.getValue().htmlBody());
        assertTrue(link.find(), "el correo lleva el enlace");

        // Con el enlace del correo sí: pone la contraseña y el correo queda verificado
        mvc.perform(post("/invitation/" + link.group(1)).with(csrf())
                        .param("password", "secreto1")
                        .param("confirmPassword", "secreto1"))
                .andExpect(redirectedUrl("/login"));
        User claimed = users.findById(manual.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("secreto1", claimed.getPassword()));
        assertTrue(claimed.isEmailVerified());
    }

    @Test
    void newSelfRegisteredAccountsStartWithEmailUnverified() {
        User created = userService.registerClient(form("nueva.cuenta@gmail.com")).user();
        assertFalse(created.isEmailVerified());
        assertNotNull(created.getPassword());
    }

    /**
     * Alguien se registra con el correo de otra persona antes que ella; cuando la dueña entra con Google, la contraseña
     * que puso el otro deja de valer.
     */
    @Test
    void googleLoginOfTheRealOwnerRemovesPasswordSetWithoutVerifying() {
        User squatted = userService.registerClient(form("victima@gmail.com")).user();
        rememberMeTokens.createNewToken(new PersistentRememberMeToken(
                "victima@gmail.com", "serie-okupa", "token-okupa", new Date()));

        User owner = userService.signInWithGoogle("google-victima", "victima@gmail.com", true, "Víctima");

        assertEquals(squatted.getId(), owner.getId());
        assertNull(owner.getPassword());
        assertTrue(owner.isEmailVerified());
        assertNull(rememberMeTokens.getTokenForSeries("serie-okupa"), "se cierran sus sesiones recordadas");
    }

    /** Una cuenta con contraseña y correo verificado (las de siempre) conserva la contraseña al vincular Google. */
    @Test
    void googleLinkingKeepsPasswordOfVerifiedAccounts() {
        User sofia = userService.signInWithGoogle("google-sofia", "sofia@gmail.com", true, "Sofía");
        assertNotNull(sofia.getPassword());
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

        userService.updateProfile(worker.getId(), new ProfileForm(null, null, DatabaseSeeder.DEMO_PASSWORD, "nueva-clave", "nueva-clave"));

        assertNull(rememberMeTokens.getTokenForSeries("serie-1"));
    }

    private static RegistrationForm form(String email) {
        return new RegistrationForm("Alguien", email, null, "secreto1", "secreto1");
    }
}
