package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import com.alexxlpz.crm_cbelleza.entities.Treatment;
import com.alexxlpz.crm_cbelleza.forms.ContactForm;
import com.alexxlpz.crm_cbelleza.mail.InquiryMailService;
import com.alexxlpz.crm_cbelleza.repositories.UserRepository;
import com.alexxlpz.crm_cbelleza.services.ClientCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CrmCBellezaApplicationTests {

    @Autowired
    private ClientCardService clientCardService;

    @Autowired
    private InquiryMailService inquiryMailService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void testAppointmentDynamicCompletedStatus() {
        Appointment pastConfirmed = Appointment.builder()
                .dateTime(LocalDateTime.now().minusDays(1))
                .status(AppointmentStatus.CONFIRMED)
                .build();
        assertEquals(AppointmentStatus.COMPLETED, pastConfirmed.getStatus());

        Appointment futureConfirmed = Appointment.builder()
                .dateTime(LocalDateTime.now().plusDays(1))
                .status(AppointmentStatus.CONFIRMED)
                .build();
        assertEquals(AppointmentStatus.CONFIRMED, futureConfirmed.getStatus());

        Appointment pastPending = Appointment.builder()
                .dateTime(LocalDateTime.now().minusDays(1))
                .status(AppointmentStatus.PENDING)
                .build();
        assertEquals(AppointmentStatus.PENDING, pastPending.getStatus());
    }

    @Test
    void testAppointmentDurationBasedCompletion() {
        Treatment oneHourTreatment = Treatment.builder()
                .duration(60)
                .name("Tratamiento 1h")
                .price(30.0)
                .build();

        Appointment inProgress = Appointment.builder()
                .dateTime(LocalDateTime.now().minusMinutes(30))
                .treatment(oneHourTreatment)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        assertEquals(AppointmentStatus.CONFIRMED, inProgress.getStatus(), "Una cita en curso sigue CONFIRMED");

        Appointment finished = Appointment.builder()
                .dateTime(LocalDateTime.now().minusMinutes(61))
                .treatment(oneHourTreatment)
                .status(AppointmentStatus.CONFIRMED)
                .build();
        assertEquals(AppointmentStatus.COMPLETED, finished.getStatus(), "Una cita terminada pasa a COMPLETED");
    }

    @Test
    void testClientCardLastCompletedAppointment() {
        Long centerId = userRepository.findByEmailIgnoreCase("carlos@cbelleza.com").orElseThrow().getCenter().getId();
        List<ClientSummaryDTO> clients = clientCardService.getClientsForCenter(centerId, null);
        assertFalse(clients.isEmpty());

        ClientSummaryDTO sofia = clients.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Sofía"))
                .findFirst()
                .orElseThrow();
        assertNotNull(sofia.getLastAppointmentDate(), "Debe mostrar la fecha de la última cita completada");
        assertTrue(sofia.getLastAppointmentDate().isBefore(LocalDateTime.now()));
    }

    @Test
    void testContactEmailFallsBackToConsoleWithoutSmtp() {
        assertDoesNotThrow(() -> inquiryMailService.sendContactInquiry(new ContactForm(
                "Cliente Prueba", "cliente@ejemplo.com", "600123456",
                "Consulta sobre horarios", "Hola, ¿abrís los sábados por la tarde?")));
    }
}
