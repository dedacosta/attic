import type { Messages } from '../i18n'
import type {
  ContributionYear, DocumentInput, Renovation, RenovationInput, Invitation, CatalogItem, CatalogItemInput, OwnAccount, Heir, HeirDocument,
  HeirInput, Estate, InventoryItem, InventoryItemInput, Picture, Property, PropertyDocument, PropertyDocumentInput, PropertyInput, Role, Session, UserAccount,
} from './types'

/** A failed request. {@link apiErrorMessage} turns it into text in the user's language. */
export class ApiError extends Error {
  constructor(
    readonly status: number | null,
    readonly detail: string | null = null,
  ) {
    super(detail ?? `Request failed (${status ?? 'offline'})`)
  }
}

/** Fired when the server says the user is no longer signed in, so the app can show the sign-in screen. */
export const SIGNED_OUT_EVENT = 'attic:signed-out'

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

/** Adds the CSRF token that the server requires on every request that changes something. */
function withCsrf(init: RequestInit = {}): RequestInit {
  const method = (init.method ?? 'GET').toUpperCase()
  if (method === 'GET' || method === 'HEAD') {
    return init
  }
  const headers = new Headers(init.headers)
  const token = csrfToken()
  if (token) {
    headers.set('X-XSRF-TOKEN', token)
  }
  return { ...init, headers }
}

async function send(url: string, init?: RequestInit): Promise<Response> {
  try {
    let response = await fetch(url, withCsrf(init))
    if (response.status === 403 && init?.method && init.method !== 'GET') {
      // The CSRF token changes when signing in; fetch the current one and try once more
      await fetch('/api/session')
      response = await fetch(url, withCsrf(init))
    }
    return response
  } catch {
    throw new ApiError(null)
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await send(url, init)
  if (response.status === 401) {
    window.dispatchEvent(new Event(SIGNED_OUT_EVENT))
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response))
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}

async function problemDetail(response: Response): Promise<string | null> {
  try {
    const problem = await response.json()
    return typeof problem.detail === 'string' ? problem.detail : null
  } catch {
    return null
  }
}

export function apiErrorMessage(error: unknown, t: Messages): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error ? error.message : String(error)
  }
  switch (error.status) {
    case null:
      return t.errorOffline
    case 401:
      return t.errorSignedOut
    case 404:
      return t.errorNotFound
    case 413:
      return t.errorTooLarge
    case 415:
      return t.errorUnsupported
  }
  return error.detail ?? t.errorRequest(error.status)
}

export interface Contact {
  email: string | null
  phone: string | null
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  listCatalog: () => request<CatalogItem[]>('/api/catalog'),
  listLocations: () => request<string[]>('/api/locations'),
  createCatalogItem: (input: CatalogItemInput) => request<CatalogItem>('/api/catalog', json('POST', input)),
  updateCatalogItem: (id: string, input: CatalogItemInput) => request<CatalogItem>(`/api/catalog/${id}`, json('PUT', input)),
  deleteCatalogItem: (id: string) => request<void>(`/api/catalog/${id}`, { method: 'DELETE' }),
  listInventory: () => request<InventoryItem[]>('/api/inventory'),
  createInventoryItem: (input: InventoryItemInput) =>
    request<InventoryItem>('/api/inventory', json('POST', input)),
  updateInventoryItem: (id: string, input: InventoryItemInput) =>
    request<InventoryItem>(`/api/inventory/${id}`, json('PUT', input)),
  deleteInventoryItem: (id: string) =>
    request<void>(`/api/inventory/${id}`, { method: 'DELETE' }),

  listHeirs: () => request<Heir[]>('/api/heirs'),
  listSexes: () => request<string[]>('/api/sexes'),
  createHeir: (input: HeirInput) => request<Heir>('/api/heirs', json('POST', input)),
  updateHeir: (id: string, input: HeirInput) => request<Heir>(`/api/heirs/${id}`, json('PUT', input)),
  deleteHeir: (id: string) => request<void>(`/api/heirs/${id}`, { method: 'DELETE' }),

