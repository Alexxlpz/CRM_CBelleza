package com.alexxlpz.crm_cbelleza;

import com.alexxlpz.crm_cbelleza.dto.ClientSummaryDTO;
import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
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
    void testClientCardLastCompletedAppointment() {
        List<ClientSummaryDTO> clients = clientCardService.getClientsForCenter(1L, null);
        assertNotNull(clients);
        assertFalse(clients.isEmpty());

        // Client 1 (Sofía Martínez) has past completed appointment and future confirmed appointment
        ClientSummaryDTO client1 = clients.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Sofía"))
                .findFirst()
                .orElse(null);

        assertNotNull(client1);
        assertNotNull(client1.getLastAppointmentDate(), "Client 1 should have a last appointment date from completed appointment");
        assertTrue(client1.getLastAppointmentDate().isBefore(LocalDateTime.now()), "Last appointment date must be in the past (completed)");
    }
}
