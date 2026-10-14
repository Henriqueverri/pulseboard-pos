# ADR 0001: baixa de estoque no pagamento, com UPDATE condicional

- **Status:** aceita
- **Data:** 2026-10-13

## Contexto

O POS passou a controlar estoque de produtos revendidos (cervejas, bebidas sem álcool). Drinks, porções e o bilhar não têm ficha técnica, então ficam sem controle (`track_stock = false`) e nunca são bloqueados.

O fluxo de venda já tem dois passos: `POST /api/orders` cria o pedido `PENDING` e `POST /api/orders/{id}/pay` o paga, gravando o evento `ORDER_PAID` na outbox na mesma transação. Era preciso decidir **quando** o saldo cai e **como** impedir que duas vendas simultâneas levem a mesma última unidade.

## Decisão

**1. A baixa acontece no pagamento, não na criação do pedido.**

`InventoryService.sell` roda dentro da transação de `OrderService.pay`, junto com a mudança de status e o evento da outbox. Sem saldo, lança `InsufficientStockException` (`409 insufficient_stock`, com os SKUs e o saldo restante em `errors`) e a transação inteira é desfeita: o pedido continua `PENDING`, nenhuma linha baixada antes fica baixada e nada vai para a outbox, então o PulseBoard nunca recebe uma venda que não aconteceu.

- Cancelar um pedido `PENDING` não mexe no estoque, porque ele nunca reservou nada.
- O estorno devolve exatamente o que a venda baixou, lido dos movimentos `SALE` do pedido. Um produto que passou a controlar estoque depois da venda não ganha unidades que nunca perdeu.

**2. A baixa é um único `UPDATE` condicional, sem lock em Java nem `SELECT ... FOR UPDATE` prévio.**

```sql
UPDATE products
SET stock_quantity = stock_quantity + :delta
WHERE id = :productId AND track_stock AND stock_quantity + :delta >= 0
RETURNING stock_quantity
```

O PostgreSQL trava a linha até o fim da transação. Quem chega depois espera e, ao ganhar a linha, reavalia o `WHERE` contra o saldo já confirmado: se não sobrou, nenhuma linha é afetada e o serviço sabe que faltou saldo. O `RETURNING` traz o saldo resultante, gravado no movimento. A constraint `CHECK (stock_quantity >= 0)` é a última barreira.

**3. As linhas são baixadas sempre em ordem de `product_id`.** Dois pedidos com os mesmos produtos, em qualquer ordem no carrinho, travam as linhas na mesma sequência e não podem entrar em deadlock.

**4. O saldo só muda por movimentação.** Cada mudança grava uma linha em `stock_movements` (`SALE`, `REFUND`, `RECEIPT`, `ADJUSTMENT`) com a quantidade com sinal, o saldo resultante, o pedido, o motivo e o usuário. A coluna `stock_quantity` é somente leitura para o JPA (`insertable = false, updatable = false`): salvar um produto editado nunca sobrescreve uma venda confirmada no meio tempo.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Reservar na criação do pedido | Pedidos `PENDING` abandonados prenderiam estoque; seria preciso expiração de reserva e um job para liberar. No caixa, criar e pagar acontecem em segundos. |
| `SELECT ... FOR UPDATE` e depois `UPDATE` | Duas idas ao banco e a mesma garantia; o `UPDATE` condicional já trava e confere numa operação só. |
| `@Version` no produto (lock otimista) | Toda venda do mesmo produto disputaria a versão, inclusive vendas com saldo de sobra, e cada conflito viraria um retry. O conflito real é só "não sobrou saldo". |
| Saldo calculado pela soma dos movimentos | Uma agregação a cada venda e a cada listagem, e a concorrência continuaria precisando de um ponto de lock. O saldo na linha do produto é o ponto de lock natural. |

## Consequências

- **No caixa**, a lista de produtos mostra o saldo e desabilita os zerados, mas pode estar desatualizada; o pagamento é a verificação real. Ao receber `insufficient_stock`, o front cancela o pedido recém-criado, mantém o carrinho e mostra os produtos sem saldo.
- **Pedidos `PENDING` antigos** (criados e não pagos) podem ser pagos depois de uma entrada de estoque.
- **O histórico explica cada saldo**: pelo `balance_after` de cada movimento é possível auditar a sequência inteira.
- **Sem ficha técnica**: produtos compostos (drinks, porções) continuam sem controle de estoque.
- **Uma linha travada por produto vendido**, durante a transação do pagamento, que é curta e não faz chamadas externas (a entrega ao PulseBoard é assíncrona, pela outbox).
