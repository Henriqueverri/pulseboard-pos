-- Pedidos e itens do POS (F8).
--
-- So existe `total` (sem desconto nem frete no MVP); o servidor e a autoridade sobre ele.
-- `version` sustenta o locking otimista (@Version) do agregado Order.

-- Numero legivel do pedido ("#512"), independente do id UUID.
CREATE SEQUENCE order_number_seq START WITH 1;

CREATE TABLE orders (
    id             uuid          PRIMARY KEY,
    number         bigint        NOT NULL,
    customer_id    uuid          NOT NULL REFERENCES customers (id),
    status         varchar(16)   NOT NULL,
    currency       varchar(3)    NOT NULL,
    payment_method varchar(16),
    total          numeric(12,2) NOT NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    paid_at        timestamptz,
    canceled_at    timestamptz,
    refunded_at    timestamptz,
    version        integer       NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_number UNIQUE (number),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'PAID', 'CANCELED', 'REFUNDED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('CASH', 'CARD', 'PIX')),
    CONSTRAINT ck_orders_total_non_negative CHECK (total >= 0)
);

CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);

CREATE TABLE order_items (
    id           uuid          PRIMARY KEY,
    order_id     uuid          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   uuid          NOT NULL REFERENCES products (id),
    -- Snapshot: o historico do pedido nao muda se o produto mudar depois.
    sku          varchar(64)   NOT NULL,
    product_name varchar(120)  NOT NULL,
    quantity     integer       NOT NULL,
    unit_price   numeric(12,2) NOT NULL,
    line_total   numeric(12,2) NOT NULL,
    -- SKU unico por transacao no PulseBoard.
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 10000),
    CONSTRAINT ck_order_items_unit_price_positive CHECK (unit_price > 0)
);

CREATE INDEX idx_order_items_product_id ON order_items (product_id);
