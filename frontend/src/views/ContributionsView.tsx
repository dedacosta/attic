import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import ConfirmDialog from '../components/ConfirmDialog'
import { ChevronLeftIcon, ChevronRightIcon, FileIcon, PencilIcon } from '../components/icons'
import { AMOUNT_PATTERN, amountText, formatEuros, parseAmount } from '../lib/format'
import { useModal } from '../lib/useModal'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { ContributionYear } from '../api/types'

/** Years shown at a time, and moved by an arrow */
const SPAN = 10

type Line = ContributionYear['lines'][number]

/**
 * The yearly amount: what most payers gave for a whole portion, so that two children paying
 * 100 € each for their half count as 200 €. Null when no amount is more frequent than the others
 * (e.g. nothing entered yet, or everybody gave something different).
 */
function yearlyAmount(year: ContributionYear): number | null {
  const counts = new Map<number, number>()
  for (const line of year.lines) {
    if (line.amountEur !== null && line.portion) {
      const whole = Math.round(line.amountEur / line.portion * 100) / 100
      counts.set(whole, (counts.get(whole) ?? 0) + 1)
    }
  }
  const sorted = [...counts].sort((a, b) => b[1] - a[1])
  if (sorted.length === 0 || (sorted.length > 1 && sorted[0][1] === sorted[1][1])) {
    return null
  }
  return sorted[0][0]
}

/** Somebody has a cell in a year when they pay then, or an amount was entered all the same */
const hasCell = (line: Line | undefined): line is Line => line !== undefined && (line.pays || line.amountEur !== null)

/**
 * The Contributions tab: a grid with a line per heir and a column per year, ten years at a time,
 * the current year on the right at first; the arrows go back and ahead. Everybody sees it; administrators enter the amounts,
 * and the first amount of a year adds that year.
 */
