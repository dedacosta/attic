import type { ReactNode } from 'react'
import { SearchIcon } from './icons'

interface Props {
  query: string
  onQuery: (query: string) => void
  placeholder: string
  label: string
  /** Actions shown next to the search field, e.g. a "New" button */
  children?: ReactNode
}

export default function SearchBar({ query, onQuery, placeholder, label, children }: Props) {
  return (
    <div className="search-bar">
      <label className="search">
        <SearchIcon />
        <input type="search" placeholder={placeholder} value={query} onChange={(e) => onQuery(e.target.value)}
          aria-label={label} />
      </label>
      {children}
    </div>
  )
}
