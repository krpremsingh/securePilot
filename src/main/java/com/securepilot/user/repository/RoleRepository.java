package com.securepilot.user.repository;

import com.securepilot.user.entity.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCodeAndActiveTrue(String code);
}
