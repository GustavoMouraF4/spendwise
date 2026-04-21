-- Author: gustavo
-- Date:   2026-04-21
-- Description: Cria tabela de transações financeiras do transaction-service.

CREATE TABLE IF NOT EXISTS transaction (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL,
    account_id       UUID           NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    operation_type   VARCHAR(20)    NOT NULL,
    category         VARCHAR(100)   NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    transaction_date DATE           NOT NULL,
    tags             VARCHAR(100),
    is_recurring     BOOLEAN        NOT NULL DEFAULT FALSE,
    recurrence_type  VARCHAR(20),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_transaction_account' AND table_name = 'transaction'
    ) THEN
        ALTER TABLE transaction
            ADD CONSTRAINT fk_transaction_account
            FOREIGN KEY (account_id) REFERENCES account(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_transaction_amount_positive' AND table_name = 'transaction'
    ) THEN
        ALTER TABLE transaction
            ADD CONSTRAINT ck_transaction_amount_positive CHECK (amount > 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_transaction_type' AND table_name = 'transaction'
    ) THEN
        ALTER TABLE transaction
            ADD CONSTRAINT ck_transaction_type CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_transaction_operation_type' AND table_name = 'transaction'
    ) THEN
        ALTER TABLE transaction
            ADD CONSTRAINT ck_transaction_operation_type
            CHECK (operation_type IN ('PIX', 'DEBIT', 'CREDIT', 'CASH', 'TED', 'DOC', 'BANK_SLIP', 'OTHER'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transaction_user_id') THEN
        CREATE INDEX idx_transaction_user_id ON transaction(user_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_transaction_date') THEN
        CREATE INDEX idx_transaction_date ON transaction(transaction_date);
    END IF;
END $$;

COMMENT ON TABLE  transaction                 IS 'Lançamentos financeiros do usuário: receitas, gastos e transferências.';
COMMENT ON COLUMN transaction.user_id         IS 'ID do usuário — extraído do JWT, nunca do body (RN-TRX-06).';
COMMENT ON COLUMN transaction.amount          IS 'Valor sempre positivo (RN-TRX-02). O campo type define se é entrada ou saída.';
COMMENT ON COLUMN transaction.description     IS 'Motivo livre do gasto — usado pelo chat-service para estratégias financeiras.';
COMMENT ON COLUMN transaction.transaction_date IS 'Data do lançamento — não pode ser futura (RN-TRX-03).';
COMMENT ON COLUMN transaction.is_recurring    IS 'TRUE exige recurrence_type preenchido (RN-TRX-04).';
