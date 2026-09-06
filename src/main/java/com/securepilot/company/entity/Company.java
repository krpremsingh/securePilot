package com.securepilot.company.entity;

import com.securepilot.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 250)
    private String legalName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_status_id", nullable = false)
    private CompanyStatus status;

    @Column(name = "deleted_at", columnDefinition = "datetime(6)")
    private Instant deletedAt;

    protected Company() {
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getIndustry() {
        return industry;
    }

    public Integer getEmployeeCount() {
        return employeeCount;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
