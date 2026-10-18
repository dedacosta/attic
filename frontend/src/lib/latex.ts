import {
  catalogCells, catalogHead, catalogNote, contributionPages, copyrightLine, generatedOn, heirCells, heirsHead,
  heirsNote, inventoryNote, inventorySum, isoDay, renovationExport, type CatalogItemExport, type InventoryRoomExport,
} from './export'
import { formatEuros } from './format'
import { THUMBNAIL_SIZE, loadThumbnails } from './thumbnails'
import { zip } from './zip'
import type { Messages } from '../i18n'
import type { ContributionYear, Heir, Renovation } from '../api/types'

/*
 * The exports as LaTeX sources, next to the PDFs: the same tables, colours and footer, to be
 * changed and compiled with pdflatex, lualatex or xelatex (twice, for "page 1 of 3").
 */

/** Babel's name of each language of Attic */
const LANGUAGES: Record<string, string> = { pt: 'portuguese', en: 'british', fr: 'french' }

const ESCAPES: Record<string, string> = {
  '\\': '\\textbackslash{}', '{': '\\{', '}': '\\}', '$': '\\$', '&': '\\&', '#': '\\#', '%': '\\%', '_': '\\_',
  '^': '\\textasciicircum{}', '~': '\\textasciitilde{}', '\u00a0': '~', '\u202f': '\\,', '\n': ' ', '\r': '',
}

