package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.NotificationDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NotificationDismissalRepository extends JpaRepository<NotificationDismissal, Long> {

    @Query("SELECT d.notificationKey FROM NotificationDismissal d WHERE d.userId = :userId")
    List<String> findKeysByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM NotificationDismissal d WHERE d.userId = :userId AND d.notificationKey IN :keys")
    void deleteByUserIdAndKeys(@Param("userId") Long userId, @Param("keys") Collection<String> keys);
}
