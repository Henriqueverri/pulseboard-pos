import { XIcon } from '@phosphor-icons/react'
import * as RadixDialog from '@radix-ui/react-dialog'
import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

const overlayClass = 'fixed inset-0 z-50 bg-ink/30 backdrop-blur-[2px] data-[state=open]:animate-fade-in'
const closeClass = 'rounded-md p-1.5 text-ink/65 hover:bg-ink/5 hover:text-ink'

/** Modal dialog: bottom sheet on phones, centered card from `sm` up. */
export function Dialog({
  open,
  title,
  description,
  size = 'md',
  onClose,
  footer,
  children,
}: {
  open: boolean
  title: string
  description?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  onClose: () => void
  footer?: ReactNode
  children: ReactNode
}) {
  return (
    <RadixDialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className={overlayClass} />
        <RadixDialog.Content
          {...(description ? {} : { 'aria-describedby': undefined })}
          className={cn(
            'fixed inset-x-0 bottom-0 z-50 flex max-h-[92dvh] flex-col rounded-t-2xl bg-surface shadow-pop outline-none data-[state=open]:animate-sheet-in',
            'sm:inset-auto sm:top-1/2 sm:left-1/2 sm:max-h-[85vh] sm:w-[calc(100%-2rem)] sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-2xl sm:data-[state=open]:animate-dialog-in',
            size === 'sm' ? 'sm:max-w-sm' : size === 'lg' ? 'sm:max-w-2xl' : 'sm:max-w-lg',
          )}
        >
          <header className="flex items-start justify-between gap-4 px-5 pt-5 pb-2 sm:px-6 sm:pt-6">
            <div className="min-w-0">
              <RadixDialog.Title className="text-base font-semibold text-ink">{title}</RadixDialog.Title>
              {description && (
                <RadixDialog.Description className="mt-1 text-sm text-ink/65">
                  {description}
                </RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close className={cn(closeClass, '-mt-1 -mr-1.5')} aria-label="Fechar">
              <XIcon size={18} aria-hidden="true" />
            </RadixDialog.Close>
          </header>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 py-3 sm:px-6 sm:pb-6">{children}</div>
          {footer && (
            <footer className="flex flex-col-reverse gap-2 border-t border-ink/[0.07] px-5 py-4 sm:flex-row sm:justify-end sm:px-6">
              {footer}
            </footer>
          )}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  )
}

/** Side panel over the page (right) or navigation drawer (left). */
export function Drawer({
  open,
  title,
  description,
  side = 'right',
  hideHeader = false,
  onClose,
  children,
}: {
  open: boolean
  title: string
  description?: ReactNode
  side?: 'left' | 'right'
  /** Keeps the title for screen readers only. */
  hideHeader?: boolean
  onClose: () => void
  children: ReactNode
}) {
  return (
    <RadixDialog.Root open={open} onOpenChange={(next) => !next && onClose()}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className={overlayClass} />
        <RadixDialog.Content
          {...(description ? {} : { 'aria-describedby': undefined })}
          className={cn(
            'fixed inset-y-0 z-50 flex flex-col bg-surface shadow-pop outline-none',
            side === 'left'
              ? 'left-0 w-[min(280px,85vw)] data-[state=open]:animate-drawer-left'
              : 'right-0 w-[min(560px,96vw)] data-[state=open]:animate-drawer-right',
          )}
        >
          <header
            className={cn(
              'flex items-start justify-between gap-4 border-b border-ink/[0.07] px-5 py-4',
              hideHeader && 'sr-only',
            )}
          >
            <div className="min-w-0">
              <RadixDialog.Title className="text-base font-semibold text-ink">{title}</RadixDialog.Title>
              {description && (
                <RadixDialog.Description className="mt-0.5 text-sm text-ink/65">
                  {description}
                </RadixDialog.Description>
              )}
            </div>
            <RadixDialog.Close className={cn(closeClass, '-mr-1.5')} aria-label="Fechar">
              <XIcon size={18} aria-hidden="true" />
            </RadixDialog.Close>
          </header>
          <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  )
}
