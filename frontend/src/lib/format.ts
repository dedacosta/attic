import type { Messages } from '../i18n'

export function formatEuros(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(value)
}

export function formatDate(isoDate: string, locale: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(locale, { dateStyle: 'medium' })
}

export function formatDateTime(timestamp: string, locale: string): string {
  return new Date(timestamp).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' })
}

/** Heirs sorted by name, for lists to pick from */
export function byName<T extends { name: string }>(heirs: T[], locale: string): T[] {
  return [...heirs].sort((a, b) => a.name.localeCompare(b.name, locale))
}

/** An amount in euros as typed: 12, 12.5 or 12,50 */
export const AMOUNT_PATTERN = /^\d+([.,]\d{1,2})?$/

/** An amount as typed, e.g. "12,50", as a number */
export function parseAmount(text: string): number {
  return Number(text.trim().replace(',', '.'))
}

/** An amount for an input, e.g. 35.5 as "35,50" in Portuguese */
export function amountText(value: number, locale: string): string {
  return value.toFixed(2).replace('.', decimalSeparator(locale))
}

/** "," or "." for the locale, so 35.5 is shown as "35,50" in Portuguese */
export function decimalSeparator(locale: string): string {
  return (1.5).toLocaleString(locale).charAt(1)
}

export function locationLabel(location: string, t: Messages): string {
  return t.rooms[location] ?? location
}

const FIRST_YEAR = 2026

/** "2026", or "2026–2028" in later years */
export function copyrightYears(now = new Date()): string {
  return now.getFullYear() > FIRST_YEAR ? `${FIRST_YEAR}–${now.getFullYear()}` : String(FIRST_YEAR)
}

/** First characters of the id, like a short git commit hash */
export function shortId(id: string): string {
  return id.slice(0, 7)
}

export function documentTypeLabel(type: string, t: Messages): string {
  return t.documentTypes[type] ?? type
}

/** Lower-case and strip accents so that "cafe" matches "Café". */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}
