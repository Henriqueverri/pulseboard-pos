# Checklist de contrato POS ↔ PulseBoard

Conferência do que o POS envia e de como ele trata cada resposta, contra o contrato público de ingestão do PulseBoard (`docs/integration.md` do repositório `pulseboard`). Detalhes de implementação em [`integration.md`](integration.md).

**Como foi verificado:**

- **Teste**: testes automatizados do backend contra um PulseBoard simulado (WireMock) ou unitários. Rodam em toda CI (`./mvnw verify`).
- **Real**: smoke test de 2026-10-08 com o ambiente completo em Docker Compose (web → api → db) entregando a um PulseBoard local real (`php artisan serve`, organização de demo com os 40 SKUs, API Key temporária de 24 h revogada ao final). Venda paga e estornada pelo proxy do Nginx; a transação foi conferida com `GET /ingest/transactions/{external_id}` no PulseBoard, e o log do PulseBoard trouxe o mesmo `request_id`. Também foi verificado o cenário PulseBoard fora do ar → `NETWORK` → nova tentativa → entregue quando ele voltou.

Nenhuma divergência que exigisse mudança no POS foi encontrada. O PulseBoard não foi alterado.

## Requisição

| Item | Contrato | POS | Status | Evidência |
|---|---|---|---|---|
| Autenticação | `Authorization: Bearer <API Key>` da organização | `Bearer ${PULSEBOARD_API_KEY}` em toda chamada; a chave nunca é logada nem exibida (só o prefixo) | ✅ | Teste `OutboxDeliveryTest.sendsTheStoredSnapshotWithTheContractHeaders`, `OutboxObservabilityTest.theApiKeyNeverAppearsInTheLogs`; Real |
| `X-Organization-Id` | não existe na integração; a chave define a organização | não é enviado | ✅ | Teste (`containsHeader("X-Organization-Id")` falso) |
| `X-Request-Id` | opcional; mantido se tiver 8–64 caracteres `A-Za-z0-9._-` | `pos-<uuid do evento>` (40 caracteres), igual em todas as tentativas e na reconciliação | ✅ | Teste (regex e tamanho); Real (devolvido idêntico e presente no log do PulseBoard) |
| `Content-Type` / `Accept` | `application/json` | idem | ✅ | Teste |
| `external_id` | até 128 caracteres em `A-Z a-z 0-9 . _ : -`, único por organização | `pos:<uuid do pedido>` (40 caracteres) | ✅ | Teste `IngestPayloadFactoryTest.externalIdsArePrefixedUuids`; Real |
| `external_id` no path | segmento de path em `/status-changes` e no `GET` | URI template sem reescapar o `:` | ✅ | Teste `deliversARefundAsAStatusChangeOfTheExternalId` (URL exata); Real |
| `status` na criação | `pending` ou `paid` | sempre `paid` (só pedidos pagos são enviados) | ✅ | Teste `orderPaidIsTheExactIngestionBody` |
| `occurred_at` | ISO 8601 com fuso; até 5 min no futuro | `paid_at` / `refunded_at` em UTC com `Z`, em segundos | ✅ | Teste; Real |
| `currency` | 3 letras, igual à da organização | `POS_CURRENCY` (`BRL`) | ✅ | Real (organização de demo em BRL) |
| SKU | `items[].sku` existente no catálogo da organização; cada SKU uma vez | SKU do catálogo do POS (seed com os 40 SKUs da demo); `UNIQUE(order_id, product_id)` | ✅ | Teste `RepositoryIntegrationTest` (seed dos 40 SKUs); Real (40/40 SKUs presentes na organização de demo) |
| Quantidade / itens | 1–10.000; 1–100 itens | mesmos limites validados no POS (`@Size(max = 100)`, `@Min(1) @Max(10_000)`, `Order.MAX_ITEMS`) | ✅ | Teste `OrderTest` (10.001 rejeitado), `OrderApiTest` (lista vazia e quantidade inválida → 400) |
| `customer.external_id` | mesmo formato do `external_id` | `pos:cus:<uuid do cliente>` | ✅ | Teste; Real (`data.customer.external_id` conferido) |
| `customer.name` / `email` | obrigatórios; campos extras em `customer` rejeitados | só `external_id`, `name`, `email` (e-mail obrigatório no POS); `document` nunca sai | ✅ | Teste `orderPaidIsTheExactIngestionBody` (JSON exato) |
| Valores monetários | string decimal com até 2 casas; número JSON → 422 | `BigDecimal` com escala 2 serializado como string (`"129.90"`) | ✅ | Teste `keepsWholeUnitsWithTwoDecimals`, `MoneyTest`; Real (`total_amount` igual nos dois lados) |
| `total_amount` | opcional; se enviado, igual à soma | enviado como conferência | ✅ | Teste; Real |
| Valor máximo | até `9999999999.99` | `NUMERIC(12,2)`; total acima é rejeitado no POS (`order_total_too_large`) | ✅ | Teste `OrderApiTest` |
| Mudança de status | `POST …/status-changes` com `{status, occurred_at}`; `paid → refunded` | `{"status":"refunded","occurred_at":…}`, sempre depois do `ORDER_PAID` do mesmo pedido estar `SENT` | ✅ | Teste `orderRefundedIsTheExactStatusChangeBody`, `OutboxClaimTest.eventsOfAnOrderAreClaimedInSequence`; Real (`status_history` = `paid → refunded`) |

