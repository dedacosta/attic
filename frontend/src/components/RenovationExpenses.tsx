import { useRef, useState, type KeyboardEvent } from 'react'
import { api, apiErrorMessage } from '../api/api'
import { AMOUNT_PATTERN, amountText, decimalSeparator, formatEuros, parseAmount } from '../lib/format'
import { useI18n } from '../i18n'
import { PlusIcon, TrashIcon } from './icons'
import { EXPENSE_KINDS, type ExpenseKind, type Renovation, type RenovationExpense } from '../api/types'

/** An expense while it is edited, with its amount as typed */
interface Row {
  key: number
  description: string
  kind: ExpenseKind
  amount: string
  paid: boolean
}

interface Props {
  renovation: Renovation
  editable: boolean
  onChanged: (renovation: Renovation) => void
}

/** What the rows are worth while they are typed: an amount not typed in full yet counts for nothing */
function total(rows: Row[]): number {
  const cents = rows.reduce((sum, row) => {
    const amount = row.amount.trim()
    return sum + (amount !== '' && AMOUNT_PATTERN.test(amount) ? Math.round(parseAmount(amount) * 100) : 0)
  }, 0)
  return cents / 100
}

/**
 * The expenses of a renovation, a list edited in place: whether each is paid, what it is, its kind
 * and its amount. The list is saved when a line is left or a box or a
 * choice is changed; a line without description is not saved until it has one.
 */
export default function RenovationExpenses({ renovation, editable, onChanged }: Props) {
  const { t } = useI18n()
  const serial = useRef(0)
  const toRow = (expense: RenovationExpense): Row => ({
    key: serial.current++,
    description: expense.description,
    kind: expense.kind,
    amount: amountText(expense.amountEur, t.locale),
    paid: expense.paid,
  })
  const [rows, setRows] = useState<Row[]>(() => renovation.expenses.map(toRow))
  // The line added last takes the focus
  const [fresh, setFresh] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Saves run one after another, and the list last saved is not sent again
  const saving = useRef(Promise.resolve())
  const saved = useRef(JSON.stringify(renovation.expenses.map(({ description, kind, amountEur, paid }) =>
    ({ description, kind, amountEur, paid }))))

  /** Show the rows, and save those with a description once every amount is one */
  function save(next: Row[]) {
    setRows(next)
    const expenses: RenovationExpense[] = []
    for (const row of next) {
      const amount = row.amount.trim()
      if (!row.description.trim()) {
        continue
      }
      if (amount !== '' && !AMOUNT_PATTERN.test(amount)) {
        setError(t.errorAmount)
        return
      }
      expenses.push({
        description: row.description.trim(),
        kind: row.kind,
        amountEur: amount ? parseAmount(amount) : 0,
        paid: row.paid,
      })
    }
    setError(null)
    const json = JSON.stringify(expenses)
    if (json === saved.current) {
      return
    }
    saved.current = json
    saving.current = saving.current.then(async () => {
      try {
        onChanged(await api.setRenovationExpenses(renovation.id, expenses))
      } catch (e) {
        saved.current = ''
        setError(apiErrorMessage(e, t))
      }
    })
  }

  const change = (key: number, change: Partial<Row>) =>
    rows.map((row) => (row.key === key ? { ...row, ...change } : row))

  function add() {
    const row: Row = { key: serial.current++, description: '', kind: 'MATERIALS', amount: '', paid: false }
    setFresh(row.key)
    setRows([...rows, row])
  }

  const onEnter = (next: () => void) => (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      next()
    }
  }

  if (!editable) {
    return (
      <>
        {renovation.expenses.length === 0 && <p className="hint">{t.noExpenses}</p>}
        <ul className="renovation-expenses">
          {renovation.expenses.map((expense, index) => (
            <li key={index} className="renovation-expense">
              <input type="checkbox" checked={expense.paid} disabled aria-label={t.expensePaid(expense.description)} />
              <span className="renovation-expense-description">{expense.description}</span>
              <span className="renovation-expense-kind">{t.expenseKinds[expense.kind]}</span>
              <span className="renovation-expense-amount">{formatEuros(expense.amountEur, t.locale)}</span>
            </li>
          ))}
          {renovation.expenses.length > 0 && (
            <li className="renovation-expense renovation-expense-total">
              <span />
              <span>{t.total}</span>
              <span className="renovation-expense-kind" />
              <span className="renovation-expense-amount">{formatEuros(renovation.costEur, t.locale)}</span>
            </li>
          )}
        </ul>
      </>
    )
  }

  return (
    <>
      <ul className="renovation-expenses">
        {rows.map((row) => (
          <li key={row.key} className="renovation-expense renovation-expense-edit"
            onBlur={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) save(rows) }}>
            <input type="checkbox" checked={row.paid}
              aria-label={t.expensePaid(row.description)} title={t.amountPaid}
              onChange={(e) => save(change(row.key, { paid: e.target.checked }))} />
            <input className="renovation-expense-description" value={row.description} placeholder={t.description}
              aria-label={t.description} autoFocus={row.key === fresh} maxLength={200}
              onKeyDown={onEnter(() => (document.getElementById(`expense-amount-${row.key}`) as HTMLInputElement | null)?.focus())}
              onChange={(e) => setRows(change(row.key, { description: e.target.value }))} />
            <select className="renovation-expense-kind-field" value={row.kind} aria-label={t.expenseKind}
              title={t.expenseKind} onChange={(e) => save(change(row.key, { kind: e.target.value as ExpenseKind }))}>
              {EXPENSE_KINDS.map((kind) => <option key={kind} value={kind}>{t.expenseKinds[kind]}</option>)}
            </select>
            <input id={`expense-amount-${row.key}`} className="renovation-expense-amount" inputMode="decimal"
              value={row.amount} placeholder={`0${decimalSeparator(t.locale)}00`} aria-label={t.amountEur}
              title={t.amountEur} enterKeyHint="next" onKeyDown={onEnter(add)}
              onChange={(e) => setRows(change(row.key, { amount: e.target.value }))} />
            <button type="button" className="icon-button" aria-label={t.removeExpense} title={t.removeExpense}
              onClick={() => save(rows.filter((other) => other.key !== row.key))}>
              <TrashIcon width={16} height={16} />
            </button>
          </li>
        ))}
        <li className="renovation-expense-add">
          <button type="button" className="button button-small" onClick={add}>
            <PlusIcon width={16} height={16} /> {t.addExpense}
          </button>
          <span className="renovation-expense-total-amount">
            {t.total}{t.colon} <strong>{formatEuros(total(rows), t.locale)}</strong>
          </span>
        </li>
      </ul>
      {error && <p className="form-error" role="alert">{error}</p>}
    </>
  )
}
