-- Author: gustavo
-- Date:   2026-04-17
-- Description: Cria tabela de usuários do auth-service.

CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_users_email' AND table_name = 'users'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_users_role' AND table_name = 'users'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
    END IF;
END $$;

COMMENT ON TABLE  users               IS 'Usuários cadastrados na plataforma SpendWise.';
COMMENT ON COLUMN users.is_active     IS 'FALSE após desativação da conta.';
COMMENT ON COLUMN users.role          IS 'Perfil de acesso: USER (padrão) ou ADMIN.';
COMMENT ON COLUMN users.password_hash IS 'Hash BCrypt da senha — nunca texto puro.';
