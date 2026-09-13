ALTER TABLE companies
    ADD COLUMN company_status_id CHAR(36) NULL;

UPDATE companies c
JOIN company_statuses cs ON cs.code = c.status
SET c.company_status_id = cs.id;

ALTER TABLE companies
    MODIFY COLUMN company_status_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_companies_company_status
        FOREIGN KEY (company_status_id) REFERENCES company_statuses(id),
    ADD INDEX idx_companies_company_status (company_status_id);

ALTER TABLE users
    ADD COLUMN role_id CHAR(36) NULL,
    ADD COLUMN user_status_id CHAR(36) NULL;

UPDATE users u
JOIN roles r ON r.code = u.role
JOIN user_statuses us ON us.code = u.status
SET u.role_id = r.id,
    u.user_status_id = us.id;

ALTER TABLE users
    MODIFY COLUMN role_id CHAR(36) NOT NULL,
    MODIFY COLUMN user_status_id CHAR(36) NOT NULL,
    ADD CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles(id),
    ADD CONSTRAINT fk_users_user_status
        FOREIGN KEY (user_status_id) REFERENCES user_statuses(id),
    ADD INDEX idx_users_role (role_id),
    ADD INDEX idx_users_user_status (user_status_id);
