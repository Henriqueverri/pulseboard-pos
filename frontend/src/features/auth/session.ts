import type { LoginResponse, User } from '../../api/types'

/**
 * The JWT lives in sessionStorage: it survives a refresh of the tab and disappears when the tab is
 * closed. This is a deliberate project decision (ADR-007), with the XSS trade-off documented in the
 * README; do not move it to localStorage.
 */
const STORAGE_KEY = 'pos.session'

export interface Session {
  token: string
  expiresAt: number
  user: User
}

export function sessionFromLogin(response: LoginResponse, now = Date.now()): Session {
  return {
    token: response.access_token,
    expiresAt: now + response.expires_in * 1000,
    user: response.user,
  }
}

export function loadSession(now = Date.now()): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const session = JSON.parse(raw) as Session
    if (!session.token || !session.user || session.expiresAt <= now) {
      clearSession()
      return null
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY)
}

export function currentToken(): string | null {
  return loadSession()?.token ?? null
}
