import { jsPDF } from 'jspdf'
import { autoTable } from 'jspdf-autotable'
import { copyrightYears, formatDate, formatEuros, locationLabel, shortId } from './format'
import type { Messages } from '../i18n'
import type { Item } from '../api/types'

const ACCENT: [number, number, number] = [181, 101, 29]
const MUTED: [number, number, number] = [115, 106, 96]
const STRIPE: [number, number, number] = [246, 243, 238]
const MARGIN = 14
const TOTAL_PAGES = '{total_pages}'

/** The built-in PDF fonts have no narrow or no-break spaces, which Intl uses in "1 234,50 €". */
const pdfText = (text: string) => text.replace(/[  ]/g, ' ')

/**
 * Download the given items as a PDF table, one line per item. Comments are left out.
 */
export async function exportPdf(items: Item[], totalCount: number, filters: string[], t: Messages) {
  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const now = new Date()
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(18)
  doc.text(pdfText(`Attic — ${t.pdfTitle}`), MARGIN, 18)

  doc.setFont('helvetica', 'normal')
  doc.setFontSize(10)
  doc.setTextColor(...MUTED)
  const generated = t.pdfGenerated(now.toLocaleString(t.locale, { dateStyle: 'long', timeStyle: 'short' }))
  const count = items.length === totalCount ? t.itemCount(totalCount) : t.filteredCount(items.length, totalCount)
  doc.text(pdfText([generated, count, ...filters].join('  ·  ')), MARGIN, 25)

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
    didDrawPage: (data) => {
      doc.setFont('helvetica', 'normal')
      doc.setFontSize(8)
      doc.setTextColor(...MUTED)
      const footer = `© ${copyrightYears(now)} David Da Costa · ${t.madeWith} · v${__APP_VERSION__}`
      doc.text(pdfText(footer), MARGIN, pageHeight - 8)
      doc.text(pdfText(t.page(data.pageNumber, TOTAL_PAGES)), pageWidth - MARGIN, pageHeight - 8, { align: 'right' })
    },
  })
  doc.putTotalPages(TOTAL_PAGES)

  const day = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  doc.save(`attic-inventory-${day}.pdf`)
}
