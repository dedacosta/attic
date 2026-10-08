import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import en from './en.json'
import fr from './fr.json'
import pt from './pt.json'

export type Language = 'en' | 'pt' | 'fr'

export const LANGUAGES: Language[] = ['pt', 'fr', 'en']

/** Each language's name in that language, so everyone can find their own */
export const LANGUAGE_NAMES: Record<Language, string> = { en: 'English', pt: 'Português', fr: 'Français' }

/*
 * The texts of each language are in <language>.json. A text can contain {placeholders}, filled in
 * from the values the code passes, in the order given here. A text that depends on a number has
 * one form per plural category of the language ("one", "other", …, chosen by Intl.PluralRules on
 * {count}), and may have forms for exact numbers such as "=0".
 */
const PARAMS = {
  itemCount: ['count'],
  filteredCount: ['shown', 'count'],
  editNamed: ['name'],
  showNamed: ['name'],
  deleteYearMessage: ['year'],
  commentOfYear: ['year'],
  paidBy: ['name'],
  deleteRenovationMessage: ['title'],
  deleteNamed: ['name'],
  deleteMessage: ['name'],
  errorRequest: ['status'],
  pdfGenerated: ['date'],
  searchFilter: ['query'],
  page: ['page', 'total'],
  documentCount: ['count'],
  filteredDocumentCount: ['shown', 'count'],
  age: ['count'],
  heirCount: ['count'],
  filteredHeirCount: ['shown', 'count'],
  deleteHeirMessage: ['name', 'count'],
  childrenOf: ['name'],
  deleteHeirChildren: ['count'],
  heritageSummary: ['assigned', 'percent'],
  heritageMissing: ['rest'],
  expiredCount: ['count'],
  expiringCount: ['count'],
  errorPasswordShort: ['length'],
  addedOn: ['when'],
  accountCreatedOn: ['when'],
  changedOn: ['when'],
  newPasswordFor: ['username'],
  deleteUserMessage: ['username'],
  addedUser: ['username'],
  passwordReset: ['username'],
  editContact: ['username'],
  invitationCreated: ['username', 'when'],
  invitationExpires: ['when'],
  photoOf: ['n', 'total'],
  openPhoto: ['n'],
  photoCount: ['count'],
  errorPhotoUpload: ['file', 'reason'],
  landCount: ['count'],
  buildingCount: ['count'],
  filteredBuildingCount: ['shown', 'count'],
  filteredLandCount: ['shown', 'count'],
  totalValue: ['value'],
  fileCount: ['count'],
  deletePropertyMessage: ['name'],
} as const satisfies Partial<Record<keyof typeof en, readonly string[]>>

type Params = typeof PARAMS

/** A text, its forms for each plural category, or a list of texts */
type Text = string | Record<string, string> | string[]

/** One value per placeholder name */
type Values<Names extends readonly string[]> = { -readonly [I in keyof Names]: string | number }

/** Texts with placeholders become functions of their values; lists such as rooms stay lists */
type Plain<T> = T extends string ? string : T extends string[] ? string[] : Record<string, string>

export type Messages = {
  [K in keyof typeof en]: K extends keyof Params
    ? (...values: Values<Params[K]>) => string
    : Plain<(typeof en)[K]>
}

function fill(text: string, values: Record<string, string | number>): string {
  return text.replace(/\{(\w+)\}/g, (placeholder, name: string) => (name in values ? String(values[name]) : placeholder))
}

/** The messages of a language. Every text of English must be there, so a missing one fails the build. */
function load(texts: Record<keyof typeof en, Text>): Messages {
  const plurals = new Intl.PluralRules(texts.locale as string)
  const messages = Object.entries(texts).map(([key, text]) => {
    const names: readonly string[] | undefined = (PARAMS as Record<string, readonly string[]>)[key]
    if (!names || Array.isArray(text)) {
      return [key, text]
    }
    return [key, (...args: (string | number)[]) => {
      const values = Object.fromEntries(names.map((name, i) => [name, args[i]]))
      if (typeof text === 'string') {
        return fill(text, values)
      }
      const count = Number(values.count)
      return fill(text[`=${count}`] ?? text[plurals.select(count)] ?? text.other, values)
    }]
  })
  return Object.fromEntries(messages) as Messages
}

const MESSAGES: Record<Language, Messages> = { en: load(en), pt: load(pt), fr: load(fr) }

const isLanguage = (value: string | null): value is Language => LANGUAGES.includes(value as Language)

const STORAGE_KEY = 'attic.language'

function initialLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (isLanguage(stored)) {
      return stored
    }
  } catch {
    // storage unavailable, fall back to the browser language
  }
  // First of the browser's preferred languages that we support, e.g. "fr-CA" -> "fr"
  const preferred = navigator.languages.map((l) => l.slice(0, 2).toLowerCase()).find(isLanguage)
  return preferred ?? 'en'
}

interface I18n {
  language: Language
  setLanguage: (language: Language) => void
  t: Messages
}

const I18nContext = createContext<I18n | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState(initialLanguage)

  useEffect(() => {
    document.documentElement.lang = MESSAGES[language].locale
  }, [language])

  const value = useMemo<I18n>(
    () => ({
      language,
      t: MESSAGES[language],
      setLanguage: (next) => {
        setLanguageState(next)
        try {
          localStorage.setItem(STORAGE_KEY, next)
        } catch {
          // not remembered, still switched for this visit
        }
      },
    }),
    [language],
  )

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18n {
  const i18n = useContext(I18nContext)
  if (!i18n) {
    throw new Error('useI18n must be used inside I18nProvider')
  }
  return i18n
}
