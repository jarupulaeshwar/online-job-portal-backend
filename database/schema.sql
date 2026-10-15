-- =====================================================================
-- Online Job Portal - MySQL 8 schema
-- This file is the ONLY place where tables are created. Hibernate is set to
-- ddl-auto=none, so it never changes the database.
-- Run it once:   mysql -u root -p < database/schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS job_portal
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE job_portal;

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role (role),
    CONSTRAINT chk_users_role CHECK (role IN ('JOB_SEEKER', 'RECRUITER', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS companies (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    industry     VARCHAR(80),
    website      VARCHAR(255),
    location     VARCHAR(120),
    company_size VARCHAR(40),
    is_demo      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_companies_name (name),
    KEY idx_companies_demo (is_demo)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS job_seeker_profiles (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    phone                VARCHAR(30),
    location             VARCHAR(120),
    summary              TEXT,
    skills               TEXT,
    education            TEXT,
    experience           TEXT,
    resume_original_name VARCHAR(255),
    resume_stored_name   VARCHAR(100),
    resume_size          BIGINT,
    resume_uploaded_at   DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_seeker_user (user_id),
    CONSTRAINT fk_seeker_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recruiter_profiles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    company_id  BIGINT,
    phone       VARCHAR(30),
    designation VARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruiter_user (user_id),
    KEY idx_recruiter_company (company_id),
    CONSTRAINT fk_recruiter_user    FOREIGN KEY (user_id)    REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recruiter_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS jobs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    recruiter_id     BIGINT       NOT NULL,
    title            VARCHAR(150) NOT NULL,
    description      TEXT         NOT NULL,
    responsibilities TEXT,
    required_skills  TEXT         NOT NULL,
    qualifications   TEXT,
    location         VARCHAR(120) NOT NULL,
    salary_min       BIGINT,
    salary_max       BIGINT,
    job_type         VARCHAR(20)  NOT NULL,
    experience_level VARCHAR(20)  NOT NULL,
    work_mode        VARCHAR(20)  NOT NULL,
    deadline         DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    is_demo          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_jobs_status_created (status, created_at),
    KEY idx_jobs_recruiter (recruiter_id),
    KEY idx_jobs_company (company_id),
    KEY idx_jobs_location (location),
    KEY idx_jobs_filters (job_type, experience_level, work_mode),
    KEY idx_jobs_title (title),
    KEY idx_jobs_demo (is_demo),
    CONSTRAINT fk_jobs_company   FOREIGN KEY (company_id)   REFERENCES companies (id),
    CONSTRAINT fk_jobs_recruiter FOREIGN KEY (recruiter_id) REFERENCES users (id),
    CONSTRAINT chk_jobs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'REMOVED')),
    CONSTRAINT chk_jobs_salary CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS applications (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    job_id               BIGINT       NOT NULL,
    seeker_id            BIGINT       NOT NULL,
    cover_letter         TEXT,
    resume_original_name VARCHAR(255),
    resume_stored_name   VARCHAR(100),
    status               VARCHAR(20)  NOT NULL DEFAULT 'APPLIED',
    applied_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_job_seeker (job_id, seeker_id),
    KEY idx_applications_seeker (seeker_id),
    KEY idx_applications_status (status),
    CONSTRAINT fk_applications_job    FOREIGN KEY (job_id)    REFERENCES jobs (id),
    CONSTRAINT fk_applications_seeker FOREIGN KEY (seeker_id) REFERENCES users (id),
    CONSTRAINT chk_applications_status CHECK (status IN
        ('APPLIED', 'UNDER_REVIEW', 'SHORTLISTED', 'REJECTED', 'HIRED', 'WITHDRAWN'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS saved_jobs (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id  BIGINT      NOT NULL,
    job_id   BIGINT      NOT NULL,
    saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_saved_user_job (user_id, job_id),
    KEY idx_saved_job (job_id),
    CONSTRAINT fk_saved_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_job  FOREIGN KEY (job_id)  REFERENCES jobs (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS application_notes (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    application_id BIGINT      NOT NULL,
    recruiter_id   BIGINT      NOT NULL,
    note           TEXT        NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notes_application (application_id),
    CONSTRAINT fk_notes_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notes_recruiter   FOREIGN KEY (recruiter_id)   REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS job_reports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    job_id      BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    reason      VARCHAR(100) NOT NULL,
    details     TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_reports_status (status),
    KEY idx_reports_job (job_id),
    CONSTRAINT fk_reports_job      FOREIGN KEY (job_id)      REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT chk_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
) ENGINE=InnoDB;

-- Fulltext index is optional and not used by the app (it searches with LIKE), so it is left out on purpose.

-- Create the application user (change the password!). Then grant access:
-- CREATE USER IF NOT EXISTS 'jobportal_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON job_portal.* TO 'jobportal_user'@'localhost';
-- FLUSH PRIVILEGES;
