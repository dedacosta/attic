import { FLAGS } from './flags'
import { LANGUAGE_NAMES, LANGUAGES, useI18n } from '../i18n'

export default function LanguageSwitch() {
  const { language, setLanguage, t } = useI18n()
  return (
    <div className="segmented segmented-small" role="group" aria-label={t.languageLabel}>
      {LANGUAGES.map((l) => {
        const Flag = FLAGS[l]
        return (
          <button key={l} type="button" lang={l} aria-pressed={language === l} aria-label={LANGUAGE_NAMES[l]}
            title={LANGUAGE_NAMES[l]} onClick={() => setLanguage(l)}>
            <Flag className="flag" />
          </button>
        )
      })}
    </div>
  )
}
