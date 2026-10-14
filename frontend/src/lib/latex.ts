import {
  catalogCells, catalogHead, catalogSubtitle, contributionPages, copyrightLine, generatedOn, heirCells, heirsHead,
  heirsSubtitle, inventorySubtitle, inventorySum, isoDay, type InventoryRoomExport,
} from './export'
import { formatEuros } from './format'
import type { Messages } from '../i18n'
import type { CatalogItem, ContributionYear, Heir } from '../api/types'

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

/** The line below a title, with room around its separators */
const subtitle = (text: string) => tex(text).replaceAll('  ·  ', '\\quad·\\quad ')

/** A column that takes what the others leave of the width of the page */
const rest = (fixedMm: number, columns: number) =>
  `\\dimexpr\\textwidth-${fixedMm}mm-${2 * columns}\\tabcolsep\\relax`

/** A whole LaTeX document around the given body */
function texDocument(body: string, landscape: boolean, now: Date, t: Messages): string {
  const language = LANGUAGES[t.locale.split('-')[0]] ?? 'british'
  const page = tex(t.page('@PAGE@', '@TOTAL@')).replace('@PAGE@', '\\thepage{}').replace('@TOTAL@', '\\pageref{LastPage}')
  return `% ${tex(generatedOn(now, t))}
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
\\usepackage{array,longtable,fancyhdr,lastpage}

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
\\newcommand{\\doctitle}[2]{{\\LARGE\\bfseries #1\\par}\\smallskip{\\small\\color{muted}#2\\par}\\medskip}

\\setlength{\\parindent}{0pt}
\\renewcommand{\\arraystretch}{1.35}
\\setlength{\\LTpre}{0pt}
\\setlength{\\LTleft}{0pt}
\\pagestyle{fancy}
\\fancyhf{}
\\renewcommand{\\headrulewidth}{0pt}
\\fancyfoot[L]{\\scriptsize\\color{muted}${tex(copyrightLine(now))}}
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

function save(source: string, fileName: string) {
  const url = URL.createObjectURL(new Blob([source], { type: 'application/x-tex;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** The catalog as LaTeX, like exportPdf: one line per item. `fileName` is without date and extension. */
export function exportCatalogLatex(items: CatalogItem[], totalCount: number, filters: string[], t: Messages,
  title: string, fileName: string) {
  const now = new Date()
  const columns = `>{\\ttfamily}L{20mm}L{${rest(186, 8)}}R{14mm}L{32mm}L{28mm}C{24mm}R{28mm}L{40mm}`
  const body = `\\doctitle{${tex(title)}}{${subtitle(catalogSubtitle(items, totalCount, filters, now, t))}}
${longTable(columns, catalogHead(t), items.map((item) => row(catalogCells(item, t).map(tex))), true)}`
  save(texDocument(body, true, now, t), `${fileName}-${isoDay(now)}.tex`)
}

/**
 * The inventory of a house as LaTeX, like exportInventoryPdf: room by room, each with its total,
 * then the total of the house and the missing items.
 */
export function exportInventoryLatex(rooms: InventoryRoomExport[], totalCount: number, filters: string[],
  t: Messages, house: string) {
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
  const body = `\\doctitle{${tex(`${t.pdfTitleInventory} — ${house}`)}}{${subtitle(inventorySubtitle(rooms, totalCount, filters, now, t))}}
${longTable(`R{14mm}L{${rest(46, 3)}}R{32mm}`, [t.quantityShort, t.description, t.value], rows, false)}`
  save(texDocument(body, false, now, t), `attic-inventory-${isoDay(now)}.tex`)
}

/** How far a child's name is set in from their parent's, in mm */
const CHILD_INDENT = 4

/** The heirs as LaTeX, like exportHeirsPdf: one line per heir, children set in under their parents. */
export function exportHeirsLatex(rows: { heir: Heir; depth: number }[], everybody: Heir[], filters: string[],
  t: Messages) {
  const now = new Date()
  const names = new Map(everybody.map((heir) => [heir.id, heir.name]))
  const lines = rows.map(({ heir, depth }) => {
    const [name, ...cells] = heirCells(heir, names, now, t).map(tex)
    return row([depth > 0 ? `\\hspace*{${depth * CHILD_INDENT}mm}${name}` : name, ...cells])
  })
  const body = `\\doctitle{${tex(t.tabHeirs)}}{${subtitle(heirsSubtitle(rows, everybody, filters, now, t))}}
${longTable(`L{62mm}L{30mm}L{30mm}R{14mm}R{28mm}L{45mm}L{${rest(209, 7)}}`, heirsHead(t), lines, true)}`
  save(texDocument(body, true, now, t), `attic-heirs-${isoDay(now)}.tex`)
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
    return `\\doctitle{${tex(page.title)}}{${subtitle(generatedOn(now, t))}}
${longTable(columns, page.head, rows, true)}`
  })
  const body = pages.length > 0 ? pages.join('\\newpage\n') : `\\doctitle{${tex(t.tabContributions)}}{${subtitle(generatedOn(now, t))}}`
  save(texDocument(body, years.length > 5, now, t), `attic-contributions-${isoDay(now)}.tex`)
}
