import { jsPDF } from 'jspdf'
import { autoTable, type RowInput, type Styles } from 'jspdf-autotable'
import {
  catalogCells, catalogHead, catalogNote, contributionPages, copyrightLine, generatedOn, heirCells, heirsHead,
  heirsNote, inventoryNote, inventorySum, isoDay, renovationExport, type CatalogItemExport, type InventoryRoomExport,
} from './export'
import { formatEuros } from './format'
import { THUMBNAIL_SIZE, loadThumbnails } from './thumbnails'
import type { Messages } from '../i18n'
import type { ContributionYear, Heir, Renovation } from '../api/types'

const ACCENT: [number, number, number] = [181, 101, 29]
const MUTED: [number, number, number] = [115, 106, 96]
const STRIPE: [number, number, number] = [246, 243, 238]
const TEXT: [number, number, number] = [42, 38, 34]
const MARGIN = 14
const TOTAL_PAGES = '{total_pages}'

/** The built-in PDF fonts have no narrow or no-break spaces, which Intl uses in "1 234,50 €". */
const pdfText = (text: string) => text.replace(/[  ]/g, ' ')

/** A PDF made in the browser, to show before it is downloaded */
export interface GeneratedPdf {
  blob: Blob
  /** Shown above the pages, e.g. "Heirs" */
  title: string
  /** e.g. "attic-heirs-2026-10-17.pdf" */
  fileName: string
}

/** The PDF, once its pages are all drawn */
function finish(doc: jsPDF, title: string, fileName: string): GeneratedPdf {
  doc.putTotalPages(TOTAL_PAGES)
  return { blob: doc.output('blob'), title, fileName }
}

/** The title of the document, for the viewer's title bar */
const setTitle = (doc: jsPDF, title: string) => doc.setProperties({ title })

/** Where the table starts, at the top of the page */
const TABLE_TOP = MARGIN

/**
 * The title at the top of the first page, and below it what the document is about, e.g. the house.
 * Returns where the table starts below them.
 */
function drawTitle(doc: jsPDF, title: string, subject: string[]): number {
  doc.setTextColor(...TEXT)
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(18)
  doc.text(pdfText(title), MARGIN, 20)
  if (subject.length === 0) {
    return 27
  }
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(10)
  doc.setTextColor(...MUTED)
  doc.text(pdfText(subject.join('  ·  ')), MARGIN, 26)
  return 32
}

/** Between the lines of the footer, in mm */
const FOOTER_LINE_HEIGHT = 3.5

/** Room kept for the page number, right of the rest of the footer, in mm */
const PAGE_NUMBER_WIDTH = 30

/**
 * Copyright, Attic's version, the note (when, how many…) and the page number, at the bottom of
 * every page. A note too long for one line goes on over more.
 */
function drawFooter(doc: jsPDF, pageNumber: number, note: string, now: Date, t: Messages) {
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...MUTED)
  const lines: string[] = doc.splitTextToSize(pdfText(`${copyrightLine(now)}  ·  ${note}`),
    pageWidth - 2 * MARGIN - PAGE_NUMBER_WIDTH)
  // The last line where a footer of one line is, next to the page number, the others above it
  const top = pageHeight - 8 - (lines.length - 1) * FOOTER_LINE_HEIGHT
  doc.text(lines, MARGIN, top, { lineHeightFactor: FOOTER_LINE_HEIGHT / (8 * 25.4 / 72) })
  doc.text(pdfText(t.page(pageNumber, TOTAL_PAGES)), pageWidth - MARGIN, pageHeight - 8, { align: 'right' })
}

/**
 * A PDF table of the given items under a title, one line per item with its cover photo small next to its ID, to
 * show and download. Comments are left out.
 * `fileName` is without date and extension, e.g. "attic-catalog".
 */
