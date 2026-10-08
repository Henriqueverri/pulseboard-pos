-- Catalogo e clientes do POS (F8).
--
-- Dinheiro em NUMERIC(12,2): nunca ponto flutuante. Timestamps em timestamptz (UTC).

CREATE TABLE products (
    id         uuid          PRIMARY KEY,
    -- Obrigatorio e unico: a ingestao do PulseBoard casa o produto pelo SKU (limite de 64 la).
    sku        varchar(64)   NOT NULL,
    name       varchar(120)  NOT NULL,
    price      numeric(12,2) NOT NULL,
    active     boolean       NOT NULL DEFAULT true,
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

CREATE TABLE customers (
    id         uuid         PRIMARY KEY,
    name       varchar(120) NOT NULL,
    -- Obrigatorio (o PulseBoard exige e-mail para cliente novo), unico e sempre em minusculas.
    email      varchar(254) NOT NULL,
    -- Opcional e interno ao POS: nunca e enviado ao PulseBoard.
    document   varchar(20),
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_email_lowercase CHECK (email = lower(email))
);
