import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, apiErrorMessage } from '../api/api'
import CommentBox from '../components/CommentBox'
import ConfirmDialog from '../components/ConfirmDialog'
import { ChevronIcon, FileIcon, PlusIcon } from '../components/icons'
import { AMOUNT_PATTERN, amountText, formatEuros, parseAmount } from '../lib/format'
import { usePermissions } from '../lib/permissions'
import { useI18n } from '../i18n'
import type { ContributionYear } from '../api/types'

/**
 * The Contributions tab: a card per year listing the heirs alive that year with what each
 * contributed. Everybody sees it; administrators add years and enter the amounts.
 */
export default function ContributionsView() {
  const { t } = useI18n()
  const { canEdit } = usePermissions()
  const [years, setYears] = useState<ContributionYear[] | null>(null)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [deleting, setDeleting] = useState<number | null>(null)
  // Years are small cards until opened
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  function toggle(year: number) {
    setExpanded((current) => {
      const next = new Set(current)
      if (!next.delete(year)) {
        next.add(year)
      }
      return next
    })
  }

  const reload = useCallback(async () => {
    try {
      setYears(await api.listContributions())
      setLoadError(null)
    } catch (e) {
      setLoadError(e)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  async function downloadPdf() {
    // Loaded on demand: the PDF library is large
    const { exportContributionsPdf } = await import('../lib/pdf')
    await exportContributionsPdf(years ?? [], t)
  }

  /** Show a year as the server answered after a change */
  const replace = (changed: ContributionYear) =>
    setYears((current) => current?.map((year) => (year.year === changed.year ? changed : year)) ?? null)

  return (
    <main className="content">
      <h1 className="page-title">{t.tabContributions}</h1>

      <div className="toolbar">
        {canEdit && years !== null && (
          <AddYear years={years} onAdded={async (year) => {
            await reload()
            // A new year opens, ready for its amounts
            setExpanded((current) => new Set(current).add(year))
          }} />
        )}
        {years !== null && years.length > 0 && (
          <button type="button" className="button contribution-pdf" onClick={downloadPdf}
            aria-label={t.exportContributionsPdf} title={t.exportContributionsPdf}>
            <FileIcon width={18} height={18} /> PDF
          </button>
        )}
      </div>

      {loadError !== null && (
        <div className="banner" role="alert">
          {apiErrorMessage(loadError, t)}{' '}
          <button type="button" className="link" onClick={reload}>{t.tryAgain}</button>
        </div>
      )}

      {years === null && loadError === null && <p className="empty">{t.loading}</p>}
      {years !== null && years.length === 0 && <p className="empty">{t.noContributionYears}</p>}

      <div className="contribution-grid">
        {years?.map((year) => (
          <YearCard key={year.year} year={year} editable={canEdit} expanded={expanded.has(year.year)}
            onToggle={() => toggle(year.year)} onChanged={replace} onDelete={() => setDeleting(year.year)} />
        ))}
      </div>

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

/** Field and button to add a year, suggesting the current year or the one after the latest */
function AddYear({ years, onAdded }: { years: ContributionYear[]; onAdded: (year: number) => Promise<void> }) {
  const { t } = useI18n()
  const current = new Date().getFullYear()
  const suggested = years.some((year) => year.year === current) ? Math.max(...years.map((y) => y.year)) + 1 : current
  const [year, setYear] = useState(String(suggested))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => setYear(String(suggested)), [suggested])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const value = Number(year)
    if (!Number.isInteger(value) || value < 1900 || value > 2999) {
      return setError(t.errorYear)
    }
    setError(null)
    try {
      await api.createContributionYear(value)
      await onAdded(value)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409 ? t.errorYearExists : apiErrorMessage(e, t))
    }
  }

  return (
    <form className="contribution-add-year" onSubmit={submit} noValidate>
      <label className="field contribution-year-field">
        <span>{t.year}</span>
        <input inputMode="numeric" value={year} onChange={(e) => setYear(e.target.value)} />
      </label>
      <button type="submit" className="button button-primary contribution-add">
        <PlusIcon width={18} height={18} /> {t.addYear}
      </button>
      {error && <p className="form-error" role="alert">{error}</p>}
    </form>
  )
}

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

function YearCard(props: {
  year: ContributionYear
  editable: boolean
  expanded: boolean
  onToggle: () => void
  onChanged: (year: ContributionYear) => void
  onDelete: () => void
}) {
  const { year, editable, expanded, onToggle, onChanged, onDelete } = props
  const { t } = useI18n()
  const [error, setError] = useState<string | null>(null)
  // Those who pay that year, and anybody else with an amount entered
  const lines = year.lines.filter((line) => line.pays || line.amountEur !== null)
  const common = yearlyAmount(year)
  // What those who pay still owe: their part of the yearly amount, less what they gave
  const missing = common === null ? null : lines.reduce((sum, line) =>
    sum + (line.portion !== null ? Math.max(0, common * line.portion - (line.amountEur ?? 0)) : 0), 0)

  /** Save an amount when it changed; an empty field removes it */
  async function save(heirId: string, text: string, previous: number | null): Promise<boolean> {
    const value = text.trim() === '' ? null : text.trim()
    if (value !== null && !AMOUNT_PATTERN.test(value)) {
      setError(t.errorAmount)
      return false
    }
    const amount = value === null ? null : parseAmount(value)
    setError(null)
    if (amount === previous) {
      return true
    }
    try {
      onChanged(await api.setContribution(year.year, heirId, amount))
      return true
    } catch (e) {
      setError(apiErrorMessage(e, t))
      return false
    }
  }

  async function saveComment(text: string) {
    if (text.trim() === (year.comment ?? '')) {
      return
    }
    setError(null)
    try {
      onChanged(await api.setContributionComment(year.year, text))
    } catch (e) {
      setError(apiErrorMessage(e, t))
    }
  }

  return (
    <section className={expanded ? 'card contribution-card contribution-card-open' : 'card contribution-card'}
      aria-labelledby={`year-${year.year}`}>
      <h2 className="contribution-heading">
        <button type="button" className="contribution-summary" onClick={onToggle} aria-expanded={expanded}
          aria-controls={`year-${year.year}-lines`}>
          <span className="contribution-title">
            <span id={`year-${year.year}`}>{year.year}</span>
            <ChevronIcon className="contribution-chevron" width={18} height={18} />
          </span>
          <span className="contribution-common">
            {common !== null ? t.yearlyAmount(formatEuros(common, t.locale)) : '—'}
          </span>
          <span className="contribution-facts">
            <span>{t.amountPaid}{t.colon} <strong>{formatEuros(year.totalEur, t.locale)}</strong></span>
            {missing !== null && (
              <span className={missing > 0 ? 'contribution-missing' : undefined}>
                {t.amountMissing}{t.colon} <strong>{formatEuros(missing, t.locale)}</strong>
              </span>
            )}
          </span>
          {!expanded && year.comment && <span className="contribution-comment-preview">{year.comment}</span>}
        </button>
      </h2>
      {expanded && (
        <div id={`year-${year.year}-lines`} className="contribution-body">
          {lines.length === 0 && <p className="hint">{t.noActiveHeirs}</p>}
          <ul className="contribution-lines">
            {lines.map((line) => (
              <li key={line.heirId}>
                <span className="contribution-heir">{line.heir}</span>
                <span className="contribution-entry">
                  {editable ? (
                    <AmountInput label={t.amountOf(line.heir)} amount={line.amountEur}
                      onSave={(text) => save(line.heirId, text, line.amountEur)} />
                  ) : (
                    <span className="contribution-amount">
                      {line.amountEur !== null ? formatEuros(line.amountEur, t.locale) : '—'}
                    </span>
                  )}
                  {/* What this heir is expected to give, e.g. half for one of two children */}
                  {common !== null && line.portion !== null && (
                    <span className="contribution-expected">/ {formatEuros(common * line.portion, t.locale)}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
          {editable ? (
            <CommentBox comment={year.comment} onSave={saveComment} />
          ) : (
            year.comment && <p className="contribution-comment">{year.comment}</p>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
          {editable && (
            <footer className="contribution-footer">
              <button type="button" className="button button-quiet button-danger-text button-small" onClick={onDelete}>
                {t.deleteYear}
              </button>
            </footer>
          )}
        </div>
      )}
    </section>
  )
}

/** An amount saved when leaving the field or pressing Enter */
function AmountInput(props: { label: string; amount: number | null; onSave: (text: string) => Promise<boolean> }) {
  const { t } = useI18n()
  const shown = props.amount !== null ? amountText(props.amount, t.locale) : ''
  const [text, setText] = useState(shown)

  useEffect(() => setText(shown), [shown])

  return (
    <span className="contribution-input">
      <input inputMode="decimal" value={text} aria-label={props.label} placeholder="—"
        onChange={(e) => setText(e.target.value)}
        onBlur={() => props.onSave(text)}
        onKeyDown={(e) => { if (e.key === 'Enter') e.currentTarget.blur() }} />
      <span aria-hidden="true">€</span>
    </span>
  )
}
