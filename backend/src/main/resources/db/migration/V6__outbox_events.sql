-- Transactional outbox da integracao com o PulseBoard (F11).
--
-- Cada linha e a intencao de enviar um evento de pedido, gravada na mesma transacao que muda o
-- pedido. `payload` e o snapshot exato do corpo a enviar: todo reenvio manda o mesmo conteudo,
-- o que mantem o fingerprint de idempotencia do PulseBoard estavel.
CREATE TABLE outbox_events (
    id              uuid         PRIMARY KEY,
    -- Ordem global estavel (desempate que created_at nao garante).
    sequence        bigserial    NOT NULL,
    aggregate_type  varchar(32)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    event_type      varchar(32)  NOT NULL,
    payload         jsonb        NOT NULL,
    status          varchar(16)  NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    -- Lease do worker: um evento PROCESSING com lease vencido volta a ser elegivel.
    locked_until    timestamptz,
    last_attempt_at timestamptz,
    last_http_status integer,
    last_error_code varchar(64),
    last_error      text,
    last_request_id varchar(64),
    remote_id       uuid,
    processed_at    timestamptz,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT uq_outbox_events_sequence UNIQUE (sequence),
    CONSTRAINT ck_outbox_events_aggregate_type CHECK (aggregate_type IN ('ORDER')),
    CONSTRAINT ck_outbox_events_event_type CHECK (event_type IN ('ORDER_PAID', 'ORDER_REFUNDED')),
    CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_status_sequence ON outbox_events (status, sequence);
CREATE INDEX idx_outbox_events_aggregate_sequence ON outbox_events (aggregate_id, sequence);