export default function ContributionsView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const currentYear = new Date().getFullYear()
  // The year of the rightmost column
  const [end, setEnd] = useState(currentYear)
  const [years, setYears] = useState<ContributionYear[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [error, setError] = useState<string | null>(null)
  const [commenting, setCommenting] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<number | null>(null)
  const start = end - SPAN + 1

  const reload = useCallback(async () => {
    try {
      setYears(await api.listContributionRange(end - SPAN + 1, end))
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [end])

  useEffect(() => {
    reload()
  }, [reload])

  // On a narrow screen the grid scrolls sideways: show the latest years first, as on a wide one
  const scroller = useRef<HTMLDivElement>(null)
  const loadedRange = years === null ? null : years[0]?.year
  useEffect(() => {
    if (scroller.current) {
      scroller.current.scrollLeft = scroller.current.scrollWidth
    }
  }, [loadedRange])

  function move(by: number) {
    setError(null)
    setEnd((current) => Math.min(2999, Math.max(1900 + SPAN - 1, current + by)))
  }

  async function downloadPdf() {
    // Loaded on demand: the PDF library is large. The PDF has every year with something entered
    const [{ exportContributionsPdf }, all] = await Promise.all([import('../lib/pdf'), api.listContributions()])
    await exportContributionsPdf(all, t)
  }

  /** Show a year as the server answered after a change */
  const replace = (changed: ContributionYear) =>
    setYears((current) => current?.map((year) => (year.year === changed.year ? changed : year)) ?? null)

  /** Save an amount when it changed; an empty field removes it */
  async function save(year: number, heirId: string, text: string, previous: number | null) {
    const value = text.trim() === '' ? null : text.trim()
    if (value !== null && !AMOUNT_PATTERN.test(value)) {
      return setError(t.errorAmount)
    }
    const amount = value === null ? null : parseAmount(value)
    setError(null)
    if (amount === previous) {
      return
    }
    try {
      replace(await api.setContribution(year, heirId, amount))
    } catch (e) {
      setError(apiErrorMessage(e, t))
    }
  }

  // Every year lists every heir in the same order; a line of the grid for those with a cell in it
  const heirs = (years?.[0]?.lines ?? []).filter((heir) =>
    years!.some((year) => hasCell(year.lines.find((line) => line.heirId === heir.heirId))))
  const commented = years?.find((year) => year.year === commenting)

  return (
    <main className="content">
      <h1 className="page-title">{t.tabContributions}</h1>

      <div className="toolbar contribution-toolbar">
        <div className="contribution-pager">
          <button type="button" className="icon-button" onClick={() => move(-SPAN)} disabled={start <= 1900}
            aria-label={t.earlierYears} title={t.earlierYears}>
            <ChevronLeftIcon />
          </button>
          <span className="contribution-range" aria-live="polite">{start}–{end}</span>
          <button type="button" className="icon-button" onClick={() => move(SPAN)} disabled={end >= 2999}
            aria-label={t.laterYears} title={t.laterYears}>
            <ChevronRightIcon />
          </button>
        </div>
        <button type="button" className="button contribution-pdf" onClick={downloadPdf}
          aria-label={t.exportContributionsPdf} title={t.exportContributionsPdf}>
          <FileIcon width={18} height={18} /> PDF
        </button>
      </div>

      {loadError !== null && (
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      )}
      {error && <p className="form-error" role="alert">{error}</p>}

      {years === null && loadError === null && <p className="empty">{t.loading}</p>}
      {years !== null && heirs.length === 0 && <p className="empty">{t.noContributors}</p>}

      {years !== null && heirs.length > 0 && (
        <div className="contribution-scroll" ref={scroller}>
          <table className="contribution-table">
            <thead>
              <tr>
                <th scope="col">{t.heirColumn}</th>
                {years.map((year) => (
                  <th key={year.year} scope="col" className={year.year === currentYear ? 'contribution-now' : undefined}>
                    {year.year}
                  </th>
                ))}
                <th scope="col">{t.total}</th>
              </tr>
            </thead>
            <tbody>
              {heirs.map((heir, row) => {
                const lines = years.map((year) => year.lines.find((line) => line.heirId === heir.heirId))
                const total = lines.reduce((sum, line) => sum + (line?.amountEur ?? 0), 0)
                return (
                  <tr key={heir.heirId}>
                    <th scope="row">{heir.deceased ? `${heir.heir} †` : heir.heir}</th>
                    {lines.map((line, column) => {
                      const year = years[column].year
                      if (!hasCell(line)) {
                        return <td key={year} className="contribution-none">—</td>
                      }
                      return (
                        <td key={year}>
                          {canEdit ? (
                            <AmountCell label={`${heir.heir} ${year}`} amount={line.amountEur} row={row} column={column}
                              onSave={(text) => save(year, heir.heirId, text, line.amountEur)} />
                          ) : (
                            line.amountEur !== null ? formatEuros(line.amountEur, t.locale) : ''
                          )}
                        </td>
                      )
                    })}
                    <td className="contribution-total">{formatEuros(total, t.locale)}</td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">{t.yearlyAmountLabel}</th>
                {years.map((year) => {
                  const common = yearlyAmount(year)
                  return <td key={year.year}>{common !== null ? formatEuros(common, t.locale) : '—'}</td>
                })}
                <td />
              </tr>
              <tr>
                <th scope="row">{t.amountPaid}</th>
                {years.map((year) => <td key={year.year}>{formatEuros(year.totalEur, t.locale)}</td>)}
                <td className="contribution-total">
                  {formatEuros(years.reduce((sum, year) => sum + year.totalEur, 0), t.locale)}
                </td>
              </tr>
              <tr>
                <th scope="row">{t.comments}</th>
                {years.map((year) => (
                  <td key={year.year}>
                    {(canEdit || year.comment) && (
                      <button type="button"
                        className={year.comment ? 'icon-button contribution-has-comment' : 'icon-button'}
                        onClick={() => setCommenting(year.year)}
                        aria-label={t.commentOfYear(year.year)} title={year.comment ?? t.commentOfYear(year.year)}>
                        <PencilIcon width={16} height={16} />
                      </button>
                    )}
                  </td>
                ))}
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {commented && (
        <YearDialog year={commented} editable={canEdit}
          onSaved={(changed) => { replace(changed); setCommenting(null) }}
          onDelete={() => { setCommenting(null); setDeleting(commented.year) }}
          onClose={() => setCommenting(null)} />
      )}

      {deleting !== null && (
        <ConfirmDialog title={t.deleteYearTitle} message={t.deleteYearMessage(deleting)} confirmLabel={t.delete}
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await api.deleteContributionYear(deleting)
            await reload()
            setDeleting(null)
          }} />
      )}
    </main>
  )
}

/** The amount field of the cell at this row and column, if there is one */
const cellAt = (row: number, column: number) =>
  document.querySelector<HTMLInputElement>(`.contribution-table input[data-row="${row}"][data-column="${column}"]`)

/**
 * An amount saved when leaving the cell. Enter and the down arrow go to the heir below in the
 * same year, the up arrow to the one above; a cell with a dash is skipped.
 */
function AmountCell(props: {
  label: string
  amount: number | null
  row: number
  column: number
  onSave: (text: string) => Promise<void>
}) {
  const { t } = useI18n()
  const shown = props.amount !== null ? amountText(props.amount, t.locale) : ''
  const [text, setText] = useState(shown)

  useEffect(() => setText(shown), [shown])

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    const step = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' || event.key === 'Enter' ? 1 : 0
    if (step === 0) {
      return
    }
    event.preventDefault()
    const rows = document.querySelectorAll('.contribution-table tbody tr').length
    for (let row = props.row + step; row >= 0 && row < rows; row += step) {
      const next = cellAt(row, props.column)
      if (next) {
        next.focus()
        next.select()
        return
      }
    }
    // Nobody further in that direction: Enter still saves
    if (event.key === 'Enter') {
      event.currentTarget.blur()
    }
  }

  return (
    <input className="contribution-cell" inputMode="decimal" value={text} aria-label={props.label}
      data-row={props.row} data-column={props.column}
      onChange={(e) => setText(e.target.value)}
      onFocus={(e) => e.currentTarget.select()}
      onBlur={() => props.onSave(text)}
      onKeyDown={onKeyDown} />
  )
}

/** The comment of a year, and for administrators a way to delete the year with its amounts */
function YearDialog(props: {
  year: ContributionYear
  editable: boolean
  onSaved: (year: ContributionYear) => void
  onDelete: () => void
  onClose: () => void
}) {
  const { year, editable, onSaved, onDelete, onClose } = props
  const { t } = useI18n()
  const ref = useModal()
  const [text, setText] = useState(year.comment ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Only a year with something entered is stored, and can be deleted
  const stored = year.comment !== null || year.lines.some((line) => line.amountEur !== null)

  async function save() {
    if (text.trim() === (year.comment ?? '')) {
      return onClose()
    }
    setSaving(true)
    setError(null)
    try {
      onSaved(await api.setContributionComment(year.year, text))
    } catch (e) {
      setError(apiErrorMessage(e, t))
      setSaving(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog dialog-small" aria-labelledby="contribution-year-title"
      onCancel={(e) => { e.preventDefault(); if (!saving) onClose() }}>
      <h2 id="contribution-year-title">{t.commentOfYear(year.year)}</h2>
      {editable ? (
        <label className="field">
          <span>{t.comments} <small>{t.optional}</small></span>
          <textarea rows={4} maxLength={1000} value={text} onChange={(e) => setText(e.target.value)} autoFocus />
        </label>
      ) : (
        <p className="contribution-comment">{year.comment}</p>
      )}
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="dialog-actions">
        {editable && stored && (
          <button type="button" className="button button-quiet button-danger-text contribution-delete-year"
            onClick={onDelete} disabled={saving}>
            {t.deleteYear}
          </button>
        )}
        <button type="button" className="button" onClick={onClose} disabled={saving}>
          {editable ? t.cancel : t.close}
        </button>
        {editable && (
          <button type="button" className="button button-primary" onClick={save} disabled={saving}>
            {saving ? t.saving : t.save}
          </button>
        )}
      </div>
    </dialog>
  )
}
