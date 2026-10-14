-- Saldo de estoque espelhado no PulseBoard (fase 3 do plano).
--
-- Eventos STOCK_CHANGED pertencem ao produto (aggregate_type PRODUCT, aggregate_id = id do
-- produto). Cada um leva o saldo absoluto e o instante da mudanca; o PulseBoard fica com o mais
-- recente, entao eles nao esperam os anteriores do mesmo produto como os eventos de pedido.
ALTER TABLE outbox_events
    DROP CONSTRAINT ck_outbox_events_aggregate_type,
    DROP CONSTRAINT ck_outbox_events_event_type,
    ADD CONSTRAINT ck_outbox_events_aggregate_type CHECK (aggregate_type IN ('ORDER', 'PRODUCT')),
    ADD CONSTRAINT ck_outbox_events_event_type
        CHECK (event_type IN ('ORDER_PAID', 'ORDER_REFUNDED', 'STOCK_CHANGED')),
    ADD CONSTRAINT ck_outbox_events_aggregate_matches_type
        CHECK ((aggregate_type = 'PRODUCT') = (event_type = 'STOCK_CHANGED'));
