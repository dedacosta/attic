import type { SVGProps } from 'react'

const base = {
  width: 20,
  height: 20,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

export const HouseIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M3 10.5 12 3l9 7.5" />
    <path d="M5 9.5V21h14V9.5" />
    <path d="M10 21v-6h4v6" />
  </svg>
)

export const SearchIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
)

export const PlusIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M12 5v14M5 12h14" />
  </svg>
)

export const TrashIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
  </svg>
)

export const ImageIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <circle cx="9" cy="10" r="2" />
    <path d="m21 16-5-5-9 9" />
  </svg>
)

export const FileIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M14 3H6v18h12V7z" />
    <path d="M14 3v4h4M9 13h6M9 17h6" />
  </svg>
)

export const BoxIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5z" />
    <path d="m3 7.5 9 4.5 9-4.5M12 12v9" />
  </svg>
)

export const IdCardIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <rect x="2.5" y="5" width="19" height="14" rx="2" />
    <circle cx="8.5" cy="11" r="2" />
    <path d="M5.5 16c.6-1.5 1.7-2.2 3-2.2s2.4.7 3 2.2M14.5 10h4M14.5 13.5h3" />
  </svg>
)

export const HeirsIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <circle cx="9" cy="8" r="3.5" />
    <path d="M2.5 20c.8-3.6 3.3-5.5 6.5-5.5s5.7 1.9 6.5 5.5" />
    <path d="M16 4.8a3.5 3.5 0 0 1 0 6.4M18.5 14.8c1.6.8 2.6 2.5 3 5.2" />
  </svg>
)

export const UserIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21c1-4.2 4-6.5 8-6.5s7 2.3 8 6.5" />
  </svg>
)

export const KeyIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <circle cx="8" cy="15" r="4" />
    <path d="m10.8 12.2 8.7-8.7M16 7l3 3M14 9l2 2" />
  </svg>
)

export const CloseIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M6 6l12 12M18 6 6 18" />
  </svg>
)

export const PencilIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M4 20h4L19 9l-4-4L4 16z" />
    <path d="m13.5 6.5 4 4" />
  </svg>
)

export const DownloadIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg {...base} {...props}>
    <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
  </svg>
)
