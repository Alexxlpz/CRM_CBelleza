package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.AccountInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {

    @EntityGraph(attributePaths = {"user", "user.center"})
    Optional<AccountInvitation> findByTokenHash(String tokenHash);
}
