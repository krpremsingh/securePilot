# SecurePilot - Codex Project Instructions

## Project Overview

SecurePilot is a multi-tenant Security & Compliance Management SaaS designed for SMEs.

The platform helps companies:

1. Connect cloud/services
2. Run security scans
3. Evaluate security rules
4. Generate security findings
5. Calculate a deterministic security score
6. Provide remediation recommendations
7. Generate security/compliance reports
8. Maintain audit history

The product should be designed with enterprise-grade security and clean architecture while remaining simple enough for SME customers.

---

## Technology Stack

### Backend

* Java 21+
* Spring Boot
* Spring Web
* Spring Security
* Spring Data JPA
* Hibernate
* MySQL 8.x
* Flyway
* Maven
* JWT authentication

### Frontend

* React
* TypeScript

### Infrastructure

* Docker
* AWS-compatible object storage where required

---

## Backend Architecture

Use a modular package-by-feature structure.

Recommended structure:

```text
com.securepilot
├── SecurePilotApplication
├── common
│   ├── config
│   ├── exception
│   ├── response
│   └── security
├── auth
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── company
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── user
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── integration
├── securitycheck
├── scan
├── finding
├── score
├── report
├── notification
├── audit
└── compliance
```

Prefer feature-based organization rather than a single global controller/service/repository hierarchy.

---

## Database

Database:

```text
MySQL 8.x
```

Use:

* InnoDB
* utf8mb4
* UTC timestamps
* UUID identifiers
* `CHAR(36)` for UUID database columns
* `DATETIME(6)` for timestamps

Hibernate must validate the existing schema rather than automatically modifying it.

Use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Do not use:

```text
ddl-auto: create
ddl-auto: create-drop
ddl-auto: update
```

for production development.

---

## Flyway

Flyway is responsible for database schema evolution.

Use:

```text
src/main/resources/db/migration/
```

Migration naming:

```text
V1__description.sql
V2__description.sql
V3__description.sql
```

The existing database schema was initially created manually.

Do not create destructive migrations or recreate the existing schema without first verifying the current database.

Long-term, move schema management completely under Flyway.

---

## Database Lookup Tables

Business statuses and roles must NOT be hard-coded as Java enums or VARCHAR columns when they are represented by database lookup/master tables.

Lookup tables include:

* roles
* company_statuses
* user_statuses
* integration_statuses
* scan_statuses
* finding_severities
* finding_statuses
* report_types
* report_statuses
* notification_types
* scan_frequencies
* mapping_strengths

Use UUID foreign keys from business tables.

Examples:

```text
companies.status              -> company_status_id
users.role                    -> role_id
users.status                  -> user_status_id
integrations.status           -> integration_status_id
scans.status                  -> scan_status_id
findings.severity             -> finding_severity_id
findings.status               -> finding_status_id
reports.report_type           -> report_type_id
reports.status                -> report_status_id
notifications.type            -> notification_type_id
scan_schedules.frequency      -> scan_frequency_id
control_mappings.mapping_strength -> mapping_strength_id
```

Lookup records should have stable business codes such as:

```text
OWNER
ADMIN
SECURITY_ANALYST
VIEWER
```

The UUID is used for relationships.

The stable `code` is used by application/business logic and APIs where appropriate.

---

## Roles

Initial roles:

```text
OWNER
ADMIN
SECURITY_ANALYST
VIEWER
```

---

## Company Statuses

```text
ACTIVE
SUSPENDED
DELETED
```

---

## User Statuses

```text
ACTIVE
INVITED
DISABLED
```

---

## Integration Statuses

```text
PENDING
CONNECTED
ERROR
DISCONNECTED
REVOKED
```

---

## Scan Statuses

```text
QUEUED
RUNNING
COMPLETED
FAILED
CANCELLED
```

---

## Finding Severities

```text
CRITICAL
HIGH
MEDIUM
LOW
INFORMATIONAL
```

---

## Finding Statuses

```text
OPEN
ACKNOWLEDGED
IN_PROGRESS
RESOLVED
ACCEPTED_RISK
```

---

## Report Types

```text
SECURITY_SUMMARY
SECURITY_ASSESSMENT
COMPLIANCE_READINESS
EXECUTIVE_SUMMARY
```

---

## Report Statuses

```text
QUEUED
GENERATING
COMPLETED
FAILED
```

---

## Notification Types

```text
SCAN_COMPLETED
CRITICAL_FINDING
HIGH_RISK_FINDING
INTEGRATION_ERROR
COMPLIANCE_ALERT
```

---

## Scan Frequencies

```text
HOURLY
DAILY
WEEKLY
MONTHLY
CUSTOM
```

---

## Mapping Strengths

```text
DIRECT
PARTIAL
SUPPORTING
```

---

# Multi-Tenancy Rules

SecurePilot is multi-tenant.

Every tenant-owned entity must be scoped to a company.

IMPORTANT:

Never trust a client-supplied `company_id` for authorization.

The company/tenant must be derived from the authenticated user's identity/security context.

All tenant-owned queries must be scoped by the authenticated company.

For example:

```text
WHERE company_id = authenticatedUser.companyId
```

Never allow a user from Company A to access Company B data by changing an ID in the request.

Parent-child relationships must also be validated against the current tenant.

---

# Authentication

Implement JWT-based authentication.

