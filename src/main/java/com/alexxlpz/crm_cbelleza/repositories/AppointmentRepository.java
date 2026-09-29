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
    long countByCenterIdAndClientIdAndStatusInAndDateTimeBefore(Long centerId, Long clientId, List<AppointmentStatus> statuses, LocalDateTime dateTime);
    long countByCenterIdAndGuestPhoneAndStatusInAndDateTimeBefore(Long centerId, String guestPhone, List<AppointmentStatus> statuses, LocalDateTime dateTime);

    boolean existsByCenterIdAndClientIdAndStatus(Long centerId, Long clientId, AppointmentStatus status);
    boolean existsByCenterIdAndGuestPhoneAndStatus(Long centerId, String guestPhone, AppointmentStatus status);
    boolean existsByCenterIdAndClientIdAndStatusIn(Long centerId, Long clientId, List<AppointmentStatus> statuses);
    boolean existsByCenterIdAndGuestPhoneAndStatusIn(Long centerId, String guestPhone, List<AppointmentStatus> statuses);

    List<Appointment> findByCenterIdAndClientIdOrderByDateTimeDesc(Long centerId, Long clientId);
    List<Appointment> findByCenterIdAndGuestPhoneOrderByDateTimeDesc(Long centerId, String guestPhone);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Appointment a SET a.status = com.alexxlpz.crm_cbelleza.entities.AppointmentStatus.COMPLETED WHERE a.status = com.alexxlpz.crm_cbelleza.entities.AppointmentStatus.CONFIRMED AND a.dateTime < :now")
    int updatePastConfirmedToCompleted(@org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM Appointment a WHERE a.client IS NULL")
    void deleteGuestAppointments();
}
