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
}
