-- Usuarios do POS (F9).
--
-- Sem cadastro aberto: as linhas sao criadas/sincronizadas no startup a partir das variaveis
-- POS_ADMIN_* e POS_CASHIER_* (UserSeeder). A senha e guardada apenas como hash BCrypt.

CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    name          varchar(120) NOT NULL,
    role          varchar(16)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CASHIER'))
);
