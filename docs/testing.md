# Testes

Como rodar e o que cada suíte protege. Quickstart no [README](../README.md#testes).

## Backend

```bash
cd backend
./mvnw -B spotless:check verify     # formatação + testes unitários e de integração (o mesmo da CI)
./mvnw test                         # só os testes
./mvnw spotless:apply               # formata
```

Os testes de integração usam **Testcontainers**: sobem um `postgres:16-alpine` descartável (o Docker precisa estar rodando; o Compose não é necessário), aplicam o Flyway do zero e nunca usam H2. O PulseBoard é simulado com **WireMock**. Os testes rodam o worker explicitamente (`OutboxWorker.runOnce()`), sem agendamento, e usam um relógio ajustável (`MutableClock`) para pular backoffs e pausas em vez de dormir.

| Suíte | Cobre |
|---|---|
| `OrderStatusTest`, `OrderTest` | matriz completa de transições, totais, snapshots, timestamps em segundos |
| `MoneyTest` | escala, ausência de arredondamento silencioso, string no JSON, número JSON rejeitado |
| `OrderApiTest` | fluxo criar → pagar → estornar, cancelar, 409 em transições inválidas, 404, 422, validação, paginação e filtros, totais do cliente ignorados |
| `ProductApiTest`, `CustomerApiTest` | CRUD, unicidade de SKU/e-mail (409), validação, busca e paginação |
| `OrderOptimisticLockingTest` | duas transações concorrentes no mesmo pedido: a segunda falha por `@Version` |
| `RepositoryIntegrationTest` | seed dos 40 SKUs, constraints e índices únicos, sequência do número do pedido |
| `OpenApiDocsTest` | OpenAPI com as rotas reais, dinheiro como `string` e esquema Bearer |
| `ApiExceptionHandlerTest` | ProblemDetail com `code`, sem detalhes internos |
| `AuthApiTest` | login (válido, inválido, indistinguível), claims e validade do token, `/auth/me`, 401 sem token / malformado / expirado / assinatura inválida, rotas públicas |
| `AuthorizationMatrixTest` | matriz ADMIN/CASHIER (403) e 401 em todas as rotas da API |
| `SecurityPropertiesTest`, `UserSeederTest` | startup falha com segredo fraco; seed idempotente com BCrypt |
| `PosApplicationIntegrationTest` | PostgreSQL 16, migrations aplicadas, `/actuator/health` `UP` |
| `IngestPayloadFactoryTest` | JSON exato de `ORDER_PAID` e `ORDER_REFUNDED` (dinheiro em string, UTC em segundos, sem `document`) |
| `OutboxTransactionTest` | evento gravado na transação de `pay`/`refund`; falha real do banco ao gravar o evento desfaz o pagamento; cancelamento não gera evento |
| `OutboxDeliveryTest` | 201 → `SENT`, headers e corpo exatos, reenvio após resultado perdido vira replay idempotente, worker atrasado não sobrescreve o resultado, estorno por `status-changes`, 422 → `FAILED` / `PERMANENT`, timeout e falha de rede → nova tentativa |
| `OutboxResilienceTest` | esgotamento após 10 tentativas, `429` com e sem `Retry-After`, `401` → pausa → reprocessar falhas de configuração, reconciliação do `409 invalid_transition`, reprocessamento manual |
| `OutboxClaimTest` | PostgreSQL real: ordem por pedido e `blockedBy`, backoff respeitado, lease expirado reivindicável, `SKIP LOCKED`, dois workers concorrentes sem envio duplicado |
| `OutboxObservabilityTest` | cada linha de uma tentativa (claim → chamada HTTP → resultado) carrega `request_id = pos-<event id>` e `event_id`; campos de retry, falha permanente e eventos bloqueados; retomada após a pausa; **a API Key nunca aparece nos logs** (appender em memória, incluindo o caminho do 401) |
| `RequestIdFilterTest`, `RequestIdApiTest` | `X-Request-Id` recebido é reutilizado, ausente ou malformado é gerado; presente em 401; logs da requisição carregam o id; MDC limpo depois (inclusive em erro) |
| `PulseBoardHealthIndicatorTest`, `IntegrationHealthActuatorTest` | health da integração desligada, sem chave, chave malformada, ativa e pausada; sem segredo; anônimo e CASHIER só veem o status, ADMIN vê o componente `pulseBoard` |
| `IntegrationApiTest` | endpoints de integração: lista, filtro, detalhe com payload, retry (404/409), health sem segredo, eventos no detalhe do pedido |
| `IngestResponseClassifierTest`, `RetryPolicyTest`, `IntegrationPauseTest` | tabela de classificação completa, fórmula do backoff com jitter e teto, `Retry-After`, pausa de 15 min |
| `OutboxWorkerDisabledTest`, `PulseBoardPropertiesTest` | integração desligada, sem chave ou pausada não reivindica nem envia nada; prefixo público da chave; a chave nunca aparece no `toString` |

## Frontend

```bash
cd frontend
npm ci
npm run lint && npm run typecheck && npm test && npm run build
```

Vitest com jsdom; as telas usam as rotas e os providers reais, e a API é simulada com **MSW** (um banco em memória com as mesmas regras de autenticação e papéis do backend).

| Suíte | Cobre |
|---|---|
| `cartReducer.test.ts` | adicionar, somar quantidade, limites de quantidade e de itens, remover, cliente, limpar, totais em centavos |
| `money.test.ts` | conversão string ↔ centavos sem ponto flutuante, formatação pt-BR |
| `schemas.test.ts` | schemas Zod de checkout, produto, cliente e login |
| `client.test.ts` | header Bearer, erro a partir do ProblemDetail, 401 limpa a sessão, 401 do login não, falha de rede |
| `CheckoutPage.test.tsx` | venda completa (corpo das requisições conferido), validação, erro 422 da API, pedido criado sem pagamento, cliente criado no caixa |
| `auth.test.tsx` | anônimo → login, token em `sessionStorage` (nunca `localStorage`), credencial inválida, 401 → login com aviso, logout |
| `authorization.test.tsx` | CASHIER sem menu/tela de Produtos e sem Estornar; ADMIN com acesso e estorno |
| `orders.test.tsx` | filtros na URL, pagar, cancelar, conflito 409, 403 → acesso negado |
| `ProductsPage.test.tsx` | criação com preço em string decimal, `duplicate_sku` no campo, erros de validação do servidor |
| `integration.test.tsx` | card de saúde, lista com "bloqueado por" e tipo de falha, reprocessar (sucesso e 409), painel com payload e request ids, banner de pausa, integração desligada, filtro na URL, polling, CASHIER sem acesso, status de envio no detalhe do pedido |

## Docker

```bash
docker compose config            # interpolação do .env (falha sem POS_JWT_SECRET / POS_ADMIN_*)
docker compose build
docker compose up -d --wait      # espera db, api e web ficarem healthy
docker compose down
```

A CI roda o hadolint nos dois Dockerfiles e o build das imagens (job `docker`). O roteiro manual de ponta a ponta com um PulseBoard real está em [`demo.md`](demo.md).
