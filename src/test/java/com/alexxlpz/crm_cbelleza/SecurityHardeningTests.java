package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.config.DatabaseSeeder;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.forms.PasswordPolicy;
import com.alexxlpz.crm_cbelleza.forms.ProfileForm;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import com.alexxlpz.crm_cbelleza.security.LoginAttemptService;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Seguridad previa a producción: intentos de login, contraseñas, cambio de contraseña y solicitudes de cita. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityHardeningTests {

    private static final String ELENA = TestUsers.WORKER_CENTER_2;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoginAttemptService loginAttempts;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private CenterRepository centers;
    @Autowired
    private TreatmentRepository treatments;
    @Autowired
    private CommandLineRunner databaseSeeder;

    @AfterEach
    void forgetFailedLogins() {
        loginAttempts.recordSuccess(ELENA);
        loginAttempts.recordSuccess("nadie@ejemplo.com");
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void login(String identifier, String password, String ip, String expectedUrl) throws Exception {
        mvc.perform(post("/login").with(csrf()).with(from(ip))
                        .param("identifier", identifier)
                        .param("password", password))
                .andExpect(redirectedUrl(expectedUrl));
    }

    @Test
    void fiveFailedLoginsLockTheAccountEvenWithTheRightPassword() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_ACCOUNT_FAILURES; i++) {
            login(ELENA, "no-es-esta-" + i, "10.0.0.1", "/login?error");
        }
        login(ELENA, DatabaseSeeder.DEMO_PASSWORD, "10.0.0.1", "/login?blocked");
        // Cambiar de IP no sirve: el cierre es de la cuenta
        login(ELENA, DatabaseSeeder.DEMO_PASSWORD, "10.0.0.2", "/login?blocked");
        mvc.perform(get("/login").param("blocked", "")).andExpect(content().string(containsString("Demasiados intentos fallidos")));
        // Las demás cuentas siguen entrando
        login(TestUsers.WORKER_CENTER_1, DatabaseSeeder.DEMO_PASSWORD, "10.0.0.3", "/worker/dashboard");
    }

    @Test
    void accountsThatDoNotExistAreLockedTooSoTheyCannotBeTold() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_ACCOUNT_FAILURES; i++) {
            login("nadie@ejemplo.com", "x" + i, "10.0.1.1", "/login?error");
        }
        login("nadie@ejemplo.com", "otra", "10.0.1.1", "/login?blocked");
    }

    @Test
    void anIpIsLockedAfterTwentyFailuresAndASuccessResetsTheAccount() {
        Instant start = Instant.parse("2026-10-27T10:00:00Z");
        LoginAttemptService atTen = new LoginAttemptService(Clock.fixed(start, ZoneId.of("UTC")));
        for (int i = 0; i < LoginAttemptService.MAX_IP_FAILURES; i++) {
            atTen.recordFailure("cuenta" + i + "@ejemplo.com", "10.0.2.1");
        }
        assertTrue(atTen.isBlocked("otra@ejemplo.com", "10.0.2.1"), "la IP queda cerrada para cualquier cuenta");
        assertFalse(atTen.isBlocked("otra@ejemplo.com", "10.0.2.2"));

        // Un éxito antes del quinto fallo vuelve a empezar la cuenta
        for (int i = 0; i < LoginAttemptService.MAX_ACCOUNT_FAILURES - 1; i++) {
            atTen.recordFailure("ana@ejemplo.com", "10.0.2.3");
        }
        atTen.recordSuccess("Ana@Ejemplo.com");
        atTen.recordFailure("ana@ejemplo.com", "10.0.2.3");
        assertFalse(atTen.isBlocked("ana@ejemplo.com", "10.0.2.4"));
    }

    @Test
    void theLockEndsFifteenMinutesAfterTheLastFailure() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        LoginAttemptService attempts = new LoginAttemptService(clock);
        for (int i = 0; i < LoginAttemptService.MAX_ACCOUNT_FAILURES; i++) {
            attempts.recordFailure("ana@ejemplo.com", "10.0.3.1");
        }
        assertTrue(attempts.isBlocked("ana@ejemplo.com", "10.0.3.2"));
        clock.advance(LoginAttemptService.LOCK.minusSeconds(1));
        assertTrue(attempts.isBlocked("ana@ejemplo.com", "10.0.3.2"));
        clock.advance(Duration.ofSeconds(1));
        assertFalse(attempts.isBlocked("ana@ejemplo.com", "10.0.3.2"));
    }

    @Test
    void shortOrCommonPasswordsAreRejected() {
        PasswordPolicy policy = new PasswordPolicy();
        assertThrows(BusinessRuleException.class, () -> policy.validateRequired("Ab1!xyz", "Ab1!xyz"));
        for (String common : new String[]{"12345678", "Password123", "contraseña", "qwerty123", "aaaaaaaaa"}) {
            BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> policy.validateRequired(common, common));
            assertTrue(e.getMessage().contains("más usadas"), common);
        }
        assertDoesNotThrow(() -> policy.validateRequired(DatabaseSeeder.DEMO_PASSWORD, DatabaseSeeder.DEMO_PASSWORD));
    }

    @Test
    void registeringWithACommonPasswordShowsTheReason() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("name", "Nueva")
                        .param("email", "nueva.comun@gmail.com")
                        .param("password", "12345678")
                        .param("confirmPassword", "12345678"))
                .andExpect(flash().attribute("errorMessage", containsString("de las más usadas")));
        assertTrue(users.findByEmailIgnoreCase("nueva.comun@gmail.com").isEmpty());
    }

    @Test
    void changingThePasswordAsksForTheCurrentOne() throws Exception {
        User sofia = users.findByNameIgnoreCase(TestUsers.CLIENT).orElseThrow();
        String before = sofia.getPassword();
        AppUserDetails session = AppUserDetails.from(sofia);

        mvc.perform(get("/client/profile").with(user(session)))
                .andExpect(content().string(containsString("name=\"currentPassword\"")));
        mvc.perform(post("/client/profile").with(csrf()).with(user(session))
                        .param("name", sofia.getName())
                        .param("currentPassword", "la-que-no-es")
                        .param("newPassword", "Nueva.Clave.1")
                        .param("confirmPassword", "Nueva.Clave.1"))
                .andExpect(flash().attribute("errorMessage", "La contraseña actual no es correcta."));
        assertEquals(before, users.findById(sofia.getId()).orElseThrow().getPassword(), "sin la actual no cambia");

        assertThrows(BusinessRuleException.class, () -> userService.updateProfile(sofia.getId(),
                new ProfileForm(null, null, null, "Nueva.Clave.1", "Nueva.Clave.1")));
        userService.updateProfile(sofia.getId(),
                new ProfileForm(null, null, DatabaseSeeder.DEMO_PASSWORD, "Nueva.Clave.1", "Nueva.Clave.1"));
        assertTrue(passwordEncoder.matches("Nueva.Clave.1", users.findById(sofia.getId()).orElseThrow().getPassword()));
    }

    @Test
    void myCenterAsksForTheCurrentPasswordToo() throws Exception {
        mvc.perform(get("/worker/center").with(TestUsers.as(users, TestUsers.WORKER_CENTER_1)))
                .andExpect(content().string(containsString("name=\"currentPassword\"")));
    }

    @Test
    void aClientCanHaveAtMostThreeUnansweredRequestsPerCenter() {
        Center center = centers.findById(users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow()
                .getCenter().getId()).orElseThrow();
        center.setAutoAcceptBookings(false);
        centers.saveAndFlush(center);
        Long treatmentId = treatments.findByCenterId(center.getId()).getFirst().getId();
        User client = userService.createUser("Pide mucho", "pide.mucho@gmail.com", null, null, Role.CLIENT, null);

        for (int i = 0; i < BookingService.MAX_PENDING_REQUESTS; i++) {
            Appointment app = bookingService.requestBooking(client.getId(), center.getId(), treatmentId, farMonday(30 + i));
            assertEquals(AppointmentStatus.PENDING, app.getStatus());
        }
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> bookingService.requestBooking(client.getId(), center.getId(), treatmentId, farMonday(40)));
        assertTrue(e.getMessage().contains("sin responder"));

        // En otro centro puede seguir pidiendo, y si el centro acepta solo, la cita no queda pendiente
        Long otherCenter = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_2).orElseThrow().getCenter().getId();
        bookingService.requestBooking(client.getId(), otherCenter,
                treatments.findByCenterId(otherCenter).getFirst().getId(), farMonday(41));
        center.setAutoAcceptBookings(true);
        centers.saveAndFlush(center);
        assertEquals(AppointmentStatus.CONFIRMED, bookingService.requestBooking(client.getId(), center.getId(),
                treatmentId, farMonday(42)).getStatus());
    }

    @Test
    void demoAccountsWithTheOldPasswordMoveToTheNewOne() throws Exception {
        User carlos = users.findByEmailIgnoreCase(TestUsers.WORKER_CENTER_1).orElseThrow();
        carlos.setPassword(passwordEncoder.encode("password123"));
        User manual = userService.createUser("Sin clave", "sin.clave@gmail.com", null, null, Role.CLIENT, null);
        users.saveAndFlush(carlos);

        databaseSeeder.run();

        assertTrue(passwordEncoder.matches(DatabaseSeeder.DEMO_PASSWORD, users.findById(carlos.getId()).orElseThrow().getPassword()));
        assertNull(users.findById(manual.getId()).orElseThrow().getPassword(), "las cuentas sin contraseña siguen sin ella");
    }

    /** Un lunes lejano a las 10, para no chocar con las citas de ejemplo. */
    private static LocalDateTime farMonday(int weeksAhead) {
        LocalDate monday = LocalDate.now().plusWeeks(weeksAhead).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(monday, LocalTime.of(10, 0));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
