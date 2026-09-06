# SecurePilot — Complete Database Table Schema

**Version:** 1.0  
**Database:** MySQL 8.x  
**Backend:** Java 21+ / Spring Boot / Spring Data JPA  
**Migration:** Flyway  
**Architecture:** Multi-tenant SaaS

## 1. Scope

This document defines the complete relational schema for the SecurePilot MVP and the planned V2 compliance/scheduling foundation.

Tables covered:

- `companies`
- `users`
- `refresh_tokens`
- `password_reset_tokens`
- `integrations`
- `integration_credentials`
- `security_checks`
- `scans`
- `findings`
- `security_score_history`
- `reports`
- `notifications`
- `audit_logs`
- `compliance_frameworks`
- `compliance_controls`
- `control_mappings`
- `scan_schedules`

## 2. Database Conventions

| Item | Standard |
|---|---|
| Database | MySQL 8.x |
| Engine | InnoDB |
| Character set | utf8mb4 |
| Collation | utf8mb4_unicode_ci |
| IDs | UUID as CHAR(36) |
| Timestamps | DATETIME(6), UTC |
| Tenant key | company_id |
| Migration | Flyway |

## 3. Relationship Overview

```text
companies
  ├── users
  │    ├── refresh_tokens
  │    └── password_reset_tokens
  ├── integrations
  │    └── integration_credentials
  ├── scans
  │    └── findings ─── security_checks
  ├── security_score_history
  ├── reports
  ├── notifications
  ├── audit_logs
  └── scan_schedules

compliance_frameworks
  └── compliance_controls
       └── control_mappings ─── security_checks
```

# 4. Core Tables

## 4.1 companies

```sql
CREATE TABLE companies (
    id              CHAR(36)     NOT NULL,
    name            VARCHAR(200) NOT NULL,
    legal_name      VARCHAR(250) NULL,
    industry        VARCHAR(100) NULL,
    employee_count  INT          NULL,
    country_code    CHAR(2)      NULL,
    timezone        VARCHAR(64)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,

    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT chk_companies_employee_count
        CHECK (employee_count IS NULL OR employee_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_companies_status ON companies(status);
CREATE INDEX idx_companies_created_at ON companies(created_at);
```

Statuses:

```text
ACTIVE
SUSPENDED
DELETED
```

## 4.2 users

```sql
CREATE TABLE users (
    id              CHAR(36)     NOT NULL,
    company_id      CHAR(36)     NOT NULL,
    name            VARCHAR(150) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NULL,
    role            VARCHAR(30)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at   DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uq_users_company_email
        UNIQUE (company_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_company ON users(company_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
```

Roles:

```text
OWNER
ADMIN
SECURITY_ANALYST
VIEWER
```

Statuses:

```text
ACTIVE
INVITED
DISABLED
```

# 5. Authentication Tables

## 5.1 refresh_tokens

Only a hash of the refresh token is stored.

```sql
CREATE TABLE refresh_tokens (
    id            CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    token_hash    VARCHAR(255) NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    revoked_at    DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    user_agent    VARCHAR(500) NULL,
    ip_address    VARCHAR(45)  NULL,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_refresh_tokens_hash
        UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
```

**Security:** Never store the raw refresh token.

## 5.2 password_reset_tokens

```sql
CREATE TABLE password_reset_tokens (
    id          CHAR(36)     NOT NULL,
    user_id     CHAR(36)     NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_password_reset_tokens_hash
        UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_password_reset_tokens_user
    ON password_reset_tokens(user_id);

CREATE INDEX idx_password_reset_tokens_expires_at
    ON password_reset_tokens(expires_at);
```

# 6. Integration Tables

## 6.1 integrations

