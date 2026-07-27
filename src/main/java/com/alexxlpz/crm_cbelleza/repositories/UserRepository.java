package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByRole(Role role);
    List<User> findByCenterIdAndRole(Long centerId, Role role);
}
