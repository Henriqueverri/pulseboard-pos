-- Estoque do POS.
--
-- O saldo so muda por movimentacao: a baixa acontece no pagamento (nao na criacao do pedido) com um
-- UPDATE condicional, e cada mudanca deixa uma linha em stock_movements com o saldo resultante.
-- Produtos sem controle de estoque (track_stock = false) nunca sao bloqueados nem movimentados.
ALTER TABLE products
    ADD COLUMN track_stock    boolean NOT NULL DEFAULT false,
    ADD COLUMN stock_quantity integer NOT NULL DEFAULT 0,
    ADD COLUMN stock_min      integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_products_stock_quantity_non_negative CHECK (stock_quantity >= 0),
    ADD CONSTRAINT ck_products_stock_min_non_negative CHECK (stock_min >= 0);

CREATE TABLE stock_movements (
    id            uuid         PRIMARY KEY,
    -- Ordem estavel do historico, mesmo com created_at igual.
    sequence      bigserial    NOT NULL,
    product_id    uuid         NOT NULL REFERENCES products (id),
    kind          varchar(16)  NOT NULL,
    -- Com sinal: negativo em SALE, positivo em REFUND e RECEIPT, qualquer um em ADJUSTMENT.
    quantity      integer      NOT NULL,
    balance_after integer      NOT NULL,
    order_id      uuid         REFERENCES orders (id),
    reason        varchar(200),
    -- Nulo apenas quando a movimentacao nao veio de uma requisicao autenticada.
    user_id       uuid         REFERENCES users (id),
    created_at    timestamptz  NOT NULL,
    CONSTRAINT uq_stock_movements_sequence UNIQUE (sequence),
    CONSTRAINT ck_stock_movements_kind CHECK (kind IN ('SALE', 'REFUND', 'RECEIPT', 'ADJUSTMENT')),
    CONSTRAINT ck_stock_movements_quantity_non_zero CHECK (quantity <> 0),
    CONSTRAINT ck_stock_movements_balance_non_negative CHECK (balance_after >= 0),
    CONSTRAINT ck_stock_movements_order_kind
        CHECK ((order_id IS NOT NULL) = (kind IN ('SALE', 'REFUND')))
);

CREATE INDEX idx_stock_movements_product_sequence
    ON stock_movements (product_id, sequence DESC);
