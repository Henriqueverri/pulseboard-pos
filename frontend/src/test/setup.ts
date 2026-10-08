import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { resetDb } from './db'
import { server } from './server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

beforeEach(() => {
  resetDb()
  sessionStorage.clear()
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  server.events.removeAllListeners()
})

afterAll(() => server.close())