export async function exportPdf(items: CatalogItemExport[], totalCount: number, filters: string[], t: Messages, title: string,
  fileName: string): Promise<GeneratedPdf> {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const now = new Date()
  const note = catalogNote(items, totalCount, filters, now, t)
  setTitle(doc, title)
  const top = drawTitle(doc, title, [])
  // The cover of each item, small, next to its ID
  const thumbnails = await loadThumbnails(items)
  const withPhotos = thumbnails.some((thumbnail) => thumbnail !== null)

  autoTable(doc, {
    startY: top,
    margin: { top: MARGIN, left: MARGIN, right: MARGIN, bottom: 18 },
    head: [catalogHead(t)],
    body: items.map((item) => catalogCells(item, t).map(pdfText)),
    styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: TEXT },
    headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: STRIPE },
    columnStyles: {
      0: { font: 'courier', cellWidth: withPhotos ? 20 + THUMBNAIL_SIZE + 2 : 20 },
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
      } else if (withPhotos) {
        // Lines high enough for the photos, their texts in the middle, and room for the photo left of the ID
        data.cell.styles.minCellHeight = THUMBNAIL_SIZE + 2
        data.cell.styles.valign = 'middle'
        if (data.column.index === 0) {
          data.cell.styles.cellPadding = { top: 2, right: 2, bottom: 2, left: 2 + THUMBNAIL_SIZE + 2 }
        }
      }
    },
    didDrawCell: (data) => {
      const thumbnail = data.section === 'body' && data.column.index === 0 ? thumbnails[data.row.index] : null
      if (thumbnail) {
        doc.addImage(thumbnail, 'JPEG', data.cell.x + 2, data.cell.y + (data.cell.height - THUMBNAIL_SIZE) / 2,
          THUMBNAIL_SIZE, THUMBNAIL_SIZE)
      }
    },
    didDrawPage: (data) => drawFooter(doc, data.pageNumber, note, now, t),
  })
  return finish(doc, title, `${fileName}-${isoDay(now)}.pdf`)
}

/**
 * The inventory of a house as a PDF, to show and download, room by room: a line with the title of the room, its
 * comments and, below, a line per item with its quantity, its description and its value, then the
 * total of the room, as an amount without a label. The total of the house follows what the house holds; the missing items come
 * after it, with a total of their own. The first page has the title and the house with its address. Photos are left out.
 */
export async function exportInventoryPdf(rooms: InventoryRoomExport[], totalCount: number, filters: string[],
  t: Messages, house: string, address: string | null): Promise<GeneratedPdf> {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const now = new Date()
  const note = inventoryNote(rooms, totalCount, filters, now, t)
  const title = house ? `${t.pdfTitleInventory} — ${house}` : t.pdfTitleInventory
  setTitle(doc, title)
  // What the inventory is of: the house and where it is
  const top = drawTitle(doc, t.pdfTitleInventory, [house, address ?? ''].filter(Boolean))

  const euros = (value: number) => pdfText(formatEuros(value, t.locale))

  // A line across the table, above the items of a room
  const across = (content: string, styles: Partial<Styles>): RowInput =>
    [{ content: pdfText(content), colSpan: 3, styles: { fillColor: 255, ...styles } }]
  // A total: its label under the descriptions, its amount under the values
  const totalLine = (label: string, value: number, styles: Partial<Styles>): RowInput => [
    { content: pdfText(label), colSpan: 2, styles: { fillColor: 255, fontStyle: 'bold', halign: 'right', ...styles } },
    { content: euros(value), styles: { fillColor: 255, fontStyle: 'bold', ...styles } },
  ]
  const section = ({ title, comments, items }: (typeof rooms)[number]): RowInput[] => [
    across(title, { fontStyle: 'bold', fontSize: 11, textColor: ACCENT, cellPadding: { top: 5, right: 2, bottom: comments ? 0.5 : 2, left: 2 } }),
    ...(comments ? [across(comments, { textColor: MUTED, cellPadding: { top: 0.5, right: 2, bottom: 2, left: 2 } })] : []),
    ...items.map((item) => [String(item.quantity), pdfText(item.description), euros(item.valueEur)]),
    // The amount alone: under the values of a room it is clearly their total
    totalLine('', inventorySum(items), {}),
  ]
  const held = rooms.filter((room) => !room.missing)
  const missing = rooms.filter((room) => room.missing)

  autoTable(doc, {
    startY: top,
    margin: { top: MARGIN, left: MARGIN, right: MARGIN, bottom: 18 },
    head: [[t.quantityShort, t.description, t.value]],
    body: [
      ...held.flatMap(section),
      ...(held.length > 0
        ? [totalLine(t.total, inventorySum(held.flatMap((room) => room.items)),
          { fontSize: 11, fillColor: STRIPE, cellPadding: { top: 3, right: 2, bottom: 3, left: 2 } })]
        : []),
      ...missing.flatMap(section),
    ],
    styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: TEXT },
    headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
    columnStyles: {
      0: { halign: 'right', cellWidth: 14 },
      1: { cellWidth: 'auto' },
      2: { halign: 'right', cellWidth: 32 },
    },
    didParseCell: (data) => {
      // Headers follow their column's alignment: quantity and value right
      if (data.section === 'head') {
        data.cell.styles.halign = data.column.index === 1 ? 'left' : 'right'
      } else if (data.cell.colSpan === 3) {
        // The lines across the table start at the left
        data.cell.styles.halign = 'left'
      }
    },
    didDrawPage: (data) => drawFooter(doc, data.pageNumber, note, now, t),
  })
  return finish(doc, title, `attic-inventory-${isoDay(now)}.pdf`)
}

