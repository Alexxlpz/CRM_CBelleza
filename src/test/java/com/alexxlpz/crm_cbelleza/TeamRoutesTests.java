package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.TeamRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Seguridad #16: en {@code /worker/**} todo es solo del dueño salvo {@link TeamRoutes#FOR_WHOLE_TEAM}. Recorre
 * todos los endpoints que existen (como haría quien va probando rutas) y comprueba que a una trabajadora estándar
 * (Lucía) se le frenan todos los que no están en la lista, y que la lista no apunta a rutas que ya no existen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamRoutesTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:]+)(?::([^}]+))?}");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    /** «MÉTODO /patrón» de cada endpoint de /worker/**. */
    private Set<String> workerEndpoints() {
        Set<String> endpoints = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/worker/")) {
                    continue;
                }
                assertFalse(methods.isEmpty(), pattern + " admite cualquier método HTTP: indica GET o POST");
                methods.forEach(m -> endpoints.add(m.name() + " " + pattern));
            }
        });
        return endpoints;
    }

    @Test
    void everyTeamRouteExists() {
        Set<String> existing = workerEndpoints();
        List<String> stale = new ArrayList<>();
        for (String route : TeamRoutes.FOR_WHOLE_TEAM) {
            if (!existing.contains(route)) {
                stale.add(route);
            }
        }
        assertTrue(stale.isEmpty(), "En TeamRoutes hay rutas que no existen (¿se renombraron?): " + stale);
    }

    @Test
    void everyOtherWorkerRouteIsOnlyForTheOwner() throws Exception {
        List<String> open = new ArrayList<>();
        int checked = 0;
        for (String endpoint : workerEndpoints()) {
            if (TeamRoutes.FOR_WHOLE_TEAM.contains(endpoint)) {
                continue;
            }
            String[] parts = endpoint.split(" ", 2);
            MvcResult result = mvc.perform(request(org.springframework.http.HttpMethod.valueOf(parts[0]), example(parts[1]))
                            .with(csrf()).with(TestUsers.as(users, LUCIA)))
                    .andReturn();
            MockHttpServletResponse response = result.getResponse();
            boolean stopped = response.getStatus() == 403
                    || (response.getStatus() == 302 && result.getFlashMap().containsKey("errorMessage"));
            if (!stopped) {
                open.add(endpoint + " → " + response.getStatus());
            }
            checked++;
        }
        assertTrue(checked > 20, "Se esperaban más endpoints solo del dueño (" + checked + ")");
        assertTrue(open.isEmpty(), "Una trabajadora estándar puede usar endpoints que no están en TeamRoutes: " + open);
    }

    /** Una URL que encaja con el patrón: {@code {id}} → 1 y {@code {section:a|b}} → a. */
    private static String example(String pattern) {
        Matcher m = PATH_VARIABLE.matcher(pattern);
        StringBuilder url = new StringBuilder();
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2).split("\\|")[0] : "1";
            m.appendReplacement(url, Matcher.quoteReplacement(value));
        }
        m.appendTail(url);
        return url.toString();
    }
}
