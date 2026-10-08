import { describe, expect, it } from 'vitest'
import { centsToMoneyString, formatCents, formatMoney, moneyToCents, parseMoneyToCents } from './money'

describe('parseMoneyToCents', () => {
  it.each([
    ['79.90', 7990],
    ['79,90', 7990],
    ['79,9', 7990],
    ['79', 7900],
    [' 0.05 ', 5],
    ['9999999999.99', 999999999999],
  ])('parses %j as %i cents', (input, cents) => {
    expect(parseMoneyToCents(input)).toBe(cents)
  })

  it.each(['', 'abc', '1.234', '-1.00', '1e3', '1.000,00', '12345678901'])('rejects %j', (input) => {
    expect(parseMoneyToCents(input)).toBeNull()
  })
})

describe('centsToMoneyString', () => {
  it('produces the API representation with two decimals', () => {
    expect(centsToMoneyString(7990)).toBe('79.90')
    expect(centsToMoneyString(5)).toBe('0.05')
    expect(centsToMoneyString(0)).toBe('0.00')
  })

  it('rejects values that are not whole non-negative cents', () => {
    expect(() => centsToMoneyString(1.5)).toThrow(RangeError)
    expect(() => centsToMoneyString(-1)).toThrow(RangeError)
  })
})

describe('moneyToCents', () => {
  it('round-trips API values exactly', () => {
    expect(centsToMoneyString(moneyToCents('409.70'))).toBe('409.70')
  })

  it('fails loudly on malformed API values', () => {
    expect(() => moneyToCents('79.999')).toThrow(RangeError)
  })
})

describe('formatting', () => {
  it('formats in pt-BR currency', () => {
    expect(formatCents(40970)).toBe('R$\u00a0409,70')
    expect(formatMoney('1234.50')).toBe('R$\u00a01.234,50')
  })
})