/** How far a child's name is set in from their parent's, in mm */
const CHILD_INDENT = 4

/**
 * A PDF table of the given heirs, to show and download, one line per heir, in the order given. `depth` is how
 * many ancestors are listed above the heir, whose name is set in by as much. `everybody` is for
 * the names of the parents. Sex, filiation, comments and documents are left out.
 */
export async function exportHeirsPdf(rows: { heir: Heir; depth: number }[], everybody: Heir[], filters: string[],
  t: Messages): Promise<GeneratedPdf> {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const now = new Date()
  const note = heirsNote(rows, everybody, filters, now, t)
  setTitle(doc, t.tabHeirs)
  const names = new Map(everybody.map((heir) => [heir.id, heir.name]))

  autoTable(doc, {
    startY: TABLE_TOP,
    margin: { left: MARGIN, right: MARGIN, bottom: 18 },
    head: [heirsHead(t)],
    body: rows.map(({ heir }) => heirCells(heir, names, now, t).map(pdfText)),
    styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: TEXT },
    headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: STRIPE },
    columnStyles: {
      // As wide as the longest name, which stays on one line; the address takes what is left
      0: { cellWidth: 'wrap' },
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
    didDrawPage: (data) => drawFooter(doc, data.pageNumber, note, now, t),
  })
  return finish(doc, t.tabHeirs, `attic-heirs-${isoDay(now)}.pdf`)
}

/**
 * A PDF of every year of the annual contribution, to show and download, ten years per page, the oldest first: a line
 * per heir and a column per year (see contributionPages).
 */
