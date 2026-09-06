# SecurePilot — Database Design

**Version:** 1.0  
**Database:** MySQL 8.x  
**Backend:** Java 21+ / Spring Boot / Spring Data JPA  
**Migration:** Flyway  
**Architecture:** Multi-tenant SaaS

## 1. Purpose

This document defines the initial relational database design for SecurePilot, a SaaS platform that helps SMEs monitor security posture, identify risks, manage compliance readiness, and generate security reports.

The design is intended for the MVP and should remain extensible for future integrations, compliance frameworks, AI capabilities, and automated remediation.

## 2. Design Principles

1. **Tenant isolation first** — customer-owned records must be associated with a company/tenant.
2. **UUID identifiers** — use UUIDs for externally exposed identifiers.
3. **Auditability** — scans, findings, status changes, and important administrative actions should be traceable.
4. **Least privilege** — application roles and integration permissions follow least privilege.
5. **Encrypted secrets** — OAuth refresh tokens, API credentials, and other integration secrets must never be plaintext.
6. **Immutable scan history** — completed scans and findings remain historically reproducible.
7. **Soft deletion where appropriate** — avoid destructive deletion of security/audit history.
8. **UTC timestamps** — store timestamps in UTC.

## 3. High-Level Entity Model

```text
companies
    |
    +---- users
    |
    +---- integrations
    |         |
    |         +---- integration_credentials
    |
    +---- scans
    |         |
    |         +---- findings
    |                    |
    |                    +---- security_checks
    |
    +---- reports
    |
    +---- notifications
    |
    +---- audit_logs
    |
    +---- security_score_history
    |
    +---- scan_schedules

compliance_frameworks
    |
    +---- compliance_controls
               |
               +---- control_mappings ---- security_checks
```

## 4. Core Tables

### 4.1 `companies`

Represents a SecurePilot customer/tenant.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | CHAR(36) | PK | Company UUID |
| name | VARCHAR(200) | NOT NULL | Company name |
| legal_name | VARCHAR(250) | NULL | Legal business name |
| industry | VARCHAR(100) | NULL | Industry |
| employee_count | INT | NULL | Approximate employee count |
| country_code | CHAR(2) | NULL | ISO country code |
| timezone | VARCHAR(64) | NOT NULL | Company timezone |
| status | VARCHAR(30) | NOT NULL | ACTIVE / SUSPENDED / DELETED |
| created_at | DATETIME(6) | NOT NULL | Creation timestamp |
| updated_at | DATETIME(6) | NOT NULL | Last update |
| deleted_at | DATETIME(6) | NULL | Soft deletion timestamp |

Indexes:
- `idx_companies_status(status)`
- `idx_companies_created_at(created_at)`

### 4.2 `users`

Users belonging to a company.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | CHAR(36) | PK | User UUID |
| company_id | CHAR(36) | FK | Tenant |
| name | VARCHAR(150) | NOT NULL | User name |
| email | VARCHAR(255) | NOT NULL | Login email |
| password_hash | VARCHAR(255) | NULL | Argon2id/bcrypt hash |
| role | VARCHAR(30) | NOT NULL | OWNER / ADMIN / SECURITY_ANALYST / VIEWER |
| status | VARCHAR(30) | NOT NULL | ACTIVE / INVITED / DISABLED |
| email_verified | BOOLEAN | NOT NULL | Verification state |
| last_login_at | DATETIME(6) | NULL | Last successful login |
| created_at | DATETIME(6) | NOT NULL | Creation timestamp |
| updated_at | DATETIME(6) | NOT NULL | Last update |

Constraints:
- FK `company_id → companies.id`
- Unique `(company_id, email)`

Indexes:
- `idx_users_company(company_id)`
- `idx_users_email(email)`
- `idx_users_status(status)`

## 5. Authentication Tables

### 5.1 `refresh_tokens`

Stores **hashed** refresh tokens for session renewal.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| user_id | CHAR(36) | FK |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL |
| expires_at | DATETIME(6) | NOT NULL |
| revoked_at | DATETIME(6) | NULL |
| created_at | DATETIME(6) | NOT NULL |
| user_agent | VARCHAR(500) | NULL |
| ip_address | VARCHAR(45) | NULL |

Never store the raw refresh token.

### 5.2 `password_reset_tokens`

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| user_id | CHAR(36) | FK |
| token_hash | VARCHAR(255) | UNIQUE, NOT NULL |
| expires_at | DATETIME(6) | NOT NULL |
| used_at | DATETIME(6) | NULL |
| created_at | DATETIME(6) | NOT NULL |

## 6. Integration Tables

### 6.1 `integrations`

Represents a connected business/cloud service.

Potential providers:
- Google Workspace
- Microsoft 365
- AWS
- Azure
- GitHub

