package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autenticación, roles, CSRF y ausencia del selector de depuración fuera del perfil "dev". */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/features", "/centers", "/contact", "/register-center", "/login", "/register", "/api/centers"})
    void publicPagesAreAccessibleWithoutLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/worker/dashboard", "/client/appointments", "/client/profile"})
    void protectedPagesRedirectAnonymousUsersToLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void clientCannotOpenWorkerArea() throws Exception {
        mvc.perform(get("/worker/dashboard").with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(redirectedUrl("/centers"));
    }

    @Test
    void workerCannotOpenClientArea() throws Exception {
        mvc.perform(get("/client/appointments").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(redirectedUrl("/worker/dashboard"));
    }

    @Test
    void devProfileSelectorDoesNotExistInDefaultProfile() throws Exception {
        mvc.perform(get("/login-selector")).andExpect(status().isNotFound());
        Long anyUserId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getId();
        mvc.perform(post("/select-session").param("userId", anyUserId.toString()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/contact").param("name", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerLoginRedirectsToDashboard() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", TestUsers.WORKER_CENTER_1)
                        .param("password", "password123"))
                .andExpect(redirectedUrl("/worker/dashboard"));
    }

    @Test
    void clientCanLogInWithUserName() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", TestUsers.CLIENT)
                        .param("password", "password123"))
                .andExpect(redirectedUrl("/centers"));
    }

    @Test
    void wrongPasswordGoesBackToLoginWithError() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", TestUsers.WORKER_CENTER_1)
                        .param("password", "incorrecta"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutRequiresPost() throws Exception {
        mvc.perform(post("/logout").with(csrf()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(redirectedUrl("/login?logout"));
    }
}