```sql
CREATE TABLE integrations (
    id                   CHAR(36)     NOT NULL,
    company_id           CHAR(36)     NOT NULL,
    provider             VARCHAR(50)  NOT NULL,
    integration_type     VARCHAR(50)  NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    external_account_id  VARCHAR(255) NULL,
    connected_by_user_id CHAR(36)     NULL,
    last_scan_at         DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,

    CONSTRAINT pk_integrations PRIMARY KEY (id),
    CONSTRAINT fk_integrations_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_integrations_connected_by
        FOREIGN KEY (connected_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_integrations_company
    ON integrations(company_id);

CREATE INDEX idx_integrations_company_status
    ON integrations(company_id, status);

CREATE INDEX idx_integrations_provider
    ON integrations(provider);
```

Statuses:

```text
PENDING
CONNECTED
ERROR
DISCONNECTED
REVOKED
```

Potential providers:

```text
Google Workspace
Microsoft 365
AWS
Azure
GitHub
```

## 6.2 integration_credentials

Sensitive credentials are isolated from integration metadata.

```sql
CREATE TABLE integration_credentials (
    id                       CHAR(36)    NOT NULL,
    integration_id           CHAR(36)    NOT NULL,
    encrypted_access_token   TEXT        NOT NULL,
    encrypted_refresh_token  TEXT        NULL,
    token_expires_at         DATETIME(6) NULL,
    encryption_key_version   VARCHAR(30) NOT NULL,
    created_at               DATETIME(6) NOT NULL,
    updated_at               DATETIME(6) NOT NULL,

    CONSTRAINT pk_integration_credentials PRIMARY KEY (id),
    CONSTRAINT fk_integration_credentials_integration
        FOREIGN KEY (integration_id) REFERENCES integrations(id),
    CONSTRAINT uq_integration_credentials_integration
        UNIQUE (integration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Security requirements:**

- Encrypt access and refresh tokens.
- Never log credentials.
- Never return credentials through REST APIs.
- Use a managed encryption/secrets service in production where possible.
- Support encryption-key rotation through `encryption_key_version`.

# 7. Security Rules

## 7.1 security_checks

```sql
CREATE TABLE security_checks (
    id                    CHAR(36)     NOT NULL,
    code                  VARCHAR(100) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT         NOT NULL,
    category              VARCHAR(80)  NOT NULL,
    default_severity      VARCHAR(30)  NOT NULL,
    remediation_guidance  TEXT         NULL,
    provider              VARCHAR(50)  NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,

    CONSTRAINT pk_security_checks PRIMARY KEY (id),
    CONSTRAINT uq_security_checks_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_checks_provider ON security_checks(provider);
CREATE INDEX idx_security_checks_category ON security_checks(category);
CREATE INDEX idx_security_checks_active ON security_checks(active);
```

Initial checks:

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

# 8. Scan Tables

## 8.1 scans

```sql
CREATE TABLE scans (
    id                   CHAR(36)     NOT NULL,
    company_id           CHAR(36)     NOT NULL,
    integration_id       CHAR(36)     NOT NULL,
    triggered_by_user_id CHAR(36)     NULL,
    status               VARCHAR(30)  NOT NULL,
    score                DECIMAL(5,2) NULL,
    checks_total         INT          NOT NULL DEFAULT 0,
    checks_passed        INT          NOT NULL DEFAULT 0,
    checks_failed        INT          NOT NULL DEFAULT 0,
    started_at           DATETIME(6)  NULL,
    completed_at         DATETIME(6)  NULL,
    error_message        TEXT         NULL,
    created_at           DATETIME(6)  NOT NULL,

    CONSTRAINT pk_scans PRIMARY KEY (id),
    CONSTRAINT fk_scans_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_scans_integration
        FOREIGN KEY (integration_id) REFERENCES integrations(id),
    CONSTRAINT fk_scans_triggered_by
        FOREIGN KEY (triggered_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_scans_score
        CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    CONSTRAINT chk_scans_counts
        CHECK (checks_total >= 0 AND checks_passed >= 0 AND checks_failed >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_scans_company_created_at
    ON scans(company_id, created_at);

CREATE INDEX idx_scans_company_status
    ON scans(company_id, status);

CREATE INDEX idx_scans_integration
    ON scans(integration_id);
```

Statuses:

```text
QUEUED
RUNNING
COMPLETED
FAILED
CANCELLED
```

Completed scans are historical evidence and should not be destructively modified.

## 8.2 findings

```sql
CREATE TABLE findings (
    id                       CHAR(36)     NOT NULL,
    company_id               CHAR(36)     NOT NULL,
    scan_id                  CHAR(36)     NOT NULL,
    security_check_id        CHAR(36)     NOT NULL,
    severity                 VARCHAR(30)  NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    title                    VARCHAR(250) NOT NULL,
    description              TEXT         NOT NULL,
    recommendation           TEXT         NULL,
    evidence                 JSON         NULL,
    affected_resource_count  INT          NOT NULL DEFAULT 0,
    first_detected_at        DATETIME(6)  NOT NULL,
    resolved_at              DATETIME(6)  NULL,
    created_at               DATETIME(6)  NOT NULL,

    CONSTRAINT pk_findings PRIMARY KEY (id),
    CONSTRAINT fk_findings_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_findings_scan
        FOREIGN KEY (scan_id) REFERENCES scans(id),
    CONSTRAINT fk_findings_security_check
        FOREIGN KEY (security_check_id) REFERENCES security_checks(id),
    CONSTRAINT chk_findings_resource_count
        CHECK (affected_resource_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_findings_company_scan
    ON findings(company_id, scan_id);

CREATE INDEX idx_findings_company_severity_status
    ON findings(company_id, severity, status);

CREATE INDEX idx_findings_company_status
    ON findings(company_id, status);

CREATE INDEX idx_findings_security_check
    ON findings(security_check_id);
```

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

The `evidence` JSON should contain only the structured evidence needed to explain the finding. Minimize sensitive customer data.

# 9. Security Score

## 9.1 security_score_history

```sql
CREATE TABLE security_score_history (
    id              CHAR(36)     NOT NULL,
    company_id      CHAR(36)     NOT NULL,
    scan_id         CHAR(36)     NOT NULL,
    score           DECIMAL(5,2) NOT NULL,
    critical_count  INT          NOT NULL DEFAULT 0,
    high_count      INT          NOT NULL DEFAULT 0,
    medium_count    INT          NOT NULL DEFAULT 0,
    low_count       INT          NOT NULL DEFAULT 0,
    calculated_at   DATETIME(6)  NOT NULL,

    CONSTRAINT pk_security_score_history PRIMARY KEY (id),
    CONSTRAINT fk_score_history_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_score_history_scan
        FOREIGN KEY (scan_id) REFERENCES scans(id),
    CONSTRAINT chk_score_history_score
        CHECK (score >= 0 AND score <= 100),
    CONSTRAINT chk_score_history_counts
        CHECK (
            critical_count >= 0
            AND high_count >= 0
            AND medium_count >= 0
            AND low_count >= 0
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_score_history_company_calculated_at
    ON security_score_history(company_id, calculated_at);

CREATE INDEX idx_score_history_scan
    ON security_score_history(scan_id);
```

**Critical rule:** the score is calculated deterministically by the Security Rules Engine. AI must not directly calculate the security score.

# 10. Reports

## 10.1 reports

```sql
CREATE TABLE reports (
    id                   CHAR(36)     NOT NULL,
    company_id           CHAR(36)     NOT NULL,
    scan_id              CHAR(36)     NOT NULL,
    generated_by_user_id CHAR(36)     NOT NULL,
    report_type          VARCHAR(50)  NOT NULL,
    storage_key          VARCHAR(500) NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    created_at           DATETIME(6)  NOT NULL,

    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_reports_scan
        FOREIGN KEY (scan_id) REFERENCES scans(id),
    CONSTRAINT fk_reports_generated_by
        FOREIGN KEY (generated_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reports_company_created_at
    ON reports(company_id, created_at);

CREATE INDEX idx_reports_scan ON reports(scan_id);
CREATE INDEX idx_reports_status ON reports(status);
```

Report types:

```text
SECURITY_SUMMARY
SECURITY_ASSESSMENT
COMPLIANCE_READINESS
EXECUTIVE_SUMMARY
```

Suggested statuses:

```text
QUEUED
GENERATING
COMPLETED
FAILED
```

For the MVP, PDF files should be stored in object storage; MySQL stores only the object key/reference.

# 11. Compliance Tables

## 11.1 compliance_frameworks

```sql
CREATE TABLE compliance_frameworks (
    id          CHAR(36)     NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    version     VARCHAR(50)  NULL,
    description TEXT         NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,

    CONSTRAINT pk_compliance_frameworks PRIMARY KEY (id),
    CONSTRAINT uq_compliance_frameworks_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

Potential frameworks:

```text
SOC2
ISO27001
GDPR
DPDP
HIPAA
```

SecurePilot must not claim certification or guarantee compliance.

## 11.2 compliance_controls

```sql
CREATE TABLE compliance_controls (
    id            CHAR(36)     NOT NULL,
    framework_id  CHAR(36)     NOT NULL,
    control_code  VARCHAR(100) NOT NULL,
    name          VARCHAR(250) NOT NULL,
    description   TEXT         NULL,
    category      VARCHAR(100) NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,

    CONSTRAINT pk_compliance_controls PRIMARY KEY (id),
    CONSTRAINT fk_compliance_controls_framework
        FOREIGN KEY (framework_id) REFERENCES compliance_frameworks(id),
    CONSTRAINT uq_compliance_controls_framework_code
        UNIQUE (framework_id, control_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_compliance_controls_framework
    ON compliance_controls(framework_id);

CREATE INDEX idx_compliance_controls_category
    ON compliance_controls(category);
```

## 11.3 control_mappings

```sql
CREATE TABLE control_mappings (
    id                     CHAR(36)    NOT NULL,
    security_check_id      CHAR(36)    NOT NULL,
    compliance_control_id  CHAR(36)    NOT NULL,
    mapping_strength       VARCHAR(30) NOT NULL,
    created_at             DATETIME(6) NOT NULL,

    CONSTRAINT pk_control_mappings PRIMARY KEY (id),
    CONSTRAINT fk_control_mappings_security_check
        FOREIGN KEY (security_check_id) REFERENCES security_checks(id),
    CONSTRAINT fk_control_mappings_compliance_control
        FOREIGN KEY (compliance_control_id) REFERENCES compliance_controls(id),
    CONSTRAINT uq_control_mappings_check_control
        UNIQUE (security_check_id, compliance_control_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_control_mappings_security_check
    ON control_mappings(security_check_id);

CREATE INDEX idx_control_mappings_compliance_control
    ON control_mappings(compliance_control_id);
```

Suggested mapping strengths:

```text
DIRECT
PARTIAL
SUPPORTING
```

# 12. Notifications

## 12.1 notifications

```sql
CREATE TABLE notifications (
    id              CHAR(36)     NOT NULL,
    company_id      CHAR(36)     NOT NULL,
    user_id         CHAR(36)     NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(250) NOT NULL,
    message         TEXT         NOT NULL,
    reference_type  VARCHAR(50)  NULL,
    reference_id    CHAR(36)     NULL,
    read_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notifications_company_user_read
    ON notifications(company_id, user_id, read_at);

CREATE INDEX idx_notifications_company_created_at
    ON notifications(company_id, created_at);
```

Types:

```text
SCAN_COMPLETED
CRITICAL_FINDING
HIGH_RISK_FINDING
INTEGRATION_ERROR
COMPLIANCE_ALERT
```

# 13. Audit Logs

## 13.1 audit_logs

```sql
CREATE TABLE audit_logs (
    id           CHAR(36)     NOT NULL,
    company_id   CHAR(36)     NOT NULL,
    user_id      CHAR(36)     NULL,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(80)  NOT NULL,
    entity_id    CHAR(36)     NULL,
    metadata     JSON         NULL,
    ip_address   VARCHAR(45)  NULL,
    user_agent   VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,

    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_logs_company_created_at
    ON audit_logs(company_id, created_at);

CREATE INDEX idx_audit_logs_user_created_at
    ON audit_logs(user_id, created_at);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id);

CREATE INDEX idx_audit_logs_action
    ON audit_logs(action);
```

Audit actions:

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

Never store passwords, access tokens, refresh tokens, API keys, encryption keys, or other secrets in audit metadata.

# 14. Scan Scheduling

## 14.1 scan_schedules

```sql
CREATE TABLE scan_schedules (
    id              CHAR(36)     NOT NULL,
    company_id      CHAR(36)     NOT NULL,
    integration_id  CHAR(36)     NOT NULL,
    frequency       VARCHAR(30)  NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    next_run_at     DATETIME(6)  NOT NULL,
    last_run_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,

    CONSTRAINT pk_scan_schedules PRIMARY KEY (id),
    CONSTRAINT fk_scan_schedules_company
        FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_scan_schedules_integration
        FOREIGN KEY (integration_id) REFERENCES integrations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_scan_schedules_company
    ON scan_schedules(company_id);

CREATE INDEX idx_scan_schedules_next_run
    ON scan_schedules(enabled, next_run_at);

CREATE INDEX idx_scan_schedules_integration
    ON scan_schedules(integration_id);
```

Frequency:

```text
HOURLY
DAILY
WEEKLY
MONTHLY
CUSTOM
```

Manual scanning should be implemented before scheduled scanning.

# 15. Flyway Migration Order

Recommended files:

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

Keep applied migrations immutable. Never edit an already-applied production migration; create a new migration for schema changes.

# 16. Foreign-Key Dependency Order

```text
companies
  ↓
users
  ↓
refresh_tokens / password_reset_tokens

companies
  ↓
integrations
  ↓
integration_credentials

security_checks
  ↓
scans
  ↓
findings
  ↓
security_score_history

compliance_frameworks
  ↓
compliance_controls
  ↓
control_mappings

companies
  ↓
reports / notifications / audit_logs / scan_schedules
```

# 17. Tenant Isolation Requirements

SecurePilot is multi-tenant.

### Rule 1 — Never trust client-supplied company_id

```text
JWT
 ↓
Authenticated User
 ↓
User.company_id
 ↓
Tenant Context
```

### Rule 2 — Scope tenant queries

Prefer:

```java
findByCompanyIdAndId(companyId, resourceId)
```

instead of:

```java
findById(resourceId)
```

for tenant-owned resources.

### Rule 3 — Validate parent relationships

```text
Authenticated company
        ↓
resource.company_id
        ↓
must match
```

Apply this to users, integrations, scans, findings, reports, notifications, audit logs, schedules and score history.

# 18. Delete Strategy

For security and audit history, prefer:

```text
ON DELETE RESTRICT
```

or application-level soft deletion.

Avoid destructive cascading deletion:

```text
company
  ↓
scans
  ↓
findings
```

Security history may be required for auditability.

# 19. MVP vs Future

## MVP

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

## V2

```text
scan_schedules
compliance_frameworks
compliance_controls
control_mappings
```

## V3+

Potential future tables:

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

# 20. SecurePilot Core Data Flow

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
Score History
   ↓
Report
   ↓
Notification
   ↓
Audit Log
```

# 21. Phase 1 Recommendation

Do not implement every table at once.

Start Phase 1 with:

```text
V1 companies
V2 users
V3 authentication tables
```

Then implement:

```text
Company Entity
      ↓
User Entity
      ↓
Registration
      ↓
Login
      ↓
JWT Authentication
      ↓
RBAC
      ↓
Tenant Context
```

After authentication and tenant isolation are proven, implement:

```text
Integrations
      ↓
Security Checks
      ↓
Scans
      ↓
Findings
      ↓
Security Score
```

This schema is based on the existing SecurePilot database design and functional specification.