The MVP should initially support only one integration.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| provider | VARCHAR(50) | NOT NULL |
| integration_type | VARCHAR(50) | NOT NULL |
| status | VARCHAR(30) | NOT NULL |
| external_account_id | VARCHAR(255) | NULL |
| connected_by_user_id | CHAR(36) | FK |
| last_scan_at | DATETIME(6) | NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

Statuses:

```text
PENDING
CONNECTED
ERROR
DISCONNECTED
REVOKED
```

### 6.2 `integration_credentials`

Sensitive credentials are isolated from normal integration metadata.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| integration_id | CHAR(36) | FK, UNIQUE |
| encrypted_access_token | TEXT | NOT NULL |
| encrypted_refresh_token | TEXT | NULL |
| token_expires_at | DATETIME(6) | NULL |
| encryption_key_version | VARCHAR(30) | NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

Use application-level encryption or a managed secrets/encryption service. Never log credentials.

## 7. Security Rules

### 7.1 `security_checks`

Defines the checks SecurePilot performs.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| code | VARCHAR(100) | UNIQUE, NOT NULL |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | NOT NULL |
| category | VARCHAR(80) | NOT NULL |
| default_severity | VARCHAR(30) | NOT NULL |
| remediation_guidance | TEXT | NULL |
| provider | VARCHAR(50) | NULL |
| active | BOOLEAN | NOT NULL |
| created_at | DATETIME(6) | NOT NULL |
| updated_at | DATETIME(6) | NOT NULL |

Example checks:

```text
MFA_ENABLED
INACTIVE_USERS
EXCESSIVE_PRIVILEGES
MULTIPLE_ADMINS
PUBLIC_FILES
EXTERNAL_SHARING
SENSITIVE_FILE_SHARING
WEAK_AUTHENTICATION
SECURITY_CONFIGURATION
ACCESS_POLICY
```

## 8. Scan Tables

### 8.1 `scans`

Represents a security assessment execution.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| integration_id | CHAR(36) | FK |
| triggered_by_user_id | CHAR(36) | FK, NULL |
| status | VARCHAR(30) | NOT NULL |
| score | DECIMAL(5,2) | NULL |
| checks_total | INT | NOT NULL |
| checks_passed | INT | NOT NULL |
| checks_failed | INT | NOT NULL |
| started_at | DATETIME(6) | NULL |
| completed_at | DATETIME(6) | NULL |
| error_message | TEXT | NULL |
| created_at | DATETIME(6) | NOT NULL |

Statuses:

