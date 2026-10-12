# Deploy

Como o POS roda em produção, entregando as vendas à organização demo do PulseBoard. Visão geral no [README](../README.md).

## Topologia

```text
Render Web Service (Free, Docker)      pos.henriqueverri.dev
  Nginx: SPA + proxy de /api
        │  HTTPS, Host do upstream (o Render roteia pelo Host)
        ▼
Render Web Service (Free, Docker)      pulseboard-pos-api.onrender.com
  Spring Boot: API + OutboxWorker
        │  Supabase Shared Pooler, Session mode (5432), TLS
        ▼
Supabase PostgreSQL (projeto próprio, não o do PulseBoard)

OutboxWorker ──HTTPS + API Key──▶ api.henriqueverri.dev (organização pulseboard-demo)
```

| Peça | Como publica |
|------|--------------|
| API e Web | Blueprint do Render ([`render.yaml`](../render.yaml)): deploy depois que os checks da CI passam, cada serviço só quando a sua pasta muda |
| Banco | Supabase gerenciado; o Flyway aplica as migrations no start da API |

No plano Free os serviços não se alcançam pela rede privada, então o Nginx faz proxy para a URL pública da API. O navegador continua falando com uma única origem (sem CORS, o JWT não vai a outro host). O upstream é resolvido a cada 30 s (`resolver` com os DNS do container), não uma vez no start.

## Hibernação

O Render Free hiberna cada serviço após ~15 min sem requisições. O primeiro acesso acorda a Web na hora e a API em 1 a 2 minutos (JVM com uma fração de CPU); até lá, `/api` responde 502 e a tela de login mostra o erro. Abra o POS alguns minutos antes de uma apresentação.

Enquanto a API dorme, o worker também dorme: nada se perde, porque os eventos estão na outbox e são entregues quando ela acorda. Do lado do PulseBoard, a API também pode estar hibernando; a primeira entrega espera o cold start dele ou cai no retry com backoff.

## Banco no Supabase

Crie um **projeto separado** (o plano Free permite dois) e use o **Shared Pooler em Session mode**, porta **5432**:

| Variável | Valor |
|----------|-------|
| `POS_DB_URL` | `jdbc:postgresql://aws-0-<região>.pooler.supabase.com:5432/postgres?sslmode=require` |
| `POS_DB_USER` | `postgres.<project-ref>` |
| `POS_DB_PASSWORD` | a senha do banco (sem URL-encoding: não vai na URL) |

- Não use a conexão direta (`db.<ref>.supabase.co`): é só IPv6 e o Render não tem saída IPv6.
- Não use o Transaction mode (6543): o driver JDBC usa prepared statements no servidor.
- O pool do Hikari fica em 3 conexões (`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`), compartilhadas pela API e pelo worker.

## Integração com o PulseBoard

O POS entrega na organização demo do PulseBoard usando a **chave gerenciada** dela: não expira, visitantes não conseguem revogá-la e a API do PulseBoard a restaura a cada start ([detalhes](https://github.com/Henriqueverri/pulseboard/blob/main/docs/deployment.md#dados-de-demonstração)).

1. Gere uma chave no formato do PulseBoard:
   ```bash
   echo "pb_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 12)_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 40)"
   ```
2. No Render do PulseBoard, coloque o valor em `DEMO_INTEGRATION_KEY` e deixe o serviço reiniciar.
3. No Render do POS, coloque **o mesmo valor** em `PULSEBOARD_API_KEY`.

Para rotacionar, repita os três passos: o PulseBoard revoga a chave gerenciada anterior no start seguinte.

## Primeiro deploy

1. Supabase: novo projeto e connection string do Session pooler.
2. Render: *New → Blueprint* apontando para este repositório. Preencha `POS_DB_URL`, `POS_DB_USER`, `POS_DB_PASSWORD`, `POS_ADMIN_PASSWORD`, `POS_CASHIER_PASSWORD` e `PULSEBOARD_API_KEY`. `POS_JWT_SECRET` é gerado pelo Render.
3. Confira a URL do serviço `pulseboard-pos-api`. Se o Render acrescentou um sufixo ao nome, ajuste `POS_API_UPSTREAM` no serviço `pulseboard-pos-web`.
4. Domínio: em `pulseboard-pos-web` → *Settings → Custom Domains*, adicione `pos.henriqueverri.dev` e crie o `CNAME` indicado no DNS (no Cloudflare, como *DNS only* até o certificado ser emitido).
5. Verifique `https://pulseboard-pos-api.onrender.com/actuator/health` → `{"status":"UP"}` (o health fica fora de `/api/`, então não passa pelo proxy da Web). Depois, entre como caixa, faça uma venda e procure `pos:<uuid>` em Transações na demo do PulseBoard.

## Limitações

- Hibernação do plano Free (acima).
- Contas públicas: qualquer visitante com a conta ADMIN pode estornar vendas e reprocessar eventos. Os dados são de demonstração; para zerar, limpe `outbox_events`, `order_items`, `orders` e `customers` pelo SQL Editor do Supabase (o catálogo fica intacto e os usuários são sincronizados a cada start).
- Uma única instância: a pausa por 401 fica em memória, o que basta com uma réplica.
