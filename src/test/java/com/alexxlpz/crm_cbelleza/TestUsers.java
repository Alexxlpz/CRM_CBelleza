package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Usuarios del DatabaseSeeder para autenticar peticiones de MockMvc. */
final class TestUsers {

    static final String WORKER_CENTER_1 = "carlos@cbelleza.com";
    static final String WORKER_CENTER_2 = "elena@cbelleza.com";
    static final String CLIENT = "Sofía Martínez";

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
}