```text
QUEUED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Completed scans should be treated as historical evidence.

### 8.2 `findings`

Stores an individual security-check result within a scan.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| scan_id | CHAR(36) | FK |
| security_check_id | CHAR(36) | FK |
| severity | VARCHAR(30) | NOT NULL |
| status | VARCHAR(30) | NOT NULL |
| title | VARCHAR(250) | NOT NULL |
| description | TEXT | NOT NULL |
| recommendation | TEXT | NULL |
| evidence | JSON | NULL |
| affected_resource_count | INT | NOT NULL |
| first_detected_at | DATETIME(6) | NOT NULL |
| resolved_at | DATETIME(6) | NULL |
| created_at | DATETIME(6) | NOT NULL |

Severity:

```text
CRITICAL
HIGH
MEDIUM
LOW
INFORMATIONAL
```

Finding status:

```text
OPEN
ACKNOWLEDGED
IN_PROGRESS
RESOLVED
ACCEPTED_RISK
```

The `evidence` JSON should contain only the structured evidence required to explain the finding. Minimize sensitive customer data.

## 9. Security Score

### 9.1 `security_score_history`

Stores score history for trend reporting.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| scan_id | CHAR(36) | FK |
| score | DECIMAL(5,2) | NOT NULL |
| critical_count | INT | NOT NULL |
| high_count | INT | NOT NULL |
| medium_count | INT | NOT NULL |
| low_count | INT | NOT NULL |
| calculated_at | DATETIME(6) | NOT NULL |

### Score rule

The score must be calculated deterministically by the Security Rules Engine.

**AI must not directly calculate the security score.**

## 10. Reports

### 10.1 `reports`

Stores generated security reports.

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| scan_id | CHAR(36) | FK |
| generated_by_user_id | CHAR(36) | FK |
| report_type | VARCHAR(50) | NOT NULL |
| storage_key | VARCHAR(500) | NOT NULL |
| status | VARCHAR(30) | NOT NULL |
| created_at | DATETIME(6) | NOT NULL |

Report types:

```text
SECURITY_SUMMARY
SECURITY_ASSESSMENT
COMPLIANCE_READINESS
EXECUTIVE_SUMMARY
```

For the MVP, store PDF files in object storage such as S3-compatible storage and store only the object key/reference in MySQL.

## 11. Compliance Tables

These can be implemented in the foundation and populated more extensively in V2.

### 11.1 `compliance_frameworks`

| Column | Type |
|---|---|
| id | CHAR(36) PK |
| code | VARCHAR(50) UNIQUE |
| name | VARCHAR(150) |
| version | VARCHAR(50) |
| description | TEXT |
| active | BOOLEAN |
| created_at | DATETIME(6) |

Potential frameworks:

```text
SOC2
ISO27001
GDPR
DPDP
HIPAA
```

SecurePilot should not claim certification or guarantee compliance.

### 11.2 `compliance_controls`

| Column | Type |
|---|---|
| id | CHAR(36) PK |
| framework_id | CHAR(36) FK |
| control_code | VARCHAR(100) |
| name | VARCHAR(250) |
| description | TEXT |
| category | VARCHAR(100) |
| active | BOOLEAN |
| created_at | DATETIME(6) |

Unique:

```text
(framework_id, control_code)
```

### 11.3 `control_mappings`

Maps security checks to compliance controls.

| Column | Type |
|---|---|
| id | CHAR(36) PK |
| security_check_id | CHAR(36) FK |
| compliance_control_id | CHAR(36) FK |
| mapping_strength | VARCHAR(30) |
| created_at | DATETIME(6) |

## 12. Notifications

### 12.1 `notifications`

| Column | Type | Constraints |
|---|---|---|
| id | CHAR(36) | PK |
| company_id | CHAR(36) | FK |
| user_id | CHAR(36) | FK |
| type | VARCHAR(50) | NOT NULL |
| title | VARCHAR(250) | NOT NULL |
| message | TEXT | NOT NULL |
| reference_type | VARCHAR(50) | NULL |
| reference_id | CHAR(36) | NULL |
| read_at | DATETIME(6) | NULL |
| created_at | DATETIME(6) | NOT NULL |

Types:

```text
SCAN_COMPLETED
CRITICAL_FINDING
HIGH_RISK_FINDING
INTEGRATION_ERROR
COMPLIANCE_ALERT
```

## 13. Scan Scheduling

### 13.1 `scan_schedules`

Useful when continuous/scheduled monitoring is introduced.

| Column | Type |
|---|---|
| id | CHAR(36) PK |
| company_id | CHAR(36) FK |
| integration_id | CHAR(36) FK |
| frequency | VARCHAR(30) |
| cron_expression | VARCHAR(100) |
| enabled | BOOLEAN |
| next_run_at | DATETIME(6) |
| last_run_at | DATETIME(6) |
| created_at | DATETIME(6) |
| updated_at | DATETIME(6) |

For the MVP, manual scanning can be implemented first.

## 14. Audit Logging

### 14.1 `audit_logs`

Important security and administrative operations should be auditable.

| Column | Type |
|---|---|
| id | CHAR(36) PK |
| company_id | CHAR(36) FK |
| user_id | CHAR(36) FK, NULL |
| action | VARCHAR(100) |
| entity_type | VARCHAR(80) |
| entity_id | CHAR(36), NULL |
| metadata | JSON, NULL |
| ip_address | VARCHAR(45), NULL |
| user_agent | VARCHAR(500), NULL |
| created_at | DATETIME(6) |

Examples:

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

Never store passwords, access tokens, refresh tokens, or other secrets in audit metadata.

## 15. Multi-Tenancy Rules

SecurePilot is a multi-tenant SaaS application.

### Rule 1 — Never trust `company_id` from the client

The backend should derive tenant context from the authenticated user/session.

### Rule 2 — Scope tenant-owned queries

Prefer:

```java
findByCompanyIdAndId(companyId, findingId)
```

over:

```java
findById(findingId)
```

for tenant-owned resources.

### Rule 3 — Validate parent relationships

For example:

```text
authenticated company
        ↓
finding.company_id
        ↓
must match
```

Apply the same principle to scans, reports, integrations, users, notifications, and schedules.

## 16. Recommended MySQL Conventions

**Storage engine**

```text
InnoDB
```

**Character set**

```text
utf8mb4
```

**IDs**

Use UUIDs represented as `CHAR(36)` initially for simplicity. They can later be optimized to binary UUID storage if scale requires it.

**Timestamps**

Use:

```text
DATETIME(6)
```

Store timestamps in UTC.

## 17. Important Indexes

At minimum:

```text
users:
  (company_id)
  (company_id, email)

integrations:
  (company_id)
  (company_id, status)

scans:
  (company_id, created_at)
  (company_id, status)

findings:
  (company_id, scan_id)
  (company_id, severity, status)
  (company_id, status)

reports:
  (company_id, created_at)

notifications:
  (company_id, user_id, read_at)

audit_logs:
  (company_id, created_at)
