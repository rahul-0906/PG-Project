package com.pgcrm.repository;

import com.pgcrm.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /** Login — searches by email */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    java.util.List<User> findByRole(com.pgcrm.entity.enums.Role role);

    java.util.List<User> findByRoleAndBranchId(com.pgcrm.entity.enums.Role role, String branchId);
}
