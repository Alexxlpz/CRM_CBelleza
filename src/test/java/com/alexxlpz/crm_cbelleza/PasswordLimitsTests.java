package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Seguridad #15: contraseñas de 8 a 32 caracteres, solo letras, números y símbolos del teclado. Con más de 72 bytes
 * BCrypt se negaba a cifrarlas y salía una página 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordLimitsTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    @Test
    void acceptsUpToThirtyTwoCommonCharacters() {
        String longest = "Peluquería-Señora_2026!áéíóúüÑ#x"; // 32 caracteres, con tildes y eñes
        assertTrue(longest.length() == PasswordPolicy.MAX_LENGTH);
        assertDoesNotThrow(() -> policy.validateRequired(longest, longest));
    }

    @Test
    void rejectsLongerPasswordsAndUncommonCharacters() {
        String tooLong = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q"; // 33
        assertThrows(BusinessRuleException.class, () -> policy.validateRequired(tooLong, tooLong));
        for (String odd : new String[]{"Clave segura 1", "Clave🙂segura1", "Clave€segura1", "Clave\tsegura1"}) {
            BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> policy.validateRequired(odd, odd), odd);
            assertTrue(e.getMessage().contains("símbolos del teclado"), odd);
        }
        assertThrows(BusinessRuleException.class, () -> policy.validateOptional(tooLong, tooLong), "también al cambiarla");
    }

    @Test
    void registeringWithAnEightyCharacterPasswordShowsAMessage() throws Exception {
        String eighty = "x1".repeat(40);
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Larga").param("email", "larga@gmail.com")
                        .param("password", eighty).param("confirmPassword", eighty))
                .andExpect(flash().attribute("errorMessage", containsString("como mucho 32")));
        assertTrue(users.findByEmailIgnoreCase("larga@gmail.com").isEmpty());
    }

    @Test
    void loggingInWithAnEightyCharacterPasswordIsJustAWrongPassword() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", TestUsers.WORKER_CENTER_1)
                        .param("password", "x1".repeat(40)))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf())
                        .param("identifier", TestUsers.WORKER_CENTER_1)
                        .param("password", "🙂".repeat(20)))
                .andExpect(redirectedUrl("/login?error"));
    }
}
