package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.SocialLinks;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Redes sociales del centro (19 nov 2026): las pone el dueño en Ajustes y salen en la ficha del centro. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterSocialLinksTests {

    private static final String LUCIA = "lucia.romero@cbelleza.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CenterRepository centers;

    private Center centroHistorico() {
        Long centerId = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow().getCenter().getId();
        return centers.findById(centerId).orElseThrow();
    }

    @Test
    void handlesAndLinksBecomeTheUserOfEachNetwork() {
        assertEquals("centro.historico", SocialLinks.instagram("@centro.historico"));
        assertEquals("centro_historico", SocialLinks.instagram("https://www.instagram.com/centro_historico/?hl=es"));
        assertEquals("centro_historico", SocialLinks.instagram("instagram.com/centro_historico"));
        assertEquals("centrohistorico", SocialLinks.tiktok("https://www.tiktok.com/@centrohistorico?lang=es"));
        assertEquals("centrohistorico", SocialLinks.tiktok(" @centrohistorico "));
        assertEquals("CentroHistoricoMadrid", SocialLinks.facebook("https://m.facebook.com/CentroHistoricoMadrid/"));
        assertEquals("profile.php?id=10009876", SocialLinks.facebook("https://www.facebook.com/profile.php?id=10009876"));
        assertNull(SocialLinks.instagram("   "));
        assertNull(SocialLinks.website(null));

        assertEquals("https://www.centrohistorico.es", SocialLinks.website("www.centrohistorico.es"));
        assertEquals("http://centrohistorico.es/reservas", SocialLinks.website("HTTP://CentroHistorico.es/reservas"));
    }

    @Test
    void linksToOtherSitesAreRejected() {
        assertThrows(BusinessRuleException.class, () -> SocialLinks.instagram("https://evil.example/instagram.com/x"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.instagram("https://www.tiktok.com/@centro"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.instagram("centro historico"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.instagram("instagram.com.evil.example/centro"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.tiktok("<script>"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.facebook("https://facebook.com.evil.example/x"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.website("javascript:alert(1)"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.website("https://user:pass@centro.es"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.website("ftp://centro.es"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.website("centro"));
        assertThrows(BusinessRuleException.class, () -> SocialLinks.website("https://centro.es/" + "a".repeat(300)));
    }

    @Test
    void ownerSavesTheNetworksAndClientsSeeThemOnThePublicPage() throws Exception {
        mvc.perform(get("/c/centro-historico"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("hero-social-link"))));

        mvc.perform(post("/worker/center/social").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("instagram", "@centro.historico").param("tiktok", "").param("facebook", "facebook.com/CentroHistorico")
                        .param("website", "www.centrohistorico.es"))
                .andExpect(redirectedUrl("/worker/center/centro#redes"))
                .andExpect(flash().attribute("successMessage", "Redes sociales guardadas."));

        Center center = centroHistorico();
        assertEquals("centro.historico", center.getInstagram());
        assertNull(center.getTiktok());
        assertEquals(3, center.getSocialLinks().size());

        mvc.perform(get("/c/centro-historico"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"https://www.instagram.com/centro.historico/\"")))
                .andExpect(content().string(containsString("href=\"https://www.facebook.com/CentroHistorico\"")))
                .andExpect(content().string(containsString("href=\"https://www.centrohistorico.es\"")))
                .andExpect(content().string(not(containsString("tiktok.com"))))
                .andExpect(content().string(containsString("\"sameAs\"")));
        mvc.perform(get("/worker/center/centro").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("value=\"@centro.historico\"")));
    }

    @Test
    void aWrongNetworkKeepsTheOldOnes() throws Exception {
        mvc.perform(post("/worker/center/social").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                .param("instagram", "@centro.historico"));
        mvc.perform(post("/worker/center/social").with(csrf()).with(TestUsers.as(users, TestUsers.WORKER_CENTER_1))
                        .param("instagram", "").param("website", "javascript:alert(1)"))
                .andExpect(flash().attribute("errorMessage", containsString("web")));
        assertEquals("centro.historico", centroHistorico().getInstagram());
        assertNull(centroHistorico().getWebsite());
    }

    @Test
    void onlyTheOwnerChangesTheNetworks() throws Exception {
        mvc.perform(post("/worker/center/social").with(csrf()).with(TestUsers.as(users, LUCIA))
                        .param("instagram", "@otra.cuenta"))
                .andExpect(flash().attribute("errorMessage", containsString("Solo el dueño del centro")));
        assertNull(centroHistorico().getInstagram());
        mvc.perform(get("/worker/center/centro").with(TestUsers.as(users, LUCIA)))
                .andExpect(content().string(not(containsString("/worker/center/social"))));
    }
}
