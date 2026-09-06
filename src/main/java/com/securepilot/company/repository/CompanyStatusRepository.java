package com.securepilot.company.repository;

import com.securepilot.company.entity.CompanyStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyStatusRepository extends JpaRepository<CompanyStatus, UUID> {

    Optional<CompanyStatus> findByCodeAndActiveTrue(String code);
}
