package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Usuarios del DatabaseSeeder para autenticar peticiones de MockMvc. */
final class TestUsers {

    static final String WORKER_CENTER_1 = "carlos@cbelleza.com";
    static final String WORKER_CENTER_2 = "elena@cbelleza.com";
    static final String CLIENT = "Sofía Martínez";
    /** crm.mail.admin-email de los tests (src/test/resources/config/application.properties). */
    static final String ADMIN_EMAIL = "admin@cuquora.test";

    private TestUsers() {
    }

    static AppUserDetails details(UserRepository repo, String identifier) {
        return AppUserDetails.from(repo.findByEmailIgnoreCase(identifier)
                .or(() -> repo.findByNameIgnoreCase(identifier))
                .orElseThrow());
    }

    static RequestPostProcessor as(UserRepository repo, String identifier) {
        return SecurityMockMvcRequestPostProcessors.user(details(repo, identifier));
    }

    /** Sesión de administración (AdminAccess): una cuenta con el correo de administración ya comprobado. */
    static RequestPostProcessor asAdmin(UserRepository repo) {
        User admin = repo.findByEmailIgnoreCase(ADMIN_EMAIL).orElseGet(() -> repo.save(User.builder()
                .name("Administración").email(ADMIN_EMAIL).password("{noop}no-se-usa").role(Role.CLIENT).build()));
        return SecurityMockMvcRequestPostProcessors.user(AppUserDetails.from(admin));
    }
}
