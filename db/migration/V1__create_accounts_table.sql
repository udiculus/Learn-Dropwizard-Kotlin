-- ============================================================
-- V1: Create customers and accounts tables
-- Finance Learning Project — Account Service
-- ============================================================

-- ── Customers ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customers (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone        VARCHAR(20),
    status       ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_email UNIQUE (email)
);

CREATE INDEX idx_customers_email  ON customers (email);
CREATE INDEX idx_customers_status ON customers (status);

-- ── Accounts ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT         NOT NULL,
    account_number VARCHAR(20)    NOT NULL,
    account_type   ENUM('SAVINGS', 'CHECKING', 'INVESTMENT') NOT NULL,
    balance        DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency       CHAR(3)        NOT NULL DEFAULT 'USD',
    status         ENUM('ACTIVE', 'INACTIVE', 'CLOSED', 'FROZEN') NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_accounts         PRIMARY KEY (id),
    CONSTRAINT uq_accounts_number  UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_customer_id    ON accounts (customer_id);
CREATE INDEX idx_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_status         ON accounts (status);

-- ── Audit view: customers with their account count ────────────────────────
CREATE OR REPLACE VIEW v_customer_account_summary AS
    SELECT
        c.id              AS customer_id,
        CONCAT(c.first_name, ' ', c.last_name) AS full_name,
        c.email,
        c.status          AS customer_status,
        COUNT(a.id)       AS total_accounts,
        SUM(a.balance)    AS total_balance,
        c.created_at
    FROM customers c
    LEFT JOIN accounts a ON a.customer_id = c.id AND a.status = 'ACTIVE'
    GROUP BY c.id, c.first_name, c.last_name, c.email, c.status, c.created_at;
