/**
 * Money arrives from the API as a decimal string ("79.90") and is handled here as integer cents,
 * never as a floating-point number. The server recalculates every total; values computed in the
 * browser are for display only.
 */

const MONEY_PATTERN = /^(\d{1,10})(?:[.,](\d{1,2}))?$/

/** "79.90" | "79,9" | "79" → cents; null when the text is not a valid amount. */
export function parseMoneyToCents(value: string): number | null {
  const match = MONEY_PATTERN.exec(value.trim())
  if (!match) {
    return null
  }
  const [, units, decimals = ''] = match
  return Number(units) * 100 + Number(decimals.padEnd(2, '0'))
}

/** Cents → the API representation ("79.90"). */
export function centsToMoneyString(cents: number): string {
  if (!Number.isSafeInteger(cents) || cents < 0) {
    throw new RangeError(`invalid cents: ${cents}`)
  }
  const units = Math.trunc(cents / 100)
  const rest = String(cents % 100).padStart(2, '0')
  return `${units}.${rest}`
}

/** API money string → cents. The API always sends a valid value; anything else is a bug. */
export function moneyToCents(value: string): number {
  const cents = parseMoneyToCents(value)
  if (cents === null) {
    throw new RangeError(`invalid money value: ${value}`)
  }
  return cents
}

const formatters = new Map<string, Intl.NumberFormat>()

/** Formats cents for display, e.g. 40970 → "R$ 409,70", using the exact decimal string. */
export function formatCents(cents: number, currency = 'BRL'): string {
  let formatter = formatters.get(currency)
  if (!formatter) {
    formatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency })
    formatters.set(currency, formatter)
  }
  return formatter.format(centsToMoneyString(cents) as Intl.StringNumericLiteral)
}

export function formatMoney(value: string, currency = 'BRL'): string {
  return formatCents(moneyToCents(value), currency)
}
