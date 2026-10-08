const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

/** ISO instant from the API → local date and time of the browser. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTimeFormatter.format(new Date(iso)) : '—'
}

/** "YYYY-MM-DD" (an <input type="date"> value) → ISO instant of local midnight. */
export function startOfLocalDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day).toISOString()
}

/** "YYYY-MM-DD" → ISO instant of the next local midnight (exclusive upper bound). */
export function endOfLocalDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day + 1).toISOString()
}
