import { jsPDF } from 'jspdf'
import { autoTable } from 'jspdf-autotable'
import { age, localDate } from './age'
import { copyrightYears, formatDate, formatEuros, locationLabel, shortId } from './format'
import type { Messages } from '../i18n'
import type { ContributionYear, CatalogItem, Heir } from '../api/types'

const ACCENT: [number, number, number] = [181, 101, 29]
const MUTED: [number, number, number] = [115, 106, 96]
const STRIPE: [number, number, number] = [246, 243, 238]
const TEXT: [number, number, number] = [42, 38, 34]
const MARGIN = 14
const TOTAL_PAGES = '{total_pages}'

/** The built-in PDF fonts have no narrow or no-break spaces, which Intl uses in "1 234,50 €". */
const pdfText = (text: string) => text.replace(/[  ]/g, ' ')

/** Title and the line below it, at the top of the first page */
function drawTitle(doc: jsPDF, title: string, subtitle: string) {
  doc.setTextColor(...TEXT)
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(18)
  doc.text(pdfText(title), MARGIN, 18)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(10)
  doc.setTextColor(...MUTED)
  doc.text(pdfText(subtitle), MARGIN, 25)
}

/** Copyright, Attic's version and the page number, at the bottom of every page */
function drawFooter(doc: jsPDF, pageNumber: number, now: Date, t: Messages) {
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...MUTED)
  doc.text(pdfText(`© ${copyrightYears(now)} David Da Costa · Attic v${__APP_VERSION__}`), MARGIN, pageHeight - 8)
  doc.text(pdfText(t.page(pageNumber, TOTAL_PAGES)), pageWidth - MARGIN, pageHeight - 8, { align: 'right' })
}

/** e.g. 2026-10-05, for file names */
function isoDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const generatedOn = (now: Date, t: Messages) =>
  t.pdfGenerated(now.toLocaleString(t.locale, { dateStyle: 'long', timeStyle: 'short' }))

/**
 * Download the given items as a PDF table, one line per item. Comments are left out.
 * `fileName` is without date and extension, e.g. "attic-catalog".
 */
export async function exportPdf(items: CatalogItem[], totalCount: number, filters: string[], t: Messages, title: string,
  fileName: string) {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const now = new Date()
  const count = items.length === totalCount ? t.itemCount(totalCount) : t.filteredCount(items.length, totalCount)
  drawTitle(doc, title, [generatedOn(now, t), count, ...filters].join('  ·  '))

  autoTable(doc, {
    startY: 31,
    margin: { left: MARGIN, right: MARGIN, bottom: 18 },
    head: [[t.idColumn, t.name, t.quantityShort, t.location, t.date, t.present, t.value, t.owner]],
    body: items.map((item) =>
      [
        shortId(item.id),
        item.name,
        String(item.quantity),
        item.location ? locationLabel(item.location, t) : '',
        item.date ? formatDate(item.date, t.locale) : '',
        item.existent ? t.yes : t.no,
        formatEuros(item.valueEur, t.locale),
        item.owner,
      ].map(pdfText),
    ),
    styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: [42, 38, 34] },
    headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: STRIPE },
    columnStyles: {
      0: { font: 'courier', cellWidth: 20 },
      1: { cellWidth: 'auto' },
      2: { halign: 'right', cellWidth: 14 },
      3: { cellWidth: 32 },
      4: { cellWidth: 28 },
      5: { halign: 'center', cellWidth: 24 },
      6: { halign: 'right', cellWidth: 28 },
      7: { cellWidth: 40 },
    },
    didParseCell: (data) => {
      // Headers follow their column's alignment: numbers right, yes/no centred
      if (data.section === 'head') {
        data.cell.styles.halign = data.column.index === 2 || data.column.index === 6 ? 'right'
          : data.column.index === 5 ? 'center' : 'left'
      }
    },
    didDrawPage: (data) => drawFooter(doc, data.pageNumber, now, t),
  })
  doc.putTotalPages(TOTAL_PAGES)
  doc.save(`${fileName}-${isoDay(now)}.pdf`)
}

/** How far a child's name is set in from their parent's, in mm */
const CHILD_INDENT = 4

/**
 * Download the given heirs as a PDF table, one line per heir, in the order given. `depth` is how
 * many ancestors are listed above the heir, whose name is set in by as much. `everybody` is for
 * the names of the parents. Sex, filiation, comments and documents are left out.
 */
