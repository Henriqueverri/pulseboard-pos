import { MinusIcon, PlusIcon, ShoppingCartIcon, TrashIcon } from '@phosphor-icons/react'
import type { Dispatch } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/States'
import { formatCents } from '../../lib/money'
import { lineTotalCents, MAX_QUANTITY, type CartAction, type CartItem } from './cartReducer'

export function Cart({ items, dispatch }: { items: CartItem[]; dispatch: Dispatch<CartAction> }) {
  if (items.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-ink/10">
        <EmptyState
          compact
          icon={ShoppingCartIcon}
          title="Carrinho vazio. Busque um produto para começar."
        />
      </div>
    )
  }
  const setQuantity = (item: CartItem, quantity: number) =>
    dispatch({ type: 'setQuantity', productId: item.productId, quantity })

  return (
    <ul className="divide-y divide-ink/[0.05]" aria-label="Carrinho">
      {items.map((item) => (
        <li key={item.productId} className="space-y-2 py-3 first:pt-0 last:pb-0">
          <div className="flex items-start gap-2">
            <div className="min-w-0 flex-1">
              <p className="text-sm leading-snug font-medium text-ink">{item.name}</p>
              <p className="mt-0.5 text-xs text-ink/55">
                <span className="font-mono">{item.sku}</span> · {formatCents(item.unitPriceCents)} cada
              </p>
            </div>
            <Button
              variant="ghost"
              size="sm"
              icon
              className="-mt-1 -mr-2 text-ink/50 hover:text-danger-strong"
              aria-label={`Remover ${item.name}`}
              onClick={() => dispatch({ type: 'remove', productId: item.productId })}
            >
              <TrashIcon size={16} aria-hidden="true" />
            </Button>
          </div>
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center rounded-lg border border-ink/10">
              <button
                type="button"
                aria-label={`Diminuir ${item.name}`}
                className="flex size-8 items-center justify-center rounded-l-lg text-ink/70 hover:bg-ink/[0.04] disabled:opacity-40"
                disabled={item.quantity <= 1}
                onClick={() => setQuantity(item, item.quantity - 1)}
              >
                <MinusIcon size={14} aria-hidden="true" />
              </button>
              <input
                type="number"
                aria-label={`Quantidade de ${item.name}`}
                min={1}
                max={MAX_QUANTITY}
                className="h-8 w-12 [appearance:textfield] border-x border-ink/10 bg-transparent text-center text-sm tabular focus:ring-0 focus:ring-offset-0 focus:outline-none [&::-webkit-inner-spin-button]:appearance-none"
                value={item.quantity}
                onChange={(event) => setQuantity(item, Number(event.target.value))}
              />
              <button
                type="button"
                aria-label={`Aumentar ${item.name}`}
                className="flex size-8 items-center justify-center rounded-r-lg text-ink/70 hover:bg-ink/[0.04] disabled:opacity-40"
                disabled={item.quantity >= MAX_QUANTITY}
                onClick={() => setQuantity(item, item.quantity + 1)}
              >
                <PlusIcon size={14} aria-hidden="true" />
              </button>
            </div>
            <span className="text-sm font-semibold tabular">{formatCents(lineTotalCents(item))}</span>
          </div>
        </li>
      ))}
    </ul>
  )
}
