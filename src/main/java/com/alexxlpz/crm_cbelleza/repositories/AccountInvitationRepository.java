package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.AccountInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {

    @EntityGraph(attributePaths = {"user", "user.center"})
    Optional<AccountInvitation> findByTokenHash(String tokenHash);

    /** Última invitación de cada trabajador indicado (estado en la lista del equipo). */
    @Query("select i from AccountInvitation i where i.user.id in :userIds and i.createdAt = "
            + "(select max(j.createdAt) from AccountInvitation j where j.user.id = i.user.id)")
    List<AccountInvitation> findLatestByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /** Invitaciones sin usar de un trabajador (se anulan al reenviarla o al sacarle del equipo). */
    List<AccountInvitation> findByUserIdAndUsedAtIsNull(Long userId);
}
