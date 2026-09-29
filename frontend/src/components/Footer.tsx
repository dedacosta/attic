import { copyrightYears } from '../lib/format'
import { useI18n } from '../i18n'

export default function Footer() {
  const { t } = useI18n()
  return (
    <footer className="footer">
      © {copyrightYears()} David Da Costa · {t.madeWith} · v{__APP_VERSION__}
    </footer>
  )
}
