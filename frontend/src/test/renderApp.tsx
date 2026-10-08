import { QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import type { Role } from '../api/types'
import { AppProviders } from '../app/providers'
import { routes } from '../app/router'
import { saveSession } from '../features/auth/session'
import { users } from './db'
import { TOKENS } from './handlers'

export function signIn(role: Role): void {
  saveSession({ token: TOKENS[role], expiresAt: Date.now() + 3_600_000, user: users[role] })
}

/** Renders the real routes and providers at {@code path}, optionally already signed in. */
export function renderApp(path = '/', { as }: { as?: Role } = {}) {
  if (as) {
    signIn(as)
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const user = userEvent.setup()
  render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { router, user }
}