  listDocuments: () => request<HeirDocument[]>('/api/documents'),
  listDocumentTypes: () => request<string[]>('/api/document-types'),
  createDocument: (input: DocumentInput) => request<HeirDocument>('/api/documents', json('POST', input)),
  updateDocument: (id: string, input: DocumentInput) =>
    request<HeirDocument>(`/api/documents/${id}`, json('PUT', input)),
  deleteDocument: (id: string) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),

  listContributions: () => request<ContributionYear[]>('/api/contributions'),
  createContributionYear: (year: number) => request<ContributionYear>('/api/contributions', json('POST', { year })),
  deleteContributionYear: (year: number) => request<void>(`/api/contributions/${year}`, { method: 'DELETE' }),
  /** Change the comment on a year; an empty one is removed */
  setContributionComment: (year: number, comment: string) =>
    request<ContributionYear>(`/api/contributions/${year}/comment`, json('PUT', { comment })),
  /** Enter the amount an heir contributed in a year; null removes it */
  setContribution: (year: number, heirId: string, amountEur: number | null) =>
    request<ContributionYear>(`/api/contributions/${year}/${heirId}`, json('PUT', { amountEur })),

  listRenovations: () => request<Renovation[]>('/api/renovations'),
  createRenovation: (input: RenovationInput) => request<Renovation>('/api/renovations', json('POST', input)),
  updateRenovation: (id: string, input: RenovationInput) =>
    request<Renovation>(`/api/renovations/${id}`, json('PUT', input)),
  deleteRenovation: (id: string) => request<void>(`/api/renovations/${id}`, { method: 'DELETE' }),
  setRenovationComment: (id: string, comment: string) =>
    request<Renovation>(`/api/renovations/${id}/comment`, json('PUT', { comment })),
  /** Tick off, or untick, that the heir has paid their part */
  setRenovationPaid: (id: string, heirId: string, paid: boolean) =>
    request<Renovation>(`/api/renovations/${id}/payments/${heirId}`, json('PUT', { paid })),

  /** The buildings and the land parcels, each by name */
  estate: () => request<Estate>('/api/estate'),
  getProperty: (id: string) => request<Property>(`/api/properties/${id}`),
  createProperty: (input: PropertyInput) => request<Property>('/api/properties', json('POST', input)),
  updateProperty: (id: string, input: PropertyInput) => request<Property>(`/api/properties/${id}`, json('PUT', input)),
  deleteProperty: (id: string) => request<void>(`/api/properties/${id}`, { method: 'DELETE' }),
  propertyLabels: () => request<string[]>('/api/property-labels'),
  propertyDocumentTypes: () => request<string[]>('/api/property-document-types'),
  createPropertyDocument: (propertyId: string, input: PropertyDocumentInput) =>
    request<PropertyDocument>(`/api/properties/${propertyId}/documents`, json('POST', input)),
  updatePropertyDocument: (id: string, input: PropertyDocumentInput) =>
    request<PropertyDocument>(`/api/property-documents/${id}`, json('PUT', input)),
  deletePropertyDocument: (id: string) => request<void>(`/api/property-documents/${id}`, { method: 'DELETE' }),

  /** Add a photo after the others, e.g. `addPicture('/api/catalog/<id>', file)` */
  addPicture: (owner: string, file: File) =>
    request<Picture>(`${owner}/pictures`, {
      method: 'POST',
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
      body: file,
    }),
  removePicture: (owner: string, pictureId: string) =>
    request<void>(`${owner}/pictures/${pictureId}`, { method: 'DELETE' }),
  /** Put the photos in this order; the first becomes the cover */
  orderPictures: (owner: string, pictureIds: string[]) =>
    request<void>(`${owner}/pictures/order`, json('PUT', pictureIds)),

  session: () => request<Session>('/api/session'),
  setup: (username: string, password: string) =>
    request<void>('/api/setup', json('POST', { username, password })),
  /** Create an account with the token of an invitation */
  register: (token: string, username: string, password: string) =>
    request<void>('/api/register', json('POST', { token, username, password })),
  /** Resolves to false for a wrong username or password */
  signIn: async (username: string, password: string, remember: boolean): Promise<boolean> => {
    const form = new URLSearchParams({ username, password })
    if (remember) {
      form.set('remember', 'true')
    }
    const response = await send('/api/login', { method: 'POST', body: form })
    if (response.status === 401) {
      return false
    }
    if (!response.ok) {
      throw new ApiError(response.status)
    }
    return true
  },
  signOut: () => request<void>('/api/logout', { method: 'POST' }),
  listUsers: () => request<UserAccount[]>('/api/users'),
  createUser: (username: string, password: string, role: Role, heirId: string | null, contact: Contact) =>
    request<UserAccount>('/api/users', json('POST', { username, password, role, heirId, ...contact })),
  changeContact: (username: string, contact: Contact) =>
    request<UserAccount>(`/api/users/${encodeURIComponent(username)}/contact`, json('PUT', contact)),
  listInvitations: () => request<Invitation[]>('/api/invitations'),
  createInvitation: (role: Role, heirId: string | null) =>
    request<Invitation>('/api/invitations', json('POST', { role, heirId })),
  deleteInvitation: (id: string) => request<void>(`/api/invitations/${id}`, { method: 'DELETE' }),
  myAccount: () => request<OwnAccount>('/api/account'),
  changeMyContact: (contact: Contact) => request<OwnAccount>('/api/account/contact', json('PUT', contact)),
  linkHeir: (username: string, heirId: string | null) =>
    request<UserAccount>(`/api/users/${encodeURIComponent(username)}/heir`, json('PUT', { heirId })),
  changeRole: (username: string, role: Role) =>
    request<UserAccount>(`/api/users/${encodeURIComponent(username)}/role`, json('PUT', { role })),
  resetPassword: (username: string, password: string) =>
    request<void>(`/api/users/${encodeURIComponent(username)}/password`, json('PUT', { password })),
  deleteUser: (username: string) =>
    request<void>(`/api/users/${encodeURIComponent(username)}`, { method: 'DELETE' }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/account/password', json('PUT', { currentPassword, newPassword })),
}
