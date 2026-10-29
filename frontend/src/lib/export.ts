import { age, localDate } from './age'
import { copyrightYears, formatDate, formatEuros, shortId, shortName } from './format'
import type { Messages } from '../i18n'
import type { CatalogItem, ContributionYear, Heir, InventoryItem, Renovation } from '../api/types'

/*
 * What the PDF and the LaTeX exports share: the file names, the lines above and below the tables
 * and the text of their cells. Kept apart from pdf.ts so that the LaTeX export does not load the
 * PDF library.
 */

/** e.g. 2026-10-05, for file names */
export function isoDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export const generatedOn = (now: Date, t: Messages) =>
  t.pdfGenerated(now.toLocaleString(t.locale, { dateStyle: 'long', timeStyle: 'short' }))

/** Copyright and Attic's version, at the bottom of every page */
export const copyrightLine = (now: Date) => `© ${copyrightYears(now)} David Da Costa · Attic v${__APP_VERSION__}`

/** What the footer says of the catalog after the copyright: when, how many and the filters */
export function catalogNote(items: CatalogItemExport[], totalCount: number, filters: string[], now: Date, t: Messages) {
  const count = items.length === totalCount ? t.itemCount(totalCount) : t.filteredCount(items.length, totalCount)
  return [generatedOn(now, t), count, ...filters].join('  ·  ')
}

export const catalogHead = (t: Messages) =>
  [t.idColumn, t.name, t.quantityShort, t.location, t.date, t.present, t.value, t.owner]

/** An item of the catalog as exported, with where it is in words */
export type CatalogItemExport = CatalogItem & { place: string }

export const catalogCells = (item: CatalogItemExport, t: Messages) => [
  shortId(item.id),
  item.name,
  String(item.quantity),
  item.place,
  item.date ? formatDate(item.date, t.locale) : '',
  item.existent ? t.yes : t.no,
  formatEuros(item.valueEur, t.locale),
  ownerNames(item, t),
]

/** Who owns an item: its heirs, or the Heritage */
export const ownerNames = (item: CatalogItem, t: Messages) =>
  item.owners.length > 0 ? item.owners.map((owner) => owner.name).join(', ') : t.heritageTitle

/** A room of the inventory, as exported */
export interface InventoryRoomExport {
  title: string
  comments: string | null
  items: InventoryItem[]
  missing: boolean
}

/** What the footer says of the inventory after the copyright: when, how many and the filters */
export function inventoryNote(rooms: InventoryRoomExport[], totalCount: number, filters: string[], now: Date,
  t: Messages) {
  const shown = rooms.reduce((sum, { items }) => sum + items.length, 0)
  const count = shown === totalCount ? t.itemCount(totalCount) : t.filteredCount(shown, totalCount)
  return [generatedOn(now, t), count, ...filters].join('  ·  ')
}

/** Sums in cents, as 33.33 + 66.67 is not exactly 100 in floating point */
export const inventorySum = (items: Pick<InventoryItem, 'valueEur'>[]) =>
  items.reduce((cents, item) => cents + Math.round(item.valueEur * 100), 0) / 100

/** What the footer says of the heirs after the copyright. The heirs are counted without the deceased, who are still listed */
export function heirsNote(rows: { heir: Heir }[], everybody: Heir[], filters: string[], now: Date, t: Messages) {
  const living = everybody.filter((heir) => !heir.deceased).length
  const count = rows.length === everybody.length ? t.heirCount(living)
    : t.filteredHeirCount(rows.filter(({ heir }) => !heir.deceased).length, living)
  return [generatedOn(now, t), count, ...filters].join('  ·  ')
}

export const heirsHead = (t: Messages) =>
  [t.name, t.birthDate, t.deathDate, t.ageColumn, t.heritageShare, t.parentHeir, t.address]

/** `names` gives the names of the parents, shortened as on the contributions tab */
export const heirCells = (heir: Heir, names: Map<string, string>, now: Date, t: Messages) => [
  heir.deceased ? `${heir.name} †` : heir.name,
  heir.birthDate ? formatDate(heir.birthDate, t.locale) : '',
  heir.deceased ? (heir.deathDate ? formatDate(heir.deathDate, t.locale) : t.deceased) : '',
  // Of the deceased, the age at death; unknown without its date
  heir.birthDate && (!heir.deceased || heir.deathDate)
    ? String(age(heir.birthDate, heir.deathDate ? localDate(heir.deathDate) : now)) : '',
  heir.calculatedShare ?? '—',
  heir.parentId && names.has(heir.parentId) ? shortName(names.get(heir.parentId)!) : '',
  heir.address ?? '',
]

