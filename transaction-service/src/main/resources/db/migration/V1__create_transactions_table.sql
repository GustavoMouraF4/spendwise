CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    amount           NUMERIC(15,2) NOT NULL,
    type             VARCHAR(20) NOT NULL,
    category         VARCHAR(100) NOT NULL,
    description      VARCHAR(255) NOT NULL,
    transaction_date DATE        NOT NULL,
    tags             VARCHAR(50),
    recurring        BOOLEAN     NOT NULL DEFAULT FALSE,
    recurrence_type  VARCHAR(20),
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_id      ON transactions(user_id);
CREATE INDEX idx_transactions_user_date    ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_transactions_user_type    ON transactions(user_id, type);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category);