export async function exportHeirsPdf(rows: { heir: Heir; depth: number }[], everybody: Heir[], filters: string[],
  t: Messages) {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const now = new Date()
  // The heirs are counted without the deceased, who are still listed
  const living = everybody.filter((heir) => !heir.deceased).length
  const count = rows.length === everybody.length ? t.heirCount(living)
    : t.filteredHeirCount(rows.filter(({ heir }) => !heir.deceased).length, living)
  drawTitle(doc, t.tabHeirs, [generatedOn(now, t), count, ...filters].join('  ·  '))
  const names = new Map(everybody.map((heir) => [heir.id, heir.name]))

  autoTable(doc, {
    startY: 31,
    margin: { left: MARGIN, right: MARGIN, bottom: 18 },
    head: [[t.name, t.birthDate, t.deathDate, t.ageColumn, t.heritageShare, t.parentHeir, t.address]],
    body: rows.map(({ heir }) =>
      [
        heir.deceased ? `${heir.name} †` : heir.name,
        heir.birthDate ? formatDate(heir.birthDate, t.locale) : '',
        heir.deceased ? (heir.deathDate ? formatDate(heir.deathDate, t.locale) : t.deceased) : '',
        // Of the deceased, the age at death; unknown without its date
        heir.birthDate && (!heir.deceased || heir.deathDate)
          ? String(age(heir.birthDate, heir.deathDate ? localDate(heir.deathDate) : now)) : '',
        heir.calculatedShare ?? '—',
        (heir.parentId && names.get(heir.parentId)) || '',
        heir.address ?? '',
      ].map(pdfText),
    ),
    styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: [42, 38, 34] },
    headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: STRIPE },
    columnStyles: {
      0: { cellWidth: 62 },
      1: { cellWidth: 30 },
      2: { cellWidth: 30 },
      3: { halign: 'right', cellWidth: 14 },
      4: { halign: 'right', cellWidth: 28 },
      5: { cellWidth: 45 },
      6: { cellWidth: 'auto' },
    },
    didParseCell: (data) => {
      if (data.section === 'head') {
        data.cell.styles.halign = data.column.index === 3 || data.column.index === 4 ? 'right' : 'left'
      } else if (data.column.index === 0) {
        const depth = rows[data.row.index].depth
        data.cell.styles.cellPadding = { top: 2, right: 2, bottom: 2, left: 2 + depth * CHILD_INDENT }
      }
    },
    didDrawPage: (data) => drawFooter(doc, data.pageNumber, now, t),
  })
  doc.putTotalPages(TOTAL_PAGES)
  doc.save(`attic-heirs-${isoDay(now)}.pdf`)
}

/** Years of the annual contribution on one page */
const YEARS_PER_PAGE = 10

/**
 * Download every year of the annual contribution, ten years per page, the oldest first: a line
 * per heir and a column per year. A dash marks a year in which the heir does not pay (not born
 * yet, deceased, or a parent still alive); an empty cell, one whose amount is not entered yet.
 */
export async function exportContributionsPdf(years: ContributionYear[], t: Messages) {
  const ordered = [...years].sort((a, b) => a.year - b.year)
  const doc = new jsPDF({ orientation: ordered.length > 5 ? 'landscape' : 'portrait', unit: 'mm', format: 'a4' })
  const now = new Date()

  // Everybody, in the order of the latest year, which knows every heir there is now
  const heirs = new Map<string, { name: string; deceased: boolean }>()
  for (const year of [...ordered].reverse()) {
    for (const line of year.lines) {
      if (!heirs.has(line.heirId)) {
        heirs.set(line.heirId, { name: line.heir, deceased: line.deceased })
      }
    }
  }
  // Whole amounts without cents: €200, but €150.50. Sums are rounded to cents first, as
  // 33.33 + 66.67 is not exactly 100 in floating point.
  const euros = (value: number) => {
    const cents = Math.round(value * 100)
    return cents % 100 === 0
      ? new Intl.NumberFormat(t.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(cents / 100)
      : formatEuros(cents / 100, t.locale)
  }

  for (let first = 0; first < ordered.length; first += YEARS_PER_PAGE) {
    const page = ordered.slice(first, first + YEARS_PER_PAGE)
    if (first > 0) {
      doc.addPage()
    }
    const range = page.length === 1 ? String(page[0].year) : `${page[0].year}–${page[page.length - 1].year}`
    drawTitle(doc, `${t.tabContributions} ${range}`, generatedOn(now, t))

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
      return [heir.deceased ? `${heir.name} †` : heir.name, ...cells, concerned ? euros(total) : '—'].map(pdfText)
    })
    const totals = [t.total, ...page.map((year) => euros(year.totalEur)),
      euros(page.reduce((sum, year) => sum + year.totalEur, 0))].map(pdfText)
    const last = page.length + 1

    autoTable(doc, {
      startY: 31,
      margin: { left: MARGIN, right: MARGIN, bottom: 18 },
      head: [[t.heirColumn, ...page.map((year) => String(year.year)), t.total].map(pdfText)],
      body,
      foot: [totals],
      showFoot: 'lastPage',
      styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: [42, 38, 34] },
      headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
      footStyles: { fillColor: STRIPE, textColor: [42, 38, 34], fontStyle: 'bold' },
      alternateRowStyles: { fillColor: STRIPE },
      didParseCell: (data) => {
        // Amounts and dashes to the right, the heir's total in bold
        if (data.column.index > 0) {
          data.cell.styles.halign = 'right'
        }
        if (data.column.index === last) {
          data.cell.styles.fontStyle = 'bold'
        }
      },
    })
  }

  // The footer once every page exists, so that each knows its number
  for (let number = 1; number <= doc.getNumberOfPages(); number++) {
    doc.setPage(number)
    drawFooter(doc, number, now, t)
  }
  doc.putTotalPages(TOTAL_PAGES)
  doc.save(`attic-contributions-${isoDay(now)}.pdf`)
}
