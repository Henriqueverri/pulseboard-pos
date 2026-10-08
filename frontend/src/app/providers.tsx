import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { AuthProvider } from '../features/auth/AuthProvider'

export function AppProviders({
  queryClient,
  children,
}: {
  queryClient: QueryClient
  children: ReactNode
}) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
}
