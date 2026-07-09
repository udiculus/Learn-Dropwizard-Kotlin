-- ============================================================
-- V2: Create transactions table
-- Finance Learning Project — Transaction Service
-- ============================================================

-- ── Transactions ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    transaction_ref   VARCHAR(36)    NOT NULL,             -- UUID
    source_account_id BIGINT         NOT NULL,
    target_account_id BIGINT,                              -- NULL for deposits/withdrawals
    type              ENUM('DEPOSIT', 'WITHDRAWAL', 'TRANSFER') NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    status            ENUM('PENDING', 'COMPLETED', 'FAILED', 'REVERSED') NOT NULL DEFAULT 'PENDING',
    description       VARCHAR(500),
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_transactions       PRIMARY KEY (id),
    CONSTRAINT uq_transaction_ref    UNIQUE (transaction_ref),
    CONSTRAINT fk_txn_source_account
        FOREIGN KEY (source_account_id) REFERENCES accounts(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_txn_target_account
        FOREIGN KEY (target_account_id) REFERENCES accounts(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_txn_source_account ON transactions (source_account_id);
CREATE INDEX idx_txn_target_account ON transactions (target_account_id);
CREATE INDEX idx_txn_created_at     ON transactions (created_at);
CREATE INDEX idx_txn_status         ON transactions (status);
CREATE INDEX idx_txn_type           ON transactions (type);

-- ── View: transaction summary per account ─────────────────────────────────
CREATE OR REPLACE VIEW v_account_transaction_summary AS
    SELECT
        a.account_number,
        t.type,
        COUNT(t.id)       AS transaction_count,
        SUM(t.amount)     AS total_amount,
        MAX(t.created_at) AS last_transaction_at
    FROM accounts a
    INNER JOIN transactions t ON t.source_account_id = a.id
    WHERE t.status = 'COMPLETED'
    GROUP BY a.account_number, t.type;
