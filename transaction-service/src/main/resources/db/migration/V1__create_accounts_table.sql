-- Author: gustavo
-- Date:   2026-04-21
-- Description: Cria tabela de contas pré-registradas do transaction-service.
--              Placeholder para futura integração com Open Finance (ex: Belvo, Pluggy).

CREATE TABLE IF NOT EXISTS account (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS account_operation_type (
    account_id     UUID        NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    CONSTRAINT pk_account_operation_type PRIMARY KEY (account_id, operation_type)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_account_op_type_account' AND table_name = 'account_operation_type'
    ) THEN
        ALTER TABLE account_operation_type
            ADD CONSTRAINT fk_account_op_type_account
            FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'idx_account_user_id'
    ) THEN
        CREATE INDEX idx_account_user_id ON account(user_id);
    END IF;
END $$;

COMMENT ON TABLE  account            IS 'Contas pré-registradas do usuário. Substituição futura: Open Finance via external_account_id + provider.';
COMMENT ON COLUMN account.user_id   IS 'ID do usuário dono da conta — nunca vem do body, sempre do JWT.';
COMMENT ON COLUMN account.name      IS 'Nome amigável da conta (ex: Nubank, Itaú Corrente).';
