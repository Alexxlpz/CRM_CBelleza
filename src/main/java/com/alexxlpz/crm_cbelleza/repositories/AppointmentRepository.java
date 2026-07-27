package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByCenterIdOrderByDateTimeDesc(Long centerId);
    List<Appointment> findByClientIdOrderByDateTimeDesc(Long clientId);
    List<Appointment> findByGuestPhoneOrderByDateTimeDesc(String phone);
}
