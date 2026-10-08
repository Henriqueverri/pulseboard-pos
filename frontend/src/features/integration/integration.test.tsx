import { act, screen, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { db, makeEvent, makeOrder } from '../../test/db'
import { problem } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'
import { POLL_INTERVAL_MS } from './events'

function seedOrderWithEvents() {
  const order = makeOrder({ status: 'REFUNDED', number: 512 })
  db.orders.push(order)
  const paid = makeEvent({
    sequence: 1023,
    orderId: order.id,
    orderNumber: 512,
    status: 'FAILED',
    failureKind: 'PERMANENT',
    attempts: 1,
    lastAttemptAt: '2026-10-07T12:00:05Z',
    nextAttemptAt: null,
    lastHttpStatus: 422,
    lastErrorCode: 'validation_failed',
    lastError: 'The selected product SKU is invalid. | items.0.sku: The selected product SKU is invalid.',
    lastRequestId: 'pb-req-9f2c',
    payload: { external_id: `pos:${order.id}`, status: 'paid', total_amount: '159.80' },
  })
  const refunded = makeEvent({
    sequence: 1024,
    orderId: order.id,
    orderNumber: 512,
    eventType: 'ORDER_REFUNDED',
    blockedBy: 1023,
  })
  db.events.push(paid, refunded)
  return { order, paid, refunded }
}

function rowOf(sequence: number) {
  return screen.getByRole('cell', { name: `#${sequence}` }).closest('tr') as HTMLElement
}

describe('integration screen', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the health card and the events with what holds them back', async () => {
    seedOrderWithEvents()
    db.events.push(
      makeEvent({
        sequence: 1025,
        orderId: crypto.randomUUID(),
        orderNumber: 513,
        status: 'SENT',
        attempts: 1,
        processedAt: '2026-10-07T12:10:00Z',
      }),
    )
    db.integration.lastSentAt = '2026-10-07T12:10:00Z'
    renderApp('/integration', { as: 'ADMIN' })

    expect(await screen.findByRole('heading', { name: 'Integração com o PulseBoard' })).toBeInTheDocument()
    const card = await screen.findByRole('region', { name: 'Saúde da integração' })
    expect(within(card).getByText('Ligada')).toBeInTheDocument()
    expect(within(card).getByText('http://localhost:8000/api/v1')).toBeInTheDocument()
    expect(within(card).getByText('pb_abcdefghijkl_…')).toBeInTheDocument()
    expect(within(card).getByText('1 / 1')).toBeInTheDocument()

    expect(await screen.findByRole('cell', { name: '#1025' })).toBeInTheDocument()
    expect(within(rowOf(1025)).getByText('Enviado')).toBeInTheDocument()
    expect(within(rowOf(1023)).getByText('Falhou (rejeitado)')).toBeInTheDocument()
    expect(within(rowOf(1023)).getByText(/^validation_failed: The selected product SKU/)).toBeInTheDocument()
    expect(within(rowOf(1024)).getByText('bloqueado por #1023')).toBeInTheDocument()
    expect(within(rowOf(1024)).getByText('Estorno')).toBeInTheDocument()
    // Only FAILED events can be reprocessed.
    expect(within(rowOf(1024)).queryByRole('button', { name: /Reprocessar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reprocesses a failed event', async () => {
    const { paid } = seedOrderWithEvents()
    const { user } = renderApp('/integration', { as: 'ADMIN' })

    await user.click(await screen.findByRole('button', { name: 'Reprocessar evento #1023' }))

    await expect.poll(() => within(rowOf(1023)).queryByText('Pendente')).not.toBeNull()
    expect(db.events.find((event) => event.id === paid.id)).toMatchObject({
      status: 'PENDING',
      failureKind: null,
      attempts: 1,
      attemptsAtRetry: 1,
    })
  })

  it('shows the API error when a retry is refused', async () => {
    seedOrderWithEvents()
    server.use(
      http.post('/api/integration/events/:id/retry', () =>
        problem(409, 'integration_event_not_failed', 'Só eventos FAILED podem ser reprocessados.'),
      ),
    )
    const { user } = renderApp('/integration', { as: 'ADMIN' })

    await user.click(await screen.findByRole('button', { name: 'Reprocessar evento #1023' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Só eventos FAILED podem ser reprocessados.')
  })

  it('opens the detail drawer with the payload and the request ids', async () => {
    const { paid } = seedOrderWithEvents()
    const { user } = renderApp('/integration', { as: 'ADMIN' })

    await screen.findByRole('cell', { name: '#1023' })
    await user.click(within(rowOf(1023)).getByRole('button', { name: 'Detalhes' }))

    const drawer = await screen.findByRole('dialog', { name: 'Evento #1023' })
    expect(within(drawer).getByText('422')).toBeInTheDocument()
    expect(within(drawer).getByText('validation_failed')).toBeInTheDocument()
    expect(within(drawer).getByText(`pos-${paid.id}`)).toBeInTheDocument()
    expect(within(drawer).getByText('pb-req-9f2c')).toBeInTheDocument()
    expect(within(drawer).getByLabelText('Payload enviado')).toHaveTextContent('"total_amount": "159.80"')

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('warns when the integration is paused and resumes it after the key is fixed', async () => {
    const order = makeOrder({ status: 'PAID' })
    db.orders.push(order)
    db.events.push(
      makeEvent({
        sequence: 7,
        orderId: order.id,
        status: 'FAILED',
        failureKind: 'CONFIGURATION',
        attempts: 1,
        lastHttpStatus: 401,
        lastErrorCode: 'invalid_api_key',
      }),
    )
    db.integration.pausedUntil = '2026-10-07T12:15:00Z'
    const { user } = renderApp('/integration', { as: 'ADMIN' })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Integração pausada: API Key inválida, revogada ou expirada.',
    )
    expect(screen.getByText(/^Pausada até/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reprocessar falhas de configuração' }))

    expect(await screen.findByText('1 evento voltou para a fila.')).toBeInTheDocument()
    await expect.poll(() => screen.queryByText(/Integração pausada/)).toBeNull()
    expect(screen.getByText('Ligada')).toBeInTheDocument()
    expect(db.events[0]).toMatchObject({ status: 'PENDING', failureKind: null })
  })

  it('says when the integration is off', async () => {
    db.integration.enabled = false
    db.integration.keyPrefix = null
    renderApp('/integration', { as: 'ADMIN' })

    const card = await screen.findByRole('region', { name: 'Saúde da integração' })
    expect(within(card).getByText('Desligada')).toBeInTheDocument()
    expect(within(card).getByText('não configurada')).toBeInTheDocument()
    expect(await screen.findByText('Nenhum evento encontrado.')).toBeInTheDocument()
  })

  it('filters by status through the URL', async () => {
    seedOrderWithEvents()
    const { router, user } = renderApp('/integration', { as: 'ADMIN' })
    expect(await screen.findByRole('cell', { name: '#1024' })).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Status'), 'FAILED')

    expect(router.state.location.search).toBe('?status=FAILED')
    await expect.poll(() => screen.queryByRole('cell', { name: '#1024' })).toBeNull()
    expect(screen.getByRole('cell', { name: '#1023' })).toBeInTheDocument()
  })

  it('polls so a delivery shows up without reloading', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const order = makeOrder({ status: 'PAID' })
    db.orders.push(order)
    const event = makeEvent({ sequence: 3, orderId: order.id })
    db.events.push(event)
    renderApp('/integration', { as: 'ADMIN' })
    expect(await screen.findByText('Pendente', { selector: 'span' })).toBeInTheDocument()

    Object.assign(event, { status: 'SENT', attempts: 1, processedAt: '2026-10-07T12:00:03Z' })
    await act(() => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS))

    expect(await screen.findByText('Enviado', { selector: 'span' })).toBeInTheDocument()
  })

  it('is not available to the cashier', async () => {
    renderApp('/integration', { as: 'CASHIER' })

    expect(await screen.findByRole('heading', { name: 'Acesso negado' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link', { name: 'Integração' })).not.toBeInTheDocument()
  })

  it('is in the admin menu', async () => {
    renderApp('/', { as: 'ADMIN' })

    const nav = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Integração' })).toHaveAttribute('href', '/integration')
  })
})

describe('integration status in the order detail', () => {
  it('shows each delivery of the order, including why it waits', async () => {
    const { order } = seedOrderWithEvents()
    renderApp(`/orders/${order.id}`, { as: 'ADMIN' })

    const section = await screen.findByRole('region', { name: 'Integração com o PulseBoard' })
    const rows = within(section).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('Venda')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Falhou (rejeitado)')).toBeInTheDocument()
    expect(within(rows[1]).getByText(/items\.0\.sku/)).toBeInTheDocument()
    expect(within(rows[2]).getByText('Estorno')).toBeInTheDocument()
    expect(within(rows[2]).getByText('bloqueado por #1023')).toBeInTheDocument()
    expect(within(section).getByRole('link', { name: 'Abrir Integração' })).toHaveAttribute('href', '/integration')
  })

  it('shows the cashier the status without the admin link', async () => {
    const { order } = seedOrderWithEvents()
    renderApp(`/orders/${order.id}`, { as: 'CASHIER' })

    const section = await screen.findByRole('region', { name: 'Integração com o PulseBoard' })
    expect(within(section).getByText('Falhou (rejeitado)')).toBeInTheDocument()
    expect(within(section).queryByRole('link', { name: 'Abrir Integração' })).not.toBeInTheDocument()
  })

  it('adds the sale to the integration as soon as the order is paid', async () => {
    const order = makeOrder({ status: 'PENDING' })
    db.orders.push(order)
    const { user } = renderApp(`/orders/${order.id}`, { as: 'CASHIER' })
    expect(await screen.findByText(/Nada enviado ao PulseBoard/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Pagar' }))
    const dialog = screen.getByRole('dialog', { name: 'Pagar pedido #1001' })
    await user.click(within(dialog).getByLabelText('Pix'))
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar pagamento' }))

    const section = await screen.findByRole('region', { name: 'Integração com o PulseBoard' })
    expect(await within(section).findByText('Pendente')).toBeInTheDocument()
    expect(within(section).getByText('Venda')).toBeInTheDocument()
  })
})
