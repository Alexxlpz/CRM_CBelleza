package com.alexxlpz.crm_cbelleza.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El perfil prod no arranca con una configuración que dejaría la web rota o insegura. */
class ProductionChecksTests {

    /** Lo que pondría un .env de producción correcto. */
    private static MockEnvironment ready() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("crm.site-url", "https://cuquora.com")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/crm_cbelleza")
                .withProperty("crm.security.remember-me-key", "x".repeat(48))
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("spring.mail.host", "smtp-relay.brevo.com")
                .withProperty("crm.mail.from-email", "citas@cuquora.com")
                .withProperty("crm.legal.owner-name", "Alex")
                .withProperty("crm.legal.owner-nif", "00000000T")
                .withProperty("crm.legal.owner-address", "Calle 1, Madrid")
                .withProperty("crm.seed-demo-data", "false");
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void aCompleteConfigurationPassesWithoutWarnings() {
        ProductionChecks.Result result = ProductionChecks.check(ready());
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        new ProductionChecks(ready()).afterPropertiesSet();
    }

    @Test
    void theSiteUrlIsRequiredAndMustBeAPublicHttpsDomain() {
        assertThat(errorsWith("crm.site-url", "")).anyMatch(e -> e.contains("falta SITE_URL"));
        assertThat(errorsWith("crm.site-url", "http://cuquora.com")).anyMatch(e -> e.contains("https://"));
        assertThat(errorsWith("crm.site-url", "https://localhost:8080")).anyMatch(e -> e.contains("apunta a este equipo"));
        assertThat(errorsWith("crm.site-url", "https://cuquora.com/app")).anyMatch(e -> e.contains("sin ruta"));
        assertThat(errorsWith("crm.site-url", "https://cuquora.com/")).isEmpty();
    }

    @Test
    void demoAccountsInMemoryDatabaseAndWeakKeysStopTheStart() {
        assertThat(errorsWith("crm.seed-demo-data", "true")).anyMatch(e -> e.contains(DatabaseSeeder.DEMO_PASSWORD));
        assertThat(errorsWith("spring.datasource.url", "jdbc:h2:mem:crm_db")).anyMatch(e -> e.contains("H2"));
        assertThat(errorsWith("crm.security.remember-me-key", "corta")).anyMatch(e -> e.contains("REMEMBER_ME_KEY"));
        assertThat(errorsWith("server.servlet.session.cookie.secure", "false")).anyMatch(e -> e.contains("COOKIE_SECURE"));
        assertThat(errorsWith("spring.h2.console.enabled", "true")).anyMatch(e -> e.contains("consola H2"));
        assertThat(errorsWith("crm.security.google.client-id", "id-sin-secreto")).anyMatch(e -> e.contains("GOOGLE_CLIENT_SECRET"));

        MockEnvironment withDev = ready();
        withDev.setActiveProfiles("prod", "dev");
        assertThat(ProductionChecks.check(withDev).errors()).anyMatch(e -> e.contains("«dev»"));

        MockEnvironment broken = ready().withProperty("crm.site-url", "");
        assertThatThrownBy(() -> new ProductionChecks(broken).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SITE_URL");
    }

    @Test
    void missingMailLegalDataAndFreeSenderOnlyWarn() {
        MockEnvironment env = ready()
                .withProperty("spring.mail.host", "")
                .withProperty("crm.mail.from-email", "cuquora@hotmail.com")
                .withProperty("crm.legal.owner-nif", "")
                .withProperty("server.forward-headers-strategy", "none");
        ProductionChecks.Result result = ProductionChecks.check(env);
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(4)
                .anyMatch(w -> w.contains("MAIL_HOST"))
                .anyMatch(w -> w.contains("citas@cuquora.com"))
                .anyMatch(w -> w.contains("LSSI"))
                .anyMatch(w -> w.contains("FORWARD_HEADERS_STRATEGY"));
    }

    @Test
    void theProdProfileDefaultsToSecureCookiesAndHidesErrorDetails() throws Exception {
        Properties prod = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
        assertThat(prod.getProperty("server.servlet.session.cookie.secure")).isEqualTo("${COOKIE_SECURE:true}");
        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("${FORWARD_HEADERS_STRATEGY:native}");
        assertThat(prod.getProperty("spring.web.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("spring.web.error.include-message")).isEqualTo("never");
        assertThat(prod.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
    }

    private static java.util.List<String> errorsWith(String key, String value) {
        return ProductionChecks.check(ready().withProperty(key, value)).errors();
    }
}
