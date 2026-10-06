package com.alexxlpz.crm_cbelleza.repositories;

import com.alexxlpz.crm_cbelleza.entities.Role;
import com.alexxlpz.crm_cbelleza.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"center"})
    List<User> findByRole(Role role);

    List<User> findByCenterIdAndRole(Long centerId, Role role);

    long countByCenterIdAndRole(Long centerId, Role role);

    @EntityGraph(attributePaths = {"center"})
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"center"})
    Optional<User> findByNameIgnoreCase(String name);

    Optional<User> findByPhone(String phone);

    @EntityGraph(attributePaths = {"center"})
    Optional<User> findWithCenterById(Long id);
}
