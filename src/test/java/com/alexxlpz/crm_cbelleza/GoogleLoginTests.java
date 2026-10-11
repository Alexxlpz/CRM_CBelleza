package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.GoogleOidcUser;
import com.alexxlpz.crm_cbelleza.security.RoleBasedAuthenticationSuccessHandler;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Iniciar sesión y crear cuenta con Google: enlace de cuentas, redirección a Google y sesión resultante. */
@SpringBootTest(properties = {
        "crm.security.google.client-id=test-client-id",
        "crm.security.google.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@Transactional
class GoogleLoginTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleBasedAuthenticationSuccessHandler successHandler;

    @Test
    void loginAndRegisterShowGoogleButton() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("Continuar con Google")))
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
        mvc.perform(get("/register"))
                .andExpect(content().string(containsString("Registrarse con Google")));
    }

    @Test
    void buttonRedirectsToGoogleConsentScreen() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
                        containsString("client_id=test-client-id"),
                        containsString("scope=openid%20profile%20email"),
                        containsString("redirect_uri=http://localhost/login/oauth2/code/google"))));
    }

    @Test
    void failedGoogleCallbackGoesBackToLoginWithMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/login/oauth2/code/google").param("error", "access_denied").param("state", "x").session(session))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString("No se ha podido iniciar sesión con Google")));
    }

    @Test
    void newGoogleUserGetsClientAccountWithoutPassword() {
        User created = userService.signInWithGoogle("google-nuevo", "Nueva.Clienta@Gmail.com", true, "Nueva Clienta");

        assertEquals(Role.CLIENT, created.getRole());
        assertEquals("nueva.clienta@gmail.com", created.getEmail());
        assertEquals("Nueva Clienta", created.getName());
        assertNull(created.getPassword());
        assertTrue(created.hasAccount(), "una cuenta de Google cuenta como registrada");

        User again = userService.signInWithGoogle("google-nuevo", "nueva.clienta@gmail.com", true, "Otro nombre");
        assertEquals(created.getId(), again.getId(), "la segunda vez entra en la misma cuenta");
    }

    @Test
    void existingEmailIsLinkedInsteadOfDuplicated() {
        User worker = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        long before = users.count();

        User linked = userService.signInWithGoogle("google-carlos", "CARLOS@cbelleza.com", true, "Carlos");

        assertEquals(worker.getId(), linked.getId());
        assertEquals(Role.WORKER, linked.getRole(), "conserva su rol y su centro");
        assertNotNull(linked.getCenter());
        assertEquals("google-carlos", linked.getGoogleId());
        assertNotNull(linked.getPassword(), "sigue pudiendo entrar con su contraseña");
        assertEquals(before, users.count());
    }

    @Test
    void unverifiedEmailIsRejected() {
        assertThrows(BusinessRuleException.class,
                () -> userService.signInWithGoogle("google-x", TestUsers.WORKER_CENTER_1, false, "Carlos"),
                "sin correo verificado no se vincula a una cuenta ajena");
    }

    @Test
    void emailLinkedToAnotherGoogleAccountIsRejected() {
        userService.signInWithGoogle("google-a", "compartido@gmail.com", true, "A");
        assertThrows(BusinessRuleException.class,
                () -> userService.signInWithGoogle("google-b", "compartido@gmail.com", true, "B"));
    }

    /** Tras volver de Google la sesión guarda el mismo AppUserDetails que con contraseña y se va al inicio del rol. */
    @Test
    void successfulGoogleLoginStoresAppUserInSession() throws Exception {
        User worker = userService.signInWithGoogle("google-carlos", TestUsers.WORKER_CENTER_1, true, "Carlos");
        AppUserDetails account = AppUserDetails.from(worker);
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "google-carlos", "email", TestUsers.WORKER_CENTER_1));
        GoogleOidcUser principal = new GoogleOidcUser(new DefaultOidcUser(List.of(), idToken), account);
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            successHandler.onAuthenticationSuccess(request, response, authentication);

            assertEquals("/worker/dashboard", response.getRedirectedUrl());
            SecurityContext saved = (SecurityContext) request.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            AppUserDetails sessionUser = assertInstanceOf(AppUserDetails.class, saved.getAuthentication().getPrincipal());
            assertEquals(worker.getId(), sessionUser.getId());
            assertEquals(worker.getCenter().getId(), sessionUser.getCenterId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void googleButtonRemembersCheckboxInSession() throws Exception {
        mvc.perform(get("/oauth2/authorization/google").param("rememberMe", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(request().sessionAttribute("GOOGLE_LOGIN_REMEMBER_ME", true));
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(request().sessionAttribute("GOOGLE_LOGIN_REMEMBER_ME", false));
    }

    /** Cuenta solo de Google (sin contraseña) con la casilla marcada: la cookie la vuelve a identificar sin pasar por Google. */
    @Test
    void googleLoginWithRememberMeSetsCookieThatLogsBackIn() throws Exception {
        User client = userService.signInWithGoogle("google-recordar", "recordar@gmail.com", true, "Recordar");
        MockHttpServletRequest request = googleCallback();
        request.getSession().setAttribute("GOOGLE_LOGIN_REMEMBER_ME", true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            successHandler.onAuthenticationSuccess(request, response, googleAuthentication(client));
        } finally {
            SecurityContextHolder.clearContext();
        }

        Cookie rememberMe = response.getCookie("remember-me");
        assertNotNull(rememberMe, "se crea la cookie de «Recordar mi sesión»");
        assertNull(request.getSession().getAttribute("GOOGLE_LOGIN_REMEMBER_ME"), "la marca se consume");
        mvc.perform(get("/client/appointments").cookie(rememberMe)).andExpect(status().isOk());
    }

    @Test
    void googleLoginWithoutCheckboxDoesNotRemember() throws Exception {
        User client = userService.signInWithGoogle("google-olvidar", "olvidar@gmail.com", true, "Olvidar");
        MockHttpServletRequest request = googleCallback();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            successHandler.onAuthenticationSuccess(request, response, googleAuthentication(client));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertNull(response.getCookie("remember-me"));
    }

    /** Las cuentas solo de Google están activas (para la cookie), pero el formulario de contraseña las rechaza. */
    @Test
    void googleOnlyAccountCannotUsePasswordForm() throws Exception {
        userService.signInWithGoogle("google-sin-clave", "sin.clave@gmail.com", true, "Sin Clave");
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", "sin.clave@gmail.com")
                        .param("password", ""))
                .andExpect(redirectedUrl("/login?error"));
    }

    private static MockHttpServletRequest googleCallback() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.getSession(true);
        return request;
    }

    private static OAuth2AuthenticationToken googleAuthentication(User user) {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", user.getGoogleId(), "email", user.getEmail()));
        GoogleOidcUser principal = new GoogleOidcUser(new DefaultOidcUser(List.of(), idToken), AppUserDetails.from(user));
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
