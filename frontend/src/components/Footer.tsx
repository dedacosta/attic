import { copyrightYears } from '../lib/format'

export default function Footer() {
  return (
    <footer className="footer">
      <p>© {copyrightYears()} David Da Costa · v{__APP_VERSION__}</p>
      <p className="footer-credits">{__BUILT_WITH__}</p>
    </footer>
  )
}