/** Text as LaTeX, its special characters escaped */
const tex = (text: string) => text.replace(/[\\{}$&#%_^~\u00a0\u202f\r\n]/g, (c) => ESCAPES[c])

/** Parts of a text joined by "  ·  ", as in the footer, with room around the separators */
const footerNote = (text: string) => tex(text).replaceAll('  ·  ', '\\quad·\\quad ')

/** The title at the top of the first page, and below it what the document is about, as in the PDFs */
const titleBlock = (title: string, subject: string[]) => `{\\fontsize{18}{22}\\selectfont\\bfseries ${tex(title)}\\par}
${subject.length > 0 ? `\\vspace{1.5mm}{\\small\\color{muted}${footerNote(subject.join('  ·  '))}\\par}\n` : ''}\\vspace{4mm}
`

/** A column that takes what the others leave of the width of the page */
const rest = (fixedMm: number, columns: number, also = '') =>
  `\\dimexpr\\textwidth-${fixedMm}mm${also && `-${also}`}-${2 * columns}\\tabcolsep\\relax`

/**
 * A whole LaTeX document around the given body, the note (when, how many…) in the footer after the
 * copyright. The title is also in the comment at the top.
 */
function texDocument(title: string, body: string, note: string, landscape: boolean, now: Date, t: Messages): string {
  const language = LANGUAGES[t.locale.split('-')[0]] ?? 'british'
  const page = tex(t.page('@PAGE@', '@TOTAL@')).replace('@PAGE@', '\\thepage{}').replace('@TOTAL@', '\\pageref{LastPage}')
  return `% ${tex(title)}
% ${tex(generatedOn(now, t))}
% ${tex(copyrightLine(now))}
% Compile it twice with pdflatex, lualatex or xelatex.
\\documentclass[10pt]{article}
\\usepackage[a4paper,${landscape ? 'landscape,' : ''}margin=14mm,bottom=20mm,footskip=10mm]{geometry}
\\usepackage{iftex}
\\ifPDFTeX
  \\usepackage[T1]{fontenc}
  \\usepackage[utf8]{inputenc}
  \\usepackage{textcomp}
  \\usepackage{helvet}
\\else
  \\usepackage{fontspec}
  \\setsansfont{TeX Gyre Heros}
\\fi
\\renewcommand{\\familydefault}{\\sfdefault}
\\usepackage[${language}]{babel}
\\usepackage[table]{xcolor}
\\usepackage{array,longtable,fancyhdr,lastpage,graphicx,needspace}

\\definecolor{accent}{RGB}{181,101,29}
\\definecolor{muted}{RGB}{115,106,96}
\\definecolor{stripe}{RGB}{246,243,238}
\\definecolor{ink}{RGB}{42,38,34}

\\newcolumntype{L}[1]{>{\\raggedright\\arraybackslash}p{#1}}
\\newcolumntype{R}[1]{>{\\raggedleft\\arraybackslash}p{#1}}
\\newcolumntype{C}[1]{>{\\centering\\arraybackslash}p{#1}}
% A header cell: white and bold on the accent colour. \\leavevmode, or \\color would start a
% wrapping cell with an empty line
\\newcommand{\\head}[1]{\\leavevmode{\\normalfont\\bfseries\\color{white}#1}}

\\setlength{\\parindent}{0pt}
\\renewcommand{\\arraystretch}{1.35}
\\setlength{\\LTpre}{0pt}
\\setlength{\\LTleft}{0pt}
\\pagestyle{fancy}
\\fancyhf{}
\\renewcommand{\\headrulewidth}{0pt}
% A note too long for one line goes on over more, clear of the page number
\\fancyfoot[L]{\\parbox[b]{\\dimexpr\\textwidth-30mm\\relax}{\\raggedright\\scriptsize\\color{muted}${tex(copyrightLine(now))}\\quad·\\quad ${footerNote(note)}}}
\\fancyfoot[R]{\\scriptsize\\color{muted}${page}}

\\begin{document}
\\color{ink}
${body}
\\end{document}
`
}

/** A table that goes on over several pages, its header repeated on each, in a smaller font */
function longTable(columns: string, head: string[], rows: string[], stripes: boolean): string {
  return `\\begingroup
\\small
${stripes ? '\\rowcolors{2}{white}{stripe}\n' : ''}\\begin{longtable}{${columns}}
\\rowcolor{accent}${head.map((cell) => `\\head{${tex(cell)}}`).join(' & ')} \\\\
\\endhead
${rows.join('\n')}
\\end{longtable}
\\endgroup
`
}

const row = (cells: string[]) => `${cells.join(' & ')} \\\\`

function save(source: string | Blob, fileName: string) {
  const blob = typeof source === 'string' ? new Blob([source], { type: 'application/x-tex;charset=utf-8' }) : source
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/**
 * The catalog as LaTeX, like exportPdf: the title, then one line per item with its cover photo small
 * next to its ID. With photos it is a ZIP of the source and a folder of the photos, to be unpacked
 * and compiled there. `fileName` is without date and extension.
 */
export async function exportCatalogLatex(items: CatalogItemExport[], totalCount: number, filters: string[], t: Messages,
  title: string, fileName: string) {
  const now = new Date()
  const thumbnails = await loadThumbnails(items)
  const photos = thumbnails.map((thumbnail, index) =>
    thumbnail && { name: `photos/${String(index + 1).padStart(4, '0')}.jpg`, dataUrl: thumbnail })
  const withPhotos = photos.some(Boolean)
  // With photos the lines are higher, their texts in the middle, as in the PDF
  const column = (align: string, width: string) =>
    `>{\\${align}\\arraybackslash}${withPhotos ? 'm' : 'p'}{${width}}`
  const idWidth = withPhotos ? 20 + THUMBNAIL_SIZE + 2 : 20
  const columns = [
    `>{\\ttfamily}${column('raggedright', `${idWidth}mm`)}`, column('raggedright', rest(166 + idWidth, 8)),
    column('raggedleft', '14mm'), column('raggedright', '32mm'), column('raggedright', '28mm'), column('centering', '24mm'),
    column('raggedleft', '28mm'), column('raggedright', '40mm'),
  ].join('')
  const rows = items.map((item, index) => {
    const [id, ...cells] = catalogCells(item, t).map(tex)
    const photo = photos[index]
    // The photo left of the ID, lowered so the ID is at its middle. A strut keeps every line as high
    // as in the PDF, 1 mm clear of the photo above and below; without a photo the room is left empty.
    const drop = THUMBNAIL_SIZE / 2 - 0.7
    const strut = `\\rule[-${drop + 1}mm]{0pt}{${THUMBNAIL_SIZE + 2}mm}`
    const picture = !withPhotos ? '' : photo
      ? `${strut}\\raisebox{-${drop}mm}{\\includegraphics[width=${THUMBNAIL_SIZE}mm,height=${THUMBNAIL_SIZE}mm]{${photo.name}}}\\hspace{2mm}`
      : `${strut}\\hspace{${THUMBNAIL_SIZE + 2}mm}`
    return row([`${picture}${id}`, ...cells])
  })
  const body = titleBlock(title, []) + longTable(columns, catalogHead(t), rows, true)
  const source = texDocument(title, body, catalogNote(items, totalCount, filters, now, t), true, now, t)
  const name = `${fileName}-${isoDay(now)}`
  if (!withPhotos) {
    save(source, `${name}.tex`)
    return
  }
  const bytes = (dataUrl: string) => Uint8Array.from(atob(dataUrl.split(',')[1]), (c) => c.charCodeAt(0))
  save(zip([
    { name: `${name}/${name}.tex`, data: new TextEncoder().encode(source) },
    ...photos.flatMap((photo) => (photo ? [{ name: `${name}/${photo.name}`, data: bytes(photo.dataUrl) }] : [])),
  ], now), `${name}.zip`)
}

/**
 * The inventory of a house as LaTeX, like exportInventoryPdf: room by room, each with its total,
 * then the total of the house and the missing items, below the title and the house with its address.
 */
export function exportInventoryLatex(rooms: InventoryRoomExport[], totalCount: number, filters: string[],
  t: Messages, house: string, address: string | null) {
  const now = new Date()
  const euros = (value: number) => tex(formatEuros(value, t.locale))
  const across = (content: string) => `\\multicolumn{3}{L{${rest(0, 1)}}}{${content}} \\\\`
  const section = ({ title, comments, items }: InventoryRoomExport) => [
    `\\noalign{\\vspace{2mm}}${across(`\\leavevmode\\normalsize\\bfseries\\color{accent}${tex(title)}`)}`,
    ...(comments ? [across(`\\leavevmode\\color{muted}${tex(comments)}`)] : []),
    ...items.map((item) => row([String(item.quantity), tex(item.description), euros(item.valueEur)])),
    // The amount alone: under the values of a room it is clearly their total
    row(['\\multicolumn{2}{r}{}', `\\bfseries ${euros(inventorySum(items))}`]),
  ]
  const held = rooms.filter((room) => !room.missing)
  const missing = rooms.filter((room) => room.missing)
  const rows = [
    ...held.flatMap(section),
    ...(held.length > 0
      ? [`\\rowcolor{stripe}${row([`\\multicolumn{2}{r}{\\normalsize\\bfseries ${tex(t.total)}}`,
        `\\normalsize\\bfseries ${euros(inventorySum(held.flatMap((room) => room.items)))}`])}`]
      : []),
    ...missing.flatMap(section),
  ]
  // What the inventory is of: the house and where it is
  const body = titleBlock(t.pdfTitleInventory, [house, address ?? ''].filter(Boolean))
    + longTable(`R{14mm}L{${rest(46, 3)}}R{32mm}`, [t.quantityShort, t.description, t.value], rows, false)
  save(texDocument(house ? `${t.pdfTitleInventory} — ${house}` : t.pdfTitleInventory, body,
    inventoryNote(rooms, totalCount, filters, now, t), false, now, t),
    `attic-inventory-${isoDay(now)}.tex`)
}

/** How far a child's name is set in from their parent's, in mm */
const CHILD_INDENT = 4

/** The heirs as LaTeX, like exportHeirsPdf: one line per heir, children set in under their parents. */
export function exportHeirsLatex(rows: { heir: Heir; depth: number }[], everybody: Heir[], filters: string[],
  t: Messages) {
  const now = new Date()
  const names = new Map(everybody.map((heir) => [heir.id, heir.name]))
  const cells = rows.map(({ heir, depth }) => {
    const [name, ...others] = heirCells(heir, names, now, t).map(tex)
    return [depth > 0 ? `\\hspace*{${depth * CHILD_INDENT}mm}${name}` : name, ...others]
  })
  const lines = cells.map(row)
  // The name column is as wide as the longest name, measured in the table's font, so that every
  // name stays on one line; the address takes what is left
  const widest = [`\\textbf{${tex(t.name)}}`, ...cells.map(([name]) => name)]
    .map((name) => `\\settowidth{\\heircell}{${name}}\\ifdim\\heircell>\\namewidth\\setlength{\\namewidth}{\\heircell}\\fi`)
  const body = `\\newlength{\\namewidth}\\newlength{\\heircell}
{\\small
${widest.join('\n')}
\\global\\namewidth=\\namewidth}
${longTable(`L{\\namewidth}L{30mm}L{30mm}R{14mm}R{28mm}L{45mm}L{${rest(147, 7, '\\namewidth')}}`, heirsHead(t), lines, true)}`
  save(texDocument(t.tabHeirs, body, heirsNote(rows, everybody, filters, now, t), true, now, t), `attic-heirs-${isoDay(now)}.tex`)
}

/** Every year of the annual contribution as LaTeX, like exportContributionsPdf: ten years per page. */
export function exportContributionsLatex(years: ContributionYear[], t: Messages) {
  const now = new Date()
  const pages = contributionPages(years, t).map((page) => {
    // The heir's total in bold, and the totals of the years in bold on a stripe
    const columns = `L{45mm}${'r'.repeat(page.head.length - 2)}>{\\bfseries}r`
    const rows = [
      ...page.body.map((cells) => row(cells.map(tex))),
      `\\rowcolor{stripe}${row(page.totals.map((cell) => `\\bfseries ${tex(cell)}`))}`,
    ]
    return longTable(columns, page.head, rows, true)
  })
  // Without any year, an empty page all the same, with its footer
  const body = pages.length > 0 ? pages.join('\\newpage\n') : '\\mbox{}'
  save(texDocument(t.tabContributions, body, generatedOn(now, t), years.length > 5, now, t), `attic-contributions-${isoDay(now)}.tex`)
}

/**
 * Every renovation as LaTeX, like exportRenovationsPdf: under its year and title, its expenses with
 * their total, then the heirs with their part and whether they paid it or did workmanship.
 */
export function exportRenovationsLatex(renovations: Renovation[], t: Messages) {
  const now = new Date()
  // The same widths as in the PDF: 34, 20 and 34 mm, the first column takes what is left
  const columns = (right: number[]) => ['L', 'L', 'L', 'L']
    .map((align, index) => (right.includes(index) ? 'R' : align))
    .map((align, index) => `${align}{${index === 0 ? rest(88, 4) : ['34mm', '20mm', '34mm'][index - 1]}}`)
    .join('')
  const muted = (text: string) => `{\\small\\color{muted}${tex(text)}\\par}\\vspace{1.5mm}\n`
  const sections = renovations.map((renovation) => {
    const parts = renovationExport(renovation, t)
    const expenses = parts.expenses.length > 0
      ? longTable(columns([3]), parts.expensesHead, [
        ...parts.expenses.map((cells) => row(cells.map(tex))),
        `\\rowcolor{stripe}${row(parts.cost.map((cell) => (cell ? `\\bfseries ${tex(cell)}` : '')))}`,
      ], true)
      : muted(t.noExpenses)
    const heirs = parts.heirs.length > 0
      ? longTable(columns([1]), parts.heirsHead, [
        ...parts.heirs.map((cells) => row(cells.map(tex))),
        `\\rowcolor{stripe}\\multicolumn{4}{r}{\\bfseries ${footerNote(parts.summary)}} \\\\`,
      ], true)
      : muted(t.nobodyPays)
    return `\\vspace{4mm}\\Needspace{50mm}
{\\fontsize{13}{16}\\selectfont\\bfseries\\color{accent}${tex(parts.heading)}\\par}\\nopagebreak\\vspace{2mm}
${parts.description ? muted(parts.description) : ''}${expenses}\\vspace{2mm}
${heirs}`
  })
  const body = titleBlock(t.tabRenovations, []) + (sections.length > 0 ? sections.join('\n') : '\\mbox{}')
  save(texDocument(t.tabRenovations, body, generatedOn(now, t), false, now, t), `attic-renovations-${isoDay(now)}.tex`)
}
