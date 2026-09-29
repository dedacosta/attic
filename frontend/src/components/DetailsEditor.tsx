import { useI18n } from '../i18n'
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon, TrashIcon } from './icons'
import type { Fact } from '../api/types'

interface Props {
  facts: Fact[]
  onChange: (facts: Fact[]) => void
  /** Labels already used on other properties, offered next to the standard suggestions */
  usedLabels: string[]
}

const LABELS_LIST = 'property-labels'

/** Label / value lines of a property, in order, with suggested labels. */
export default function DetailsEditor({ facts, onChange, usedLabels }: Props) {
  const { t } = useI18n()
  const suggestions = [...new Set([...t.suggestedLabels, ...usedLabels])]

  const set = (index: number, change: Partial<Fact>) =>
    onChange(facts.map((fact, i) => (i === index ? { ...fact, ...change } : fact)))

  function move(index: number, step: number) {
    const moved = [...facts]
    ;[moved[index], moved[index + step]] = [moved[index + step], moved[index]]
    onChange(moved)
  }

  return (
    <div className="details-editor field-wide">
      <span className="details-editor-title">{t.details} <small>{t.optional}</small></span>
      <datalist id={LABELS_LIST}>
        {suggestions.map((label) => <option key={label} value={label} />)}
      </datalist>
      {facts.map((fact, index) => (
        <div key={index} className="details-row">
          <input list={LABELS_LIST} value={fact.label} placeholder={t.detailLabel} aria-label={t.detailLabel}
            onChange={(e) => set(index, { label: e.target.value })} />
          <input value={fact.value} placeholder={t.detailValue} aria-label={t.detailValue}
            onChange={(e) => set(index, { value: e.target.value })} />
          <button type="button" className="icon-button details-move" onClick={() => move(index, -1)}
            disabled={index === 0} aria-label={t.moveUp}>
            <ChevronLeftIcon width={16} height={16} />
          </button>
          <button type="button" className="icon-button details-move" onClick={() => move(index, 1)}
            disabled={index === facts.length - 1} aria-label={t.moveDown}>
            <ChevronRightIcon width={16} height={16} />
          </button>
          <button type="button" className="icon-button" onClick={() => onChange(facts.filter((_, i) => i !== index))}
            aria-label={t.removeDetail}>
            <TrashIcon width={16} height={16} />
          </button>
        </div>
      ))}
      <button type="button" className="button button-quiet" onClick={() => onChange([...facts, { label: '', value: '' }])}>
        <PlusIcon width={16} height={16} /> {t.addDetail}
      </button>
    </div>
  )
}
