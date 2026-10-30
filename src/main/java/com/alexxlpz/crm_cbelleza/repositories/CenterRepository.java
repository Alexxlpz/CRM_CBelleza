package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Center;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CenterRepository extends JpaRepository<Center, Long> {

    /**
     * Bloquea la fila del centro durante la transacción. Se usa al reservar y al confirmar citas
     * para que dos peticiones simultáneas no puedan ocupar la misma franja.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Center c WHERE c.id = :id")
    Optional<Center> findByIdForUpdate(@Param("id") Long id);

    Optional<Center> findByApprovalTokenHash(String approvalTokenHash);

    /** Centro de un enlace corto guardado ({@code /c/<slug>}, en minúsculas). */
    Optional<Center> findBySlug(String slug);

    /** Dueño del centro (nulo si no tiene) sin cargar el centro entero: se consulta en cada petición del panel. */
    @Query("SELECT c.ownerId FROM Center c WHERE c.id = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
}
