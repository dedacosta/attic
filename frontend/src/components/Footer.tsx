import { copyrightYears } from '../lib/format'
import { useI18n } from '../i18n'

const SOURCE_URL = 'https://github.com/dedacosta/attic'

export default function Footer() {
  const { t } = useI18n()
  return (
    <footer className="footer">
      <p>
        © {copyrightYears()} David Da Costa · v{__APP_VERSION__} ·{' '}
        <a href={SOURCE_URL} target="_blank" rel="noreferrer">{t.sourceCode}</a>
      </p>
      <p className="footer-credits">{__BUILT_WITH__}</p>
    </footer>
  )
}