```

Review indexes against real query patterns after the MVP is running.

## 18. Foreign-Key Behavior

For customer-owned security history, prefer:

```text
ON DELETE RESTRICT
```

or application-level soft deletion.

Avoid cascading deletion from:

```text
company
    ↓
scans
    ↓
findings
```

because security history may be required for auditability.

## 19. MVP Database Flow

```text
1. User creates company
       ↓
2. User connects cloud service
       ↓
3. integration + encrypted credentials
       ↓
4. User clicks "Run Security Scan"
       ↓
5. scans = RUNNING
       ↓
6. Security Engine executes security_checks
       ↓
7. findings created
       ↓
8. Security Score calculated
       ↓
9. security_score_history created
       ↓
10. scans = COMPLETED
       ↓
11. notification created
       ↓
12. User generates report
       ↓
13. reports record created
```

## 20. MVP vs Future Tables

### MVP — Build First

```text
companies
users
refresh_tokens
password_reset_tokens
integrations
integration_credentials
security_checks
scans
findings
security_score_history
reports
notifications
audit_logs
```

### V2

```text
scan_schedules
compliance_frameworks
compliance_controls
control_mappings
```

### V3+

Potential additions:

```text
vendors
vendor_risks
security_questionnaires
evidence
policy_documents
remediation_tasks
risk_acceptances
ai_conversations
ai_messages
webhook_events
billing_accounts
subscriptions
usage_records
```

## 21. Suggested Java Entity Structure

```text
com.securepilot
│
├── company
│   └── Company
│
├── user
│   └── User
│
├── auth
│   ├── RefreshToken
│   └── PasswordResetToken
│
├── integration
│   ├── Integration
│   └── IntegrationCredential
│
├── scanner
│   ├── SecurityCheck
│   ├── Scan
│   ├── Finding
│   └── SecurityScoreHistory
│
├── report
│   └── Report
│
├── notification
│   └── Notification
│
├── audit
│   └── AuditLog
│
└── compliance
    ├── ComplianceFramework
    ├── ComplianceControl
    └── ControlMapping
```

## 22. Flyway Migration Strategy

Use versioned migrations:

```text
V1__create_companies.sql
V2__create_users.sql
V3__create_auth_tables.sql
V4__create_integrations.sql
V5__create_security_checks.sql
V6__create_scans_and_findings.sql
V7__create_security_score_history.sql
V8__create_reports.sql
V9__create_notifications.sql
V10__create_audit_logs.sql
V11__create_compliance_tables.sql
V12__create_scan_schedules.sql
```

Keep applied migrations immutable. Create a new migration for schema changes instead of editing an already-applied migration.

## 23. Initial Seed Data

Seed approximately 10–20 deterministic security checks for the MVP:

```text
MFA_ENABLED
INACTIVE_USERS
EXCESSIVE_PRIVILEGES
MULTIPLE_ADMINS
PUBLIC_FILES
EXTERNAL_SHARING
SENSITIVE_FILE_SHARING
WEAK_AUTHENTICATION
SECURITY_CONFIGURATION
ACCESS_POLICY
```

Each check should contain:

```text
code
name
description
category
default_severity
remediation_guidance
provider
active
```

## 24. Recommended MVP Architecture

```text
                    ┌─────────────────────┐
                    │     React Web App   │
                    └──────────┬──────────┘
                               │ HTTPS
                               ▼
                    ┌─────────────────────┐
                    │ Spring Boot API     │
                    ├─────────────────────┤
                    │ Auth / RBAC         │
                    │ Tenant Context      │
                    │ Security Engine     │
                    │ Report Service      │
                    └──────────┬──────────┘
                               │
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
        ┌─────────────────┐       ┌─────────────────┐
        │ MySQL           │       │ Object Storage  │
        │                 │       │                 │
        │ Tenant data     │       │ PDF reports     │
        │ Scans           │       │ Evidence/files  │
        │ Findings        │       │                 │
        └─────────────────┘       └─────────────────┘
                 ▲
                 │
        ┌────────┴─────────┐
        │ External APIs    │
        │ Cloud providers  │
        │ SaaS services    │
        └──────────────────┘
```

## 25. Final Recommendation

Do not over-engineer the first SecurePilot database.

The core MVP data flow should be:

```text
Company
  ↓
User
  ↓
Integration
  ↓
Security Checks
  ↓
Scan
  ↓
Findings
  ↓
Security Score
  ↓
Report
```

Keep compliance, scheduled scans, AI conversations, vendor risk, billing, and automated remediation extensible without making them dependencies for the first working release.

The highest-priority database requirements are:

**1. Strong tenant isolation**  
**2. Secure credential storage**  
**3. Immutable scan history**  
**4. Reliable audit logging**  
**5. A schema that can evolve through Flyway migrations**
