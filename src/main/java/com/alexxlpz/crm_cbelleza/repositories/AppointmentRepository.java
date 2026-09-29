package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import com.alexxlpz.crm_cbelleza.entities.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByCenterIdOrderByDateTimeDesc(Long centerId);
    List<Appointment> findByClientIdOrderByDateTimeDesc(Long clientId);
    List<Appointment> findByGuestPhoneOrderByDateTimeDesc(String phone);
    List<Appointment> findByCenterIdAndStatusInAndDateTimeAfterOrderByDateTimeAsc(
            Long centerId, List<AppointmentStatus> statuses, LocalDateTime dateTime);
    List<Appointment> findByCenterIdAndStatusIn(Long centerId, List<AppointmentStatus> statuses);

    long countByCenterIdAndClientIdAndDateTimeBefore(Long centerId, Long clientId, LocalDateTime dateTime);
    long countByCenterIdAndGuestPhoneAndDateTimeBefore(Long centerId, String guestPhone, LocalDateTime dateTime);
    long countByCenterIdAndClientIdAndStatusAndDateTimeBefore(Long centerId, Long clientId, AppointmentStatus status, LocalDateTime dateTime);
    long countByCenterIdAndGuestPhoneAndStatusAndDateTimeBefore(Long centerId, String guestPhone, AppointmentStatus status, LocalDateTime dateTime);

    boolean existsByCenterIdAndClientIdAndStatus(Long centerId, Long clientId, AppointmentStatus status);
    boolean existsByCenterIdAndGuestPhoneAndStatus(Long centerId, String guestPhone, AppointmentStatus status);

    List<Appointment> findByCenterIdAndClientIdOrderByDateTimeDesc(Long centerId, Long clientId);
    List<Appointment> findByCenterIdAndGuestPhoneOrderByDateTimeDesc(Long centerId, String guestPhone);
}