## Respostas

| Resposta | Contrato | POS | Status | Evidência |
|---|---|---|---|---|
| `201` | criada | `SENT` + `remote_id` | ✅ | Teste `createdIsSent`, `deliversAPaidOrderAndMarksItSent`; Real |
| `200` + `Idempotent-Replayed: true` | reenvio idêntico (criação ou status já aplicado) | `SENT` | ✅ | Teste `idempotentReplayIsSent`, `aRedeliveryAfterALostResultIsAnIdempotentReplay` |
| Idempotência | `external_id` + fingerprint do conteúdo | payload snapshot imutável: todo reenvio é idêntico; entrega at-least-once | ✅ | Teste `aTimeoutThenAReplayIsSentOnce` (uma única criação no simulador) |
| `401 invalid_api_key` | não repetir | `FAILED` / `CONFIGURATION` + pausa de 15 min; no máximo 1 requisição por pausa | ✅ | Teste `invalidApiKeyIsAConfigurationFailure`, `anInvalidKeyPausesTheIntegrationWithoutALoop` |
| `409 transaction_conflict` | não repetir | `FAILED` / `PERMANENT` | ✅ | Teste `contentRejectionsArePermanent` |
| `409 customer_email_conflict` | vincular o cliente pelo ID externo e reenviar | `FAILED` / `PERMANENT` com essa instrução; reprocessamento manual | ✅ | Teste `customerEmailConflictTellsWhatToDo` |
| `409 invalid_transition` | não repetir; conferir com `GET` | estorno: reconciliação por `GET` (já `refunded` → `SENT`; senão `FAILED`); venda: `FAILED` | ✅ | Teste `invalidTransitionOfARefundIsReconciled`, `aRefundAlreadyRecordedIsReconciledAsSent`, `aRefundThatDivergesFromPulseBoardFailsPermanently` |
| `404 not_found` | `external_id` inexistente | `FAILED` / `PERMANENT` (não deveria ocorrer por causa da ordem por pedido) | ✅ | Teste `unknownTransactionOfARefundIsPermanent` |
| `413` | corpo acima de 2 MB | `FAILED` / `PERMANENT` | ✅ | Teste `payloadTooLargeIsPermanent` |
| `422` (`validation_failed`, `currency_mismatch`, `total_mismatch`) | corrigir o payload | `FAILED` / `PERMANENT` com `code`, `message` e erros por campo | ✅ | Teste `validationFailureIsPermanentWithTheFieldErrors`, `anUnknownSkuIsAPermanentFailureWithPulseBoardsMessage` |
| `429 rate_limited` | esperar `Retry-After` | `PENDING` até `now + Retry-After` (segundos ou data HTTP; 60 s se ausente) | ✅ | Teste `rateLimitedWaitsForRetryAfter`, `rateLimitedAcceptsAnHttpDate`, `rateLimitingWithoutRetryAfterWaitsSixtySeconds` |
| `5xx` | repetir o mesmo payload | `PENDING` com backoff exponencial + jitter; `FAILED` / `EXHAUSTED` após 10 tentativas | ✅ | Teste `serverErrorsBackOffUntilExhausted` |
| Timeout | transitório; reconciliar ou reenviar o mesmo payload | `PENDING` com backoff (conexão 10 s, resposta 30 s); o reenvio idêntico vira replay | ✅ | Teste `aTimeoutIsRetriedWithBackoff`, `aTimeoutThenAReplayIsSentOnce` |
| Falha de rede | transitório | `PENDING` com backoff (`NETWORK`) | ✅ | Teste `aNetworkFailureIsRetriedWithBackoff`; Real (PulseBoard parado → entregue na 3ª tentativa depois de religado) |
| `X-Request-Id` da resposta | sempre presente | gravado em `last_request_id` e logado como `response_request_id` | ✅ | Teste; Real |

## Observações (sem mudança de código)

- **Limite de 120 req/min por chave.** O worker envia até 20 eventos por ciclo, um de cada vez. Com um backlog grande (por exemplo, depois de horas com o PulseBoard fora), parte do lote pode receber `429`. Cada evento respeita o próprio `Retry-After`, mas o restante do lote ainda é tentado, e cada `429` conta como tentativa. O backlog converge sem perda, mas com tentativas extras. Uma pausa global por `429` seria a melhoria natural, fora do escopo atual.
- **`occurred_at` até 5 min no futuro.** O POS usa o próprio relógio. Um relógio adiantado mais de 5 minutos resultaria em `422`. Em Docker o relógio é o do host.
- **Moeda.** Trocar `POS_CURRENCY` afeta apenas pedidos novos; eventos já gravados mantêm o snapshot (por design, para não mudar o fingerprint).