Initial APIs:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

Passwords must:

* Never be stored in plain text
* Be hashed using a strong password encoder
* Never appear in logs
* Never appear in audit metadata
* Never appear in API responses

Refresh tokens must be securely stored and handled.

---

# Company APIs

Initial APIs:

```text
GET /api/company
PUT /api/company

GET /api/company/users
POST /api/company/users/invite

PUT /api/company/users/{id}/role
PUT /api/company/users/{id}/status
```

Authorization must be enforced according to role.

---

# Security Scanning Architecture

Core product flow:

```text
Company
   ↓
Integration
   ↓
Security Check
   ↓
Scan
   ↓
Security Rules Engine
   ↓
Finding
   ↓
Risk Analysis
   ↓
Security Score
   ↓
Recommendation
   ↓
Report / Alert
```

The security rules engine must be deterministic.

AI must NOT directly calculate the security score.

AI can later be used for:

* Explanation
* Recommendations
* Natural-language summaries
* Security assistant
* Report interpretation

But the underlying security evaluation and score calculation must remain deterministic and auditable.

---

# Finding Severity

Use:

```text
CRITICAL
HIGH
MEDIUM
LOW
INFORMATIONAL
```

---

# Finding Lifecycle

Use:

```text
OPEN
ACKNOWLEDGED
IN_PROGRESS
RESOLVED
ACCEPTED_RISK
```

---

# Scan Lifecycle

Use:

```text
QUEUED
RUNNING
COMPLETED
FAILED
CANCELLED
```

---

# Audit Logging

Important audit events include:

```text
USER_LOGIN
USER_INVITED
USER_ROLE_CHANGED
INTEGRATION_CONNECTED
INTEGRATION_DISCONNECTED
SCAN_STARTED
SCAN_COMPLETED
FINDING_ACKNOWLEDGED
FINDING_RESOLVED
REPORT_GENERATED
COMPANY_SETTINGS_UPDATED
```

Never store:

* Passwords
* JWT tokens
* Refresh tokens
* API keys
* Cloud credentials
* Client secrets
* Other sensitive secrets

inside audit metadata.

---

# API Design

Base API:

```text
/api
```

Modules:

```text
/api/auth
/api/company
/api/security
/api/integrations
/api/reports
/api/notifications
/api/audit-logs
```

Use consistent HTTP status codes and a consistent API error response.

---

# Error Handling

Use a global exception handler.

API errors should have a consistent structure, for example:

```json
{
  "timestamp": "2026-09-06T10:00:00Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Company not found",
  "path": "/api/company"
}
```

Do not expose internal stack traces to API consumers.

---

# Entity Guidelines

Use a shared base entity where appropriate.

Base entity should contain:

```text
id
createdAt
updatedAt
```

UUID should be generated by the application/ORM.

Use UTC timestamps.

Avoid unnecessary bidirectional JPA relationships.

Avoid `CascadeType.ALL` unless there is a clear reason.

Security and audit history must not be accidentally deleted through cascading relationships.

---

# Coding Standards

Write production-quality code.

Prefer:

* Constructor injection
* DTOs for API requests/responses
* Validation using Jakarta Bean Validation
* Service layer for business logic
* Repository layer for persistence
* Explicit authorization checks
* Small focused classes
* Meaningful names
* Unit tests for business logic
* Integration tests for important API/database behavior

Avoid:

* Field injection
* Large controllers
* Business logic inside controllers
* Hard-coded status strings throughout the application
* Exposing JPA entities directly from REST APIs
* Logging secrets
* Returning unnecessary internal database details

---

# Development Order

Implement incrementally.

Phase 1:

```text
Spring Boot application
Configuration
Database connection
Flyway
Common exceptions
API response
Base entity
Health endpoint
```

Phase 2:

```text
Company
User
Role lookup
User status lookup
Company status lookup
```

Phase 3:

```text
Registration
Login
JWT
Refresh token
Password reset
Spring Security
RBAC
Tenant context
```

Phase 4:

```text
Integration management
Security checks
Scans
```

Phase 5:

```text
Security rule engine
Findings
Risk analysis
Security score
```

Phase 6:

```text
Dashboard
Recommendations
Reports
Notifications
Audit logs
```

Phase 7:

```text
Compliance
Scheduling
Additional integrations
Advanced security features
```

---

# Current Development Task

The immediate task is to establish the backend foundation.

First ensure the project has:

```text
SecurePilotApplication.java
pom.xml
application.yml
common/config
common/entity
common/exception
common/response
```

The Spring Boot entry point must be:

```java
package com.securepilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecurePilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePilotApplication.class, args);
    }
}
```

After the application starts successfully, implement the Company/User domain and lookup entities.

Do not jump directly into the security scanning engine.

Build the foundation first.

---

# Important Development Rule

Before implementing a feature:

1. Inspect the existing code.
2. Inspect the existing database schema/migrations.
3. Reuse existing classes where appropriate.
4. Do not overwrite working code unnecessarily.
5. Do not change the architecture without explaining why.
6. Keep changes small and logically grouped.
7. Run tests/build after significant changes.
8. Report compilation/test failures clearly.
9. Never use destructive database operations without explicit approval.
10. Do not commit secrets or credentials.

When uncertain about an architectural decision, explain the options and recommend one rather than silently making a major change.
