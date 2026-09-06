package com.securepilot.user.repository;

import com.securepilot.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByCompanyIdAndId(UUID companyId, UUID userId);

    Optional<User> findByCompanyIdAndEmailIgnoreCase(UUID companyId, String email);
}
