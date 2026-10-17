import type { Messages } from '../i18n'
import type { HeirDocument } from '../api/types'

/**
 * An amount with the euro sign after it, separated by a no-break space, in every language:
 * "1,234.50 €" in English too, where Intl would put the sign in front. Without `cents`, whole euros.
 */
export function formatEuros(value: number, locale: string, cents = true): string {
  const digits = cents ? 2 : 0
  const amount = new Intl.NumberFormat(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits })
    .format(value)
  return `${amount}\u00a0€`
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

/** Type and heir, e.g. "Passport — Ana", for labels and messages */
export function documentName(document: HeirDocument, t: Messages): string {
  return `${documentTypeLabel(document.type, t)} — ${document.heir ?? t.noHeir}`
}

/** Lower-case and strip accents so that "cafe" matches "Café". */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/** Words that join names without being one: "Maria da Conceição", "João dos Santos", "Costa e Silva" */
const CONNECTORS = new Set(['de', 'da', 'do', 'dos', 'das', 'e'])

/**
 * First, second and last name, each with the connector before it:
 * "Maria da Conceição Silva Costa" is "Maria da Conceição Costa", and
 * "Ana Maria Ferreira dos Santos" is "Ana Maria dos Santos".
 */
export function shortName(name: string): string {
  const names: string[] = []
  let connectors: string[] = []
  for (const word of name.trim().split(/\s+/)) {
    if (CONNECTORS.has(word.toLowerCase())) {
      connectors.push(word)
    } else {
      names.push([...connectors, word].join(' '))
      connectors = []
    }
  }
  // A name that ends in a connector keeps it
  if (connectors.length > 0) {
    names.push(connectors.join(' '))
  }
  return names.length > 3 ? [names[0], names[1], names[names.length - 1]].join(' ') : names.join(' ')
}
