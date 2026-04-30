package com.example.micro_user.Repository;

import com.example.micro_user.Entity.LoginHistory;
import com.example.micro_user.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    @Query("SELECT u FROM User u " +
            "WHERE (:role IS NULL OR u.role = :role) " +
            "AND (:active IS NULL OR u.active = :active)")
    Page<User> searchUsers(@Param("role") String role,
                           @Param("active") Boolean active,
                           Pageable pageable);

    long countByActive(boolean active);
    List<User> findByRoleAndRegion(String role, String region);
}
