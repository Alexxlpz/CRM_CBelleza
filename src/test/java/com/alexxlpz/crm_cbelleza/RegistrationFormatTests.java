package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.CenterForm;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.forms.PhoneNumbers;
import com.alexxlpz.crm_cbelleza.forms.TaxIds;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import com.alexxlpz.crm_cbelleza.services.CenterService;
import db.migration.V27__formato_telefonos_centros;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Formato de los datos del alta de centro: teléfono con prefijo aparte, CIF/NIF con su control, código postal. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationFormatTests {

    @Autowired
    private CenterRegistrationService registrationService;

    @Autowired
    private CenterService centerService;

    @Autowired
    private CenterRepository centers;

    @Autowired
    private UserRepository users;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    // ---- Teléfonos ----

    @Test
    void spanishPhonesAreSavedWithTheCountryCodeApartAndInGroupsOfThree() {
        assertEquals("+34 612 345 678", PhoneNumbers.normalize("34", "612345678"));
        assertEquals("+34 612 345 678", PhoneNumbers.normalize(null, " 612 34 56 78 "));
        assertEquals("+34 912 345 678", PhoneNumbers.normalize("34", "912-345-678"));
        // Con el prefijo escrito: con «+», con «00» o pegado sin «+»
        assertEquals("+34 612 345 678", PhoneNumbers.normalize("34", "+34612345678"));
        assertEquals("+34 612 345 678", PhoneNumbers.normalize("34", "0034 612 345 678"));
        assertEquals("+34 612 345 678", PhoneNumbers.normalize("34", "34 612 345 678"));
    }

    @Test
    void otherCountriesKeepTheirOwnCodeAndUsualGroups() {
        assertEquals("+351 912 345 678", PhoneNumbers.normalize("351", "912345678"));
        assertEquals("+33 6 12 34 56 78", PhoneNumbers.normalize("33", "06 12 34 56 78"));
        assertEquals("+44 7700 900123", PhoneNumbers.normalize("34", "+44 07700 900123"));
        assertEquals("+56 2 2033 3444", PhoneNumbers.normalize("56", "2 2033 3444"));
        assertEquals("+1 212 555 0123", PhoneNumbers.normalize("1", "(212) 555-0123"));
        assertEquals("+52 551 234 5678", PhoneNumbers.normalize("52", "55 1234 5678"));
        // Un prefijo que no está en la lista, escrito con «+»
        assertEquals("+420 601 123 456", PhoneNumbers.normalize("34", "+420 601123456"));
    }

    @Test
    void wrongPhonesExplainWhatToFix() {
        assertMessage(() -> PhoneNumbers.normalize("34", "61234567"), "9 cifras");
        assertMessage(() -> PhoneNumbers.normalize("34", "512345678"), "empieza por 6, 7, 8 o 9");
        assertMessage(() -> PhoneNumbers.normalize("34", "612 ABC 678"), "solo puede llevar números");
        assertMessage(() -> PhoneNumbers.normalize("34", "+999 1234567"), "prefijo del país");
        assertMessage(() -> PhoneNumbers.normalize("999", "1234567"), "prefijo del país en la lista");
        assertMessage(() -> PhoneNumbers.normalize("49", "123"), "entre 4 y 13 cifras");
    }

    @Test
    void savedPhonesAreSplitBackForTheForm() {
        assertEquals(new PhoneNumbers.Parts("34", "612 345 678"), PhoneNumbers.split("+34 612 345 678", "34"));
        assertEquals(new PhoneNumbers.Parts("56", "2 2033 3444"), PhoneNumbers.split("+56 2 2033 3444", "34"));
        assertEquals(new PhoneNumbers.Parts("34", "912345678"), PhoneNumbers.split("912345678", "34"));
        assertEquals(new PhoneNumbers.Parts("351", ""), PhoneNumbers.split(null, "351"));
        // Un prefijo fuera de la lista se añade para que salga elegido
        assertTrue(PhoneNumbers.countriesWith("420").stream().anyMatch(c -> c.code().equals("420")));
        assertEquals(PhoneNumbers.COUNTRIES, PhoneNumbers.countriesWith("34"));
    }

    @Test
    void callingCodesNeverStartLikeAnother() {
        for (String code : PhoneNumbers.CALLING_CODES) {
            for (String other : PhoneNumbers.CALLING_CODES) {
                assertTrue(code.equals(other) || !other.startsWith(code), code + " y " + other);
            }
        }
    }

    // ---- CIF / NIF ----

    @Test
    void spanishTaxIdsAreCompactedAndTheirControlChecked() {
        assertEquals("B12345674", TaxIds.normalize("b-1234567.4", true));
        assertEquals("12345678Z", TaxIds.normalize("12.345.678-z", true));
        assertEquals("X1234567L", TaxIds.normalize("X 1234567 L", true));
        assertEquals("Q2826000H", TaxIds.normalize("Q2826000H", true));
        assertMessage(() -> TaxIds.normalize("B12345678", true), "no cuadra");
        assertMessage(() -> TaxIds.normalize("12345678A", true), "no cuadra");
        assertMessage(() -> TaxIds.normalize("ABC123456XYZ", true), "un NIF son 8 números y una letra");
        assertMessage(() -> TaxIds.normalize("B1234_567", true), "solo letras y números");
    }

    @Test
    void foreignTaxIdsOnlyNeedLettersAndNumbers() {
        assertEquals("76123456K", TaxIds.normalize("76.123.456-K", false));
        assertEquals("ABC123456XYZ", TaxIds.normalize("abc123456xyz", false));
    }

    // ---- Alta completa ----

    @Test
    void registrationSavesNormalisedPhoneTaxIdNamesAndAddress() {
        var registration = registrationService.register(form("34", "611 22 33 44", "b12345674", "Calle  Mayor 12,   28013 Madrid",
                "Europe/Madrid", "  Estudio   Alba ", "alba@estudioalba.es"));
        Center center = centers.findById(registration.centerId()).orElseThrow();
        assertEquals("+34 611 223 344", center.getPhone());
        assertEquals("B12345674", center.getTaxId());
        assertEquals("Estudio Alba", center.getName());
        assertEquals("Calle Mayor 12, 28013 Madrid", center.getAddress());
        assertEquals("+34 611 223 344", users.findByEmailIgnoreCase("alba@estudioalba.es").orElseThrow().getPhone());
    }

    @Test
    void spanishAddressNeedsItsPostcodeButAForeignOneDoesNot() {
        assertMessage(() -> registrationService.register(form("34", "611223344", "B12345674", "Calle Mayor 12, Madrid",
                "Europe/Madrid", "Estudio Alba", "alba@estudioalba.es")), "Añade el código postal");
        assertMessage(() -> registrationService.register(form("34", "611223344", "B12345674", "Calle Mayor 12, 99013 Madrid",
                "Europe/Madrid", "Estudio Alba", "alba@estudioalba.es")), "empieza por 01 a 52");
        var chile = registrationService.register(form("56", "9 1234 5678", "76.123.456-K", "Av. Providencia 1234, Santiago",
                "America/Santiago", "Estudio Andes", "hola@estudioandes.cl"));
        Center center = centers.findById(chile.centerId()).orElseThrow();
        assertEquals("+56 9 1234 5678", center.getPhone());
        assertEquals("76123456K", center.getTaxId());
    }

    @Test
    void registrationRejectsWrongFormatsWithAClearMessage() {
        assertMessage(() -> registrationService.register(form("34", "61122334", "B12345674", "Calle Mayor 12, 28013 Madrid",
                "Europe/Madrid", "Estudio Alba", "alba@estudioalba.es")), "9 cifras");
        assertMessage(() -> registrationService.register(form("34", "611223344", "B12345678", "Calle Mayor 12, 28013 Madrid",
                "Europe/Madrid", "Estudio Alba", "alba@estudioalba.es")), "no cuadra");
        assertMessage(() -> registrationService.register(form("34", "611223344", "B12345674", "Calle Mayor 12, 28013 Madrid",
                "Europe/Madrid", "Estudio Alba", "alba@estudioalba.e")), "no es válido");
        assertMessage(() -> registrationService.register(form("34", "611223344", "B12345674", "Calle Mayor 12, 28013 Madrid",
                "Europe/Madrid", "A", "alba@estudioalba.es")), "al menos 2 caracteres");
    }

    @Test
    void registrationPageShowsTheCountryCodeApart() throws Exception {
        mvc.perform(get("/register-center"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"phoneCountry\"")))
                .andExpect(content().string(containsString("selected=\"selected\">+34 (España)</option>")))
                .andExpect(content().string(containsString("data-calling-codes=\"1 7 20 ")));
    }

    // ---- Mi Centro y teléfonos ya guardados ----

    @Test
    void myCenterSavesThePhoneWithTheSameFormat() {
        Center center = centers.findAll().getFirst();
        centerService.updateCenter(center.getId(), new CenterForm(null, null, "+34 600111222", null, null, null, null, null));
        assertEquals("+34 600 111 222", centers.findById(center.getId()).orElseThrow().getPhone());
        assertMessage(() -> centerService.updateCenter(center.getId(),
                new CenterForm(null, null, "+34 6001", null, null, null, null, null)), "9 cifras");
        centerService.updateCenter(center.getId(), new CenterForm(null, null, "", null, null, null, null, null));
        assertNull(centers.findById(center.getId()).orElseThrow().getPhone());
    }

    @Test
    void migrationFormatsSavedPhonesAndLeavesTheOnesItDoesNotUnderstand() throws Exception {
        List<Center> all = centers.findAll();
        Center spanish = all.get(0);
        Center other = all.get(1);
        jdbc.update("UPDATE centers SET phone = '912345678', time_zone = 'Europe/Madrid' WHERE id = ?", spanish.getId());
        jdbc.update("UPDATE centers SET phone = 'llamar al 6', time_zone = 'Europe/Madrid' WHERE id = ?", other.getId());

        Connection connection = DataSourceUtils.getConnection(dataSource);
        new V27__formato_telefonos_centros().migrate(new Context() {
            @Override
            public Configuration getConfiguration() {
                return null;
            }

            @Override
            public Connection getConnection() {
                return connection;
            }
        });

        assertEquals("+34 912 345 678", jdbc.queryForObject("SELECT phone FROM centers WHERE id = ?", String.class, spanish.getId()));
        assertEquals("llamar al 6", jdbc.queryForObject("SELECT phone FROM centers WHERE id = ?", String.class, other.getId()));
    }

    // ---- Registro de clientes (/register) ----

    @Test
    void clientRegistrationSavesThePhoneWithItsCountryCode() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "  Laura   Gómez ").param("email", " Laura@Gmail.com ")
                        .param("phoneCountry", "34").param("phone", "699 99 99 99")
                        .param("password", "secreto12").param("confirmPassword", "secreto12"))
                .andExpect(redirectedUrl("/centers"));
        var laura = users.findByEmailIgnoreCase("laura@gmail.com").orElseThrow();
        assertEquals("+34 699 999 999", laura.getPhone());
        assertEquals("Laura Gómez", laura.getName());
        assertEquals("laura@gmail.com", laura.getEmail());

        mvc.perform(post("/register").with(csrf())
                        .param("name", "Ana Sin Teléfono").param("email", "ana@gmail.com")
                        .param("phoneCountry", "34").param("phone", "")
                        .param("password", "secreto12").param("confirmPassword", "secreto12"))
                .andExpect(redirectedUrl("/centers"));
        assertNull(users.findByEmailIgnoreCase("ana@gmail.com").orElseThrow().getPhone(), "el teléfono sigue siendo opcional");
    }

    @Test
    void clientRegistrationRejectsWrongPhoneAndEmailAndKeepsWhatWasTyped() throws Exception {
        long before = users.count();
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Laura Gómez").param("email", "laura@gmail.com")
                        .param("phoneCountry", "351").param("phone", "69999")
                        .param("password", "secreto12").param("confirmPassword", "secreto12"))
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", containsString("Portugal tiene 9 cifras")))
                .andExpect(flash().attribute("phoneCountry", "351"));
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Laura Gómez").param("email", "laura@gmail.c")
                        .param("password", "secreto12").param("confirmPassword", "secreto12"))
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", containsString("Revisa el correo")));
        assertEquals(before, users.count());
    }

    @Test
    void clientRegistrationPageShowsTheCountryCodeApart() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"phoneCountry\"")))
                .andExpect(content().string(containsString("selected=\"selected\">+34 (España)</option>")));
    }

    @Test
    void theCountryCodeIsChosenFromWhereThePageIsOpened() throws Exception {
        // Detrás de Cloudflare: el país de la conexión
        mvc.perform(get("/register-center").header("CF-IPCountry", "MX"))
                .andExpect(content().string(containsString("selected=\"selected\">+52 (México)</option>")));
        mvc.perform(get("/register").header("CF-IPCountry", "cl"))
                .andExpect(content().string(containsString("selected=\"selected\">+56 (Chile)</option>")));
        // Sin él, el idioma del navegador; un país desconocido o sin pistas, España
        mvc.perform(get("/register").header("Accept-Language", "es-PE,es;q=0.9"))
                .andExpect(content().string(containsString("selected=\"selected\">+51 (Perú)</option>")));
        assertEquals("34", PhoneNumbers.countryForRequest("XX", List.of(java.util.Locale.forLanguageTag("es"))));
        assertEquals("34", PhoneNumbers.countryForRequest(null, null));
        assertEquals("1", PhoneNumbers.countryForRequest(null, List.of(java.util.Locale.forLanguageTag("en-US"))));
    }

    private static CenterRegistrationForm form(String country, String phone, String cif, String address, String timeZone,
                                               String name, String email) {
        return new CenterRegistrationForm(name, cif, country, phone, address, 40.4155, -3.7074, timeZone, 1,
                TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba Ruiz", email, List.of(), List.of(),
                "OFF", 1, 100, null, List.of(), null, true);
    }

    private static void assertMessage(org.junit.jupiter.api.function.Executable action, String expected) {
        BusinessRuleException e = assertThrows(BusinessRuleException.class, action);
        assertTrue(e.getMessage().contains(expected), () -> "«" + e.getMessage() + "» no dice «" + expected + "»");
    }
}
