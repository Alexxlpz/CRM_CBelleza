package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.PasswordReset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<PasswordReset> findByTokenHash(String tokenHash);

    /** Enlaces sin usar de una cuenta: al pedir otro o al usar uno, los demás dejan de valer. */
    List<PasswordReset> findByUserIdAndUsedAtIsNull(Long userId);

    /** Cuántos enlaces se han pedido para una cuenta desde ese momento (tope de correos por cuenta). */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Todos los enlaces de una cuenta, usados o no (se borran con la cuenta). */
    List<PasswordReset> findByUserId(Long userId);
}
