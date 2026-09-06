package com.securepilot.user.repository;

import com.securepilot.user.entity.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusRepository extends JpaRepository<UserStatus, UUID> {

    Optional<UserStatus> findByCodeAndActiveTrue(String code);
}