export async function exportContributionsPdf(years: ContributionYear[], t: Messages): Promise<GeneratedPdf> {
  const doc = new jsPDF({ orientation: years.length > 5 ? 'landscape' : 'portrait', unit: 'mm', format: 'a4' })
  const now = new Date()
  setTitle(doc, t.tabContributions)

  contributionPages(years, t).forEach((page, index) => {
    if (index > 0) {
      doc.addPage()
    }
    const last = page.head.length - 1

    autoTable(doc, {
      startY: TABLE_TOP,
      margin: { left: MARGIN, right: MARGIN, bottom: 18 },
      head: [page.head.map(pdfText)],
      body: page.body.map((row) => row.map(pdfText)),
      foot: [page.totals.map(pdfText)],
      showFoot: 'lastPage',
      styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: TEXT },
      headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
      footStyles: { fillColor: STRIPE, textColor: TEXT, fontStyle: 'bold' },
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
  })

  // The footer once every page exists, so that each knows its number
  for (let number = 1; number <= doc.getNumberOfPages(); number++) {
    doc.setPage(number)
    drawFooter(doc, number, generatedOn(now, t), now, t)
  }
  return finish(doc, t.tabContributions, `attic-contributions-${isoDay(now)}.pdf`)
}

/** Widths of the narrow columns of the tables of a renovation, in mm; the first takes what is left */
const RENOVATION_COLUMNS = [34, 20, 34]

/**
 * A PDF of every renovation, to show and download, the latest first: under its year and title, its
 * expenses with their total, then the heirs with their part and whether they paid it or did
 * workmanship. Comments are left out.
 */
export async function exportRenovationsPdf(renovations: Renovation[], t: Messages): Promise<GeneratedPdf> {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const now = new Date()
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  const width = pageWidth - 2 * MARGIN
  setTitle(doc, t.tabRenovations)
  let y = drawTitle(doc, t.tabRenovations, [])
  const tableEnd = () => (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY

  /** A text in the muted colour, over as many lines as it takes */
  function muted(text: string, size: number) {
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(size)
    doc.setTextColor(...MUTED)
    const lines: string[] = doc.splitTextToSize(pdfText(text), width)
    doc.text(lines, MARGIN, y + size * 0.3)
    y += lines.length * size * 0.42 + 1.5
  }

  /** A table of the renovation, the given columns to the right */
  function table(head: string[], body: string[][], foot: RowInput, right: number[]) {
    autoTable(doc, {
      startY: y,
      margin: { top: MARGIN, left: MARGIN, right: MARGIN, bottom: 18 },
      head: [head.map(pdfText)],
      body: body.map((row) => row.map(pdfText)),
      foot: [foot],
      showFoot: 'lastPage',
      styles: { font: 'helvetica', fontSize: 9, cellPadding: 2, overflow: 'linebreak', textColor: TEXT },
      headStyles: { fillColor: ACCENT, textColor: 255, fontStyle: 'bold' },
      footStyles: { fillColor: STRIPE, textColor: TEXT, fontStyle: 'bold' },
      alternateRowStyles: { fillColor: STRIPE },
      columnStyles: Object.fromEntries(RENOVATION_COLUMNS.map((cellWidth, index) => [index + 1, { cellWidth }])),
      didParseCell: (data) => {
        if (right.includes(data.column.index)) {
          data.cell.styles.halign = 'right'
        }
      },
    })
    y = tableEnd() + 4
  }

  renovations.forEach((renovation, index) => {
    const parts = renovationExport(renovation, t)
    // A renovation starts on a new page when its heading and a few lines would not fit
    if (index > 0) {
      y += 4
    }
    if (y > pageHeight - 70) {
      doc.addPage()
      y = MARGIN + 2
    }
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(13)
    doc.setTextColor(...ACCENT)
    doc.text(pdfText(parts.heading), MARGIN, y + 4)
    y += 8
    if (parts.description) {
      muted(parts.description, 9)
    }
    y += 1
    if (parts.expenses.length > 0) {
      table(parts.expensesHead, parts.expenses, parts.cost.map(pdfText), [3])
    } else {
      muted(t.noExpenses, 9)
    }
    if (parts.heirs.length > 0) {
      table(parts.heirsHead, parts.heirs,
        [{ content: pdfText(parts.summary), colSpan: 4, styles: { halign: 'right' } }], [1])
    } else {
      muted(t.nobodyPays, 9)
    }
  })

  // The footer once every page exists, so that each knows its number
  for (let number = 1; number <= doc.getNumberOfPages(); number++) {
    doc.setPage(number)
    drawFooter(doc, number, generatedOn(now, t), now, t)
  }
  return finish(doc, t.tabRenovations, `attic-renovations-${isoDay(now)}.pdf`)
}
