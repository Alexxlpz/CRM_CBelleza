package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.GoogleCalendarLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleCalendarLinkRepository extends JpaRepository<GoogleCalendarLink, Long> {

    Optional<GoogleCalendarLink> findByWorkerId(Long workerId);
}
