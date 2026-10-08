import type { Dispatch } from 'react'
import { formatCents } from '../../lib/money'
import { lineTotalCents, MAX_QUANTITY, type CartAction, type CartItem } from './cartReducer'

export function Cart({ items, dispatch }: { items: CartItem[]; dispatch: Dispatch<CartAction> }) {
  if (items.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 px-3 py-6 text-center text-sm text-slate-500">
        Carrinho vazio. Busque um produto para começar.
      </p>
    )
  }
  return (
    <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white" aria-label="Carrinho">
      {items.map((item) => (
        <li key={item.productId} className="flex flex-wrap items-center gap-3 px-3 py-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{item.name}</p>
            <p className="text-xs text-slate-500">
              <span className="font-mono">{item.sku}</span> · {formatCents(item.unitPriceCents)} cada
            </p>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label={`Diminuir ${item.name}`}
              className="h-8 w-8 rounded border border-slate-300 disabled:opacity-40"
              disabled={item.quantity <= 1}
              onClick={() =>
                dispatch({ type: 'setQuantity', productId: item.productId, quantity: item.quantity - 1 })
              }
            >
              −
            </button>
            <input
              type="number"
              aria-label={`Quantidade de ${item.name}`}
              min={1}
              max={MAX_QUANTITY}
              className="h-8 w-16 rounded border border-slate-300 text-center text-sm"
              value={item.quantity}
              onChange={(event) =>
                dispatch({
                  type: 'setQuantity',
                  productId: item.productId,
                  quantity: Number(event.target.value),
                })
              }
            />
            <button
              type="button"
              aria-label={`Aumentar ${item.name}`}
              className="h-8 w-8 rounded border border-slate-300 disabled:opacity-40"
              disabled={item.quantity >= MAX_QUANTITY}
              onClick={() =>
                dispatch({ type: 'setQuantity', productId: item.productId, quantity: item.quantity + 1 })
              }
            >
              +
            </button>
          </div>
          <span className="w-28 text-right text-sm font-medium tabular-nums">
            {formatCents(lineTotalCents(item))}
          </span>
          <button
            type="button"
            aria-label={`Remover ${item.name}`}
            className="text-sm text-red-600 hover:underline"
            onClick={() => dispatch({ type: 'remove', productId: item.productId })}
          >
            Remover
          </button>
        </li>
      ))}
    </ul>
  )
}