/** Years of the annual contribution on one page */
const YEARS_PER_PAGE = 10

/** One page of the contributions: a table with a line per heir and a column per year */
export interface ContributionPage {
  head: string[]
  body: string[][]
  totals: string[]
}

/**
 * Every year of the annual contribution, ten years per page, the oldest first: a line per heir
 * and a column per year. A dash marks a year in which the heir does not pay (not born yet,
 * deceased, or a parent still alive); an empty cell, one whose amount is not entered yet.
 */
export function contributionPages(years: ContributionYear[], t: Messages): ContributionPage[] {
  const ordered = [...years].sort((a, b) => a.year - b.year)

  // Everybody, in the order of the latest year, which knows every heir there is now
  const heirs = new Map<string, { name: string; deceased: boolean }>()
  for (const year of [...ordered].reverse()) {
    for (const line of year.lines) {
      if (!heirs.has(line.heirId)) {
        heirs.set(line.heirId, { name: line.heir, deceased: line.deceased })
      }
    }
  }
  // Whole amounts without cents: 200 €, but 150.50 €. Sums are rounded to cents first, as
  // 33.33 + 66.67 is not exactly 100 in floating point.
  const euros = (value: number) => {
    const cents = Math.round(value * 100)
    return cents % 100 === 0
      ? formatEuros(cents / 100, t.locale, false)
      : formatEuros(cents / 100, t.locale)
  }

  const pages: ContributionPage[] = []
  for (let first = 0; first < ordered.length; first += YEARS_PER_PAGE) {
    const page = ordered.slice(first, first + YEARS_PER_PAGE)
    const body = [...heirs].map(([heirId, heir]) => {
      let total = 0
      let concerned = false
      const cells = page.map((year) => {
        const line = year.lines.find((l) => l.heirId === heirId)
        concerned ||= line !== undefined && (line.pays || line.amountEur !== null)
        if (line && line.amountEur !== null) {
          total += line.amountEur
          return euros(line.amountEur)
        }
        return line?.pays ? '' : '—'
      })
      // Somebody who never had to pay in these years has no total either
      return [heir.deceased ? `${heir.name} †` : heir.name, ...cells, concerned ? euros(total) : '—']
    })
    pages.push({
      head: [t.heirColumn, ...page.map((year) => String(year.year)), t.total],
      body,
      totals: [t.total, ...page.map((year) => euros(year.totalEur)),
        euros(page.reduce((sum, year) => sum + year.totalEur, 0))],
    })
  }
  return pages
}

/** A renovation as exported: its heading, its expenses with their total, and the heirs with what they gave. Its comment is left out. */
export interface RenovationExport {
  heading: string
  description: string | null
  expensesHead: string[]
  expenses: string[][]
  /** Under the amounts: the cost */
  cost: string[]
  heirsHead: string[]
  heirs: string[][]
  /** Below the heirs: what is paid and what is missing */
  summary: string
}

export function renovationExport(renovation: Renovation, t: Messages): RenovationExport {
  const euros = (value: number) => formatEuros(value, t.locale)
  const tick = (done: boolean) => (done ? t.yes : '—')
  return {
    heading: `${renovation.year} — ${renovation.title}`,
    description: renovation.description,
    expensesHead: [t.description, t.expenseKind, t.amountPaid, t.amount],
    expenses: renovation.expenses.map((expense) =>
      [expense.description, t.expenseKinds[expense.kind], tick(expense.paid), euros(expense.amountEur)]),
    cost: [t.total, '', '', euros(renovation.costEur)],
    heirsHead: [t.heir, t.share, t.amountPaid, t.expenseKinds.WORKMANSHIP],
    heirs: renovation.lines.map((line) => [
      line.deceased ? `${line.heir} †` : line.heir,
      line.dueEur !== null ? euros(line.dueEur) : '—',
      tick(line.paid),
      tick(line.workmanship),
    ]),
    summary: `${t.amountPaid}${t.colon} ${euros(renovation.paidEur)}  ·  ${t.amountMissing}${t.colon} ${euros(renovation.missingEur)}`,
  }
}
