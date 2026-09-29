import type { SVGProps } from 'react'
import type { Language } from '../i18n'

const size = { width: 21, height: 14, preserveAspectRatio: 'none', 'aria-hidden': true } as const

const UnitedKingdom = (props: SVGProps<SVGSVGElement>) => (
  <svg viewBox="0 0 60 30" {...size} {...props}>
    <clipPath id="flag-uk-cross">
      <path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" />
    </clipPath>
    <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
    <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" strokeWidth="6" />
    <path d="M0,0 L60,30 M60,0 L0,30" clipPath="url(#flag-uk-cross)" stroke="#C8102E" strokeWidth="4" />
    <path d="M30,0 v30 M0,15 h60" stroke="#fff" strokeWidth="10" />
    <path d="M30,0 v30 M0,15 h60" stroke="#C8102E" strokeWidth="6" />
  </svg>
)

const Portugal = (props: SVGProps<SVGSVGElement>) => (
  <svg viewBox="0 0 600 400" {...size} {...props}>
    <rect width="240" height="400" fill="#046A38" />
    <rect x="240" width="360" height="400" fill="#DA291C" />
    <circle cx="240" cy="200" r="84" fill="#FFE900" />
    <path d="M195 145h90v65a45 45 0 0 1-90 0z" fill="#DA291C" />
    <path d="M212 162h56v46a28 28 0 0 1-56 0z" fill="#fff" />
  </svg>
)

const France = (props: SVGProps<SVGSVGElement>) => (
  <svg viewBox="0 0 3 2" {...size} {...props}>
    <rect width="1" height="2" fill="#002654" />
    <rect x="1" width="1" height="2" fill="#fff" />
    <rect x="2" width="1" height="2" fill="#ED2939" />
  </svg>
)

export const FLAGS: Record<Language, (props: SVGProps<SVGSVGElement>) => React.JSX.Element> = {
  en: UnitedKingdom,
  pt: Portugal,
  fr: France,
}
