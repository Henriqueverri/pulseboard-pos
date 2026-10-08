-- Retry, backoff e falhas classificadas da outbox (F12).
--
-- next_attempt_at: quando um evento PENDING pode ser reivindicado (backoff, Retry-After).
-- failure_kind: por que um evento esta FAILED (so existe em FAILED).
-- attempts_at_retry: valor de attempts no ultimo reprocessamento manual. attempts guarda o historico
-- inteiro; o limite de tentativas e o backoff contam a partir daqui (attempts - attempts_at_retry).
ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at   timestamptz,
    ADD COLUMN failure_kind      varchar(16),
    ADD COLUMN attempts_at_retry integer NOT NULL DEFAULT 0;

-- Na F11 qualquer resposta diferente de 200/201 virava FAILED provisorio, sem classificacao. Esses
-- eventos voltam para a fila e o classificador decide: o reenvio e seguro (mesmo payload, mesmo
-- X-Request-Id, idempotencia do PulseBoard) e um erro permanente volta a ser FAILED/PERMANENT.
UPDATE outbox_events
SET status = 'PENDING',
    attempts_at_retry = attempts,
    updated_at = now()
WHERE status = 'FAILED';

UPDATE outbox_events SET next_attempt_at = created_at;

ALTER TABLE outbox_events
    ALTER COLUMN next_attempt_at SET NOT NULL,
    ADD CONSTRAINT ck_outbox_events_failure_kind
        CHECK (failure_kind IN ('PERMANENT', 'EXHAUSTED', 'CONFIGURATION')),
    ADD CONSTRAINT ck_outbox_events_failed_has_kind
        CHECK ((status = 'FAILED') = (failure_kind IS NOT NULL)),
    ADD CONSTRAINT ck_outbox_events_attempts_at_retry
        CHECK (attempts_at_retry BETWEEN 0 AND attempts);

CREATE INDEX idx_outbox_events_status_next_attempt ON outbox_events (status, next_attempt_at);
