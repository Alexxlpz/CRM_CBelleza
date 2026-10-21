package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.entities.Center;
import com.alexxlpz.crm_cbelleza.entities.LoyaltyMode;
import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import com.alexxlpz.crm_cbelleza.exceptions.ResourceNotFoundException;
import com.alexxlpz.crm_cbelleza.forms.CenterRegistrationForm;
import com.alexxlpz.crm_cbelleza.repositories.AccountInvitationRepository;
import com.alexxlpz.crm_cbelleza.repositories.CenterRepository;
import com.alexxlpz.crm_cbelleza.repositories.TreatmentRepository;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.entities.TreatmentType;
import com.alexxlpz.crm_cbelleza.services.BookingService;
import com.alexxlpz.crm_cbelleza.services.OpeningHoursService;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService;
import com.alexxlpz.crm_cbelleza.services.CenterRegistrationService.Registration;
import com.alexxlpz.crm_cbelleza.services.SecureTokens;
import com.alexxlpz.crm_cbelleza.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alta de centros: centro oculto hasta publicarlo, equipo con invitaciones y tarjeta de puntos opcional. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CenterRegistrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CenterRegistrationService registrationService;

    @Autowired
    private CenterRepository centers;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccountInvitationRepository invitations;

    @Autowired
    private TreatmentRepository treatments;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OpeningHoursService openingHours;

    private static CenterRegistrationForm form(String ownerEmail, List<String> workerNames, List<String> workerEmails) {
        return new CenterRegistrationForm("Estudio Alba", "b-1234567.8", "+34 911 222 333", "Calle Mayor 12, 28013 Madrid",
                40.4155, -3.7074, "Europe/Madrid", 2, TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba Ruiz", ownerEmail,
                workerNames, workerEmails, "PER_ITEM", 10, 100, "Manicura gratis",
                List.of("Manicura y uñas"), "Instagram: @estudioalba", true);
    }

    @Test
    void registeringCreatesHiddenCenterWithTeamWithoutPasswordsAndLoyalty() throws Exception {
        mvc.perform(post("/register-center").with(csrf())
                        .param("centerName", "Estudio Alba")
                        .param("cif", "B12345678")
                        .param("phone", "911 222 333")
                        .param("cabins", "2")
                        // Martes con jornada partida y sábado por la mañana
                        .param("hoursDay", "2", "2", "6")
                        .param("hoursFrom", "09:30", "16:00", "10:00")
                        .param("hoursTo", "14:00", "20:30", "14:00")
                        .param("address", "Calle Mayor 12, 28013 Madrid")
                        .param("latitude", "40.4155")
                        .param("longitude", "-3.7074")
                        .param("timeZone", "Atlantic/Canary")
                        .param("contactName", "Alba Ruiz")
                        .param("email", "Alba@EstudioAlba.es")
                        .param("workerNames", "Nerea Gil", "")
                        .param("workerEmails", "nerea@estudioalba.es", "")
                        .param("mode", "PER_EURO")
                        .param("pointsPerUnit", "1")
                        .param("rewardPoints", "200")
                        .param("rewardText", "Tratamiento facial gratis")
                        .param("terms", "true"))
                .andExpect(redirectedUrl("/register-center"));

        User owner = users.findByEmailIgnoreCase("alba@estudioalba.es").orElseThrow();
        User nerea = users.findByEmailIgnoreCase("nerea@estudioalba.es").orElseThrow();
        Center center = centers.findById(owner.getCenter().getId()).orElseThrow();
        assertEquals("Estudio Alba", center.getName());
        assertEquals("B12345678", center.getTaxId());
        assertEquals("Atlantic/Canary", center.getTimeZone());
        assertEquals(2, center.getCabins());
        assertEquals(40.4155, center.getLatitude());
        assertEquals("alba@estudioalba.es", center.getEmail());
        assertFalse(center.isPublished());
        assertNotNull(center.getApprovalTokenHash());
        assertEquals(LoyaltyMode.PER_EURO, center.getLoyaltyMode());
        assertEquals(200, center.getLoyaltyRewardPoints());
        assertEquals(owner.getId(), center.getOwnerId(), "Quien da de alta el centro es su dueño");
        assertEquals(List.of("Martes: 09:30 - 14:00 y 16:00 - 20:30", "Sábado: 10:00 - 14:00"),
                openingHours.forCenter(center.getId()).describe());

        for (User worker : List.of(owner, nerea)) {
            assertEquals(Role.WORKER, worker.getRole());
            assertEquals(center.getId(), worker.getCenter().getId());
            assertNull(worker.getPassword());
            assertFalse(AppUserDetails.from(worker).isEnabled(), "Sin contraseña no puede entrar todavía");
        }
        assertEquals(2, invitations.count());
    }

    @Test
    void pendingCenterIsHiddenFromSearchDetailsAndBooking() throws Exception {
        Registration registration = registrationService.register(form("hola@estudioalba.es", List.of(), List.of()));
        Center center = centers.findById(registration.centerId()).orElseThrow();
        Treatment treatment = treatments.save(Treatment.builder().name("Manicura rusa").price(30.0).duration(60)
                .type(TreatmentType.MANICURA_PEDICURA).center(center).build());

        mvc.perform(get("/api/centers").param("size", "50"))
                .andExpect(content().string(not(containsString("Estudio Alba"))));
        mvc.perform(get("/client/centers/" + center.getId()).with(TestUsers.as(users, TestUsers.CLIENT)))
                .andExpect(status().isNotFound());
        Long clientId = TestUsers.details(users, TestUsers.CLIENT).getId();
        assertThrows(ResourceNotFoundException.class, () -> bookingService.requestBooking(
                clientId, center.getId(), treatment.getId(), LocalDateTime.now().plusDays(7).withHour(10).withMinute(0)));

        // Administración lo publica desde el enlace del correo
        String approve = "/register-center/approve/" + registration.approvalToken();
        mvc.perform(get(approve)).andExpect(status().isOk()).andExpect(content().string(containsString("B12345678")));
        mvc.perform(post(approve).with(csrf())).andExpect(status().isOk());
        assertTrue(centers.findById(center.getId()).orElseThrow().isPublished());
        mvc.perform(get("/api/centers").param("size", "50"))
                .andExpect(content().string(containsString("Estudio Alba")));
        // El enlace solo vale una vez
        mvc.perform(post(approve).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void invitationSetsPasswordOnceAndThenTheWorkerCanLogIn() throws Exception {
        Registration registration = registrationService.register(
                form("alba@estudioalba.es", List.of("Nerea Gil"), List.of("nerea@estudioalba.es")));
        String token = registration.invitees().get(1).invitationToken();

        mvc.perform(get("/invitation/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estudio Alba")));
        mvc.perform(post("/invitation/" + token).with(csrf())
                        .param("password", "secreta1").param("confirmPassword", "otra"))
                .andExpect(content().string(containsString("no coinciden")));
        mvc.perform(post("/invitation/" + token).with(csrf())
                        .param("password", "secreta1").param("confirmPassword", "secreta1"))
                .andExpect(redirectedUrl("/login"));

        User nerea = users.findByEmailIgnoreCase("nerea@estudioalba.es").orElseThrow();
        assertTrue(passwordEncoder.matches("secreta1", nerea.getPassword()));
        assertTrue(AppUserDetails.from(nerea).isEnabled());

        mvc.perform(post("/invitation/" + token).with(csrf())
                        .param("password", "otraclave").param("confirmPassword", "otraclave"))
                .andExpect(content().string(containsString("ya se ha usado")));
        assertTrue(passwordEncoder.matches("secreta1", users.findByEmailIgnoreCase("nerea@estudioalba.es").orElseThrow().getPassword()));
    }

    @Test
    void expiredInvitationCanBeRenewedForTheSameWorker() throws Exception {
        Registration registration = registrationService.register(form("alba@estudioalba.es", List.of(), List.of()));
        String token = registration.owner().invitationToken();
        var invitation = invitations.findByTokenHash(SecureTokens.hash(token)).orElseThrow();
        invitation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        invitations.save(invitation);

        mvc.perform(get("/invitation/" + token)).andExpect(content().string(containsString("ha caducado")));
        mvc.perform(post("/invitation/" + token).with(csrf())
                        .param("password", "secreta1").param("confirmPassword", "secreta1"))
                .andExpect(content().string(containsString("ha caducado")));
        long before = invitations.count();
        mvc.perform(post("/invitation/" + token + "/renew").with(csrf()))
                .andExpect(content().string(containsString("alba@estudioalba.es")));
        assertEquals(before + 1, invitations.count());
        assertNull(users.findByEmailIgnoreCase("alba@estudioalba.es").orElseThrow().getPassword());
    }

    @Test
    void unknownLinksAnswerNotFound() throws Exception {
        mvc.perform(get("/invitation/no-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/register-center/approve/no-existe")).andExpect(status().isNotFound());
    }

    @Test
    void emailOfAnExistingAccountIsRejectedAndNothingIsCreated() throws Exception {
        long centersBefore = centers.count();
        long usersBefore = users.count();
        mvc.perform(post("/register-center").with(csrf())
                        .param("centerName", "Estudio Alba").param("cif", "B12345678").param("phone", "911222333")
                        .param("cabins", "1").param("address", "Calle Mayor 12, Madrid")
                        .param("hoursDay", "1").param("hoursFrom", "09:00").param("hoursTo", "20:00")
                        .param("latitude", "40.4").param("longitude", "-3.7")
                        .param("contactName", "Alba Ruiz").param("email", "alba@estudioalba.es")
                        .param("workerNames", "Carlos").param("workerEmails", TestUsers.WORKER_CENTER_1)
                        .param("mode", "OFF").param("terms", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya hay una cuenta de Cuquora con el correo " + TestUsers.WORKER_CENTER_1)))
                // Lo escrito se conserva para corregirlo
                .andExpect(content().string(containsString("Calle Mayor 12, Madrid")));
        assertEquals(centersBefore, centers.count());
        assertEquals(usersBefore, users.count());
    }

    @Test
    void invalidRegistrationsExplainWhatToFix() {
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", null, null,
                null, 1, TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, true),
                "Marca en el mapa");
        assertRejected(form("alba@estudioalba.es", List.of("Nerea"), List.of("ALBA@estudioalba.es")), "está repetido");
        assertRejected(form("alba@estudioalba.es", List.of("Nerea"), List.of("")), "falta el nombre o el correo");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, null),
                "acepta los términos");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, TestHours.DAYS, TestHours.FROM, TestHours.TO, "Alba", "alba@estudioalba.es", List.of(), List.of(), "PER_ITEM", 1, 10, " ", null, null, true),
                "qué recompensa");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, List.of(), List.of(), List.of(), "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, true),
                "al menos un día");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, List.of(1), List.of("20:00"), List.of("09:00"), "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, true),
                "posterior a la de apertura");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, List.of(3, 3), List.of("09:00", "13:00"), List.of("14:00", "20:00"), "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, true),
                "se pisan");
        assertRejected(new CenterRegistrationForm("Estudio Alba", "B12345678", "911222333", "Calle Mayor 12", 40.4, -3.7,
                null, 1, List.of(1), List.of("09:15"), List.of("14:00"), "Alba", "alba@estudioalba.es", List.of(), List.of(), "OFF", null, null, null, null, null, true),
                "de 30 en 30 minutos");
        assertEquals(0, users.findByEmailIgnoreCase("alba@estudioalba.es").stream().count());
    }

    private void assertRejected(CenterRegistrationForm form, String message) {
        var e = assertThrows(BusinessRuleException.class, () -> registrationService.register(form));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

}
