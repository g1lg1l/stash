// The web client's state: it has no local database and reads and writes Supabase directly
// (REQUIREMENTS.md, "Account and sync"). Plain fetch, no SDK, like the apps.
import { analyze, pastedLink } from './links.js'

// Public by design: row level security protects the data. A `.env.local` can point `npm run dev` at
// the local stack (`supabase start`): VITE_SUPABASE_URL=http://127.0.0.1:54321 and its VITE_SUPABASE_KEY.
const base = import.meta.env.VITE_SUPABASE_URL ?? 'https://kzgmhvqbvrylwydqskcm.supabase.co'
const key = import.meta.env.VITE_SUPABASE_KEY ?? 'sb_publishable_dCk3njz-F4vws7Y5ky6XNA__pOe081Z'

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem('session')) ?? null
  } catch {
    return null
  }
}

/**
 * The confirmation email's link: Supabase checks it, then opens this page (the project's Site URL) with the
 * new session in the fragment, or why it failed (expired, already used). Read once, then out of the address bar.
 */
function fromEmailLink() {
  const params = new URLSearchParams(location.hash.slice(1))
  if (!params.has('access_token') && !params.has('error')) return null
  history.replaceState(null, '', location.pathname + location.search)
  if (params.has('error')) {
    const used = params.get('error_code') === 'otp_expired'
    return { confirmed: false, reason: used ? 'This link has expired or was already used.' : params.get('error_description') ?? "This link didn't work." }
  }
  let email = null
  try {
    email = JSON.parse(atob(params.get('access_token').split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).email
  } catch {}
  const session = {
    accessToken: params.get('access_token'),
    refreshToken: params.get('refresh_token'),
    expiresAt: Number(params.get('expires_at')) || Math.floor(Date.now() / 1000) + Number(params.get('expires_in') ?? 3600),
    email,
  }
  try {
    localStorage.setItem('session', JSON.stringify(session))
  } catch {}
  return { confirmed: true, email, session }
}

/** Set when this page was opened from a confirmation email. */
export const emailLink = fromEmailLink()

export const stash = $state({
  session: emailLink?.session ?? loadSession(),
  /** Newest first, without deleted ones. Null until the first load. */
  saves: null,
  /** The one message at the bottom of the screen: { text, undo? }. */
  notice: null,
})

function setSession(session) {
  stash.session = session
  try {
    if (session) localStorage.setItem('session', JSON.stringify(session))
    else localStorage.removeItem('session')
  } catch {
    // Private mode: signed in until the tab closes.
  }
}

class ServerError extends Error {}

const offline = "Couldn't connect. Check your connection and try again."

async function request(method, path, { body, token, prefer } = {}) {
  let response
  try {
    response = await fetch(`${base}/${path}`, {
      method,
      headers: {
        apikey: key,
        'Content-Type': 'application/json',
        ...(token && { Authorization: `Bearer ${token}` }),
        ...(prefer && { Prefer: prefer }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ServerError(offline)
  }
  const text = await response.text()
  const json = text ? JSON.parse(text) : null
  if (!response.ok) {
    // Auth answers `msg` (or `error_description`), the REST API `message`.
    const error = new ServerError(json?.msg ?? json?.error_description ?? json?.message ?? `The server answered ${response.status}.`)
    error.status = response.status
    throw error
  }
  return json
}

const toSession = (json, email) =>
  json?.access_token
    ? {
        accessToken: json.access_token, refreshToken: json.refresh_token, expiresAt: json.expires_at, email: json.user?.email ?? email,
        smartCategories: json.user?.user_metadata?.smart_categories === true,
      }
    : null

let refreshing = null

/** One refresh at a time; a dead refresh token signs out. */
function refresh() {
  refreshing ??= request('POST', 'auth/v1/token?grant_type=refresh_token', { body: { refresh_token: stash.session.refreshToken } })
    .then((json) => setSession(toSession(json, stash.session.email)))
    .catch((error) => {
      if (error.status >= 400 && error.status < 500) {
        setSession(null)
        stash.saves = null
        throw new ServerError('Signed out. Sign in again to keep syncing.')
      }
      throw error
    })
    .finally(() => (refreshing = null))
  return refreshing
}

/** Signed in: refreshes a minute before expiry, and once more after a 401. */
async function authorized(method, path, options = {}) {
  if (!stash.session) throw new ServerError('Sign in again to keep syncing.')
  if (Date.now() / 1000 > stash.session.expiresAt - 60) await refresh()
  try {
    return await request(method, path, { ...options, token: stash.session.accessToken })
  } catch (error) {
    if (error.status !== 401) throw error
    await refresh()
    return request(method, path, { ...options, token: stash.session.accessToken })
  }
}

// Account

/** Returns a note instead when the server wants the email confirmed first. */
export async function signIn(email, password, create) {
  // The confirmation link comes back to this page wherever it runs (GitHub Pages, or the dev server).
  const back = encodeURIComponent(location.origin + import.meta.env.BASE_URL)
  const json = await request('POST', create ? `auth/v1/signup?redirect_to=${back}` : 'auth/v1/token?grant_type=password', { body: { email, password } })
  const session = toSession(json, email)
  if (!session) return 'Check your email to confirm, then sign in.'
  setSession(session)
  await load()
  return null
}

export function signOut() {
  const token = stash.session?.accessToken
  if (token) request('POST', 'auth/v1/logout', { token }).catch(() => {}) // Best effort.
  setSession(null)
  stash.saves = null
}

/** The account and every save on the server. The phones keep their own copies. */
export async function deleteAccount() {
  await authorized('POST', 'rest/v1/rpc/delete_account', { body: {} })
  signOut()
}

/**
 * Smart categories run on the server, so the choice lives with the account (its user metadata), where every
 * device sees it. Sign-in and each refresh bring back what another device set.
 */
export async function setSmartCategories(on) {
  await authorized('PUT', 'auth/v1/user', { body: { data: { smart_categories: on } } })
  setSession({ ...stash.session, smartCategories: on })
}

// Saves

const page = 1000

/** Everything, again. */
// ponytail: refetches all saves on every focus; pull by updated_at like the phones if accounts get into the tens of thousands.
export async function load() {
  const saves = []
  for (let offset = 0; ; offset += page) {
    const rows = await authorized('GET', `rest/v1/saves?select=*&deleted_at=is.null&order=last_saved_at.desc,id.asc&limit=${page}&offset=${offset}`)
    saves.push(...rows)
    if (rows.length < page) break
  }
  stash.saves = saves
}

/** Changes a save here at once and on the server, and puts it back as it was if the server says no. */
async function change(save, fields) {
  const before = { ...save }
  Object.assign(save, fields)
  try {
    await authorized('PATCH', `rest/v1/saves?id=eq.${save.id}`, { body: fields, prefer: 'return=minimal' })
  } catch (error) {
    Object.assign(save, before)
    tell(error.message)
  }
}

const now = () => new Date().toISOString()

export const setSeen = (save, seen) => (save.opened_at != null) !== seen && change(save, { opened_at: seen ? now() : null })
export const setCategory = (save, category) => change(save, { category, category_is_manual: true })

/** Stashes a pasted link, or moves it to the top when it's already there. Phones fill in the details on their next sync. */
export async function add(text) {
  const link = pastedLink(text)
  const analysis = link && analyze(link)
  if (!analysis) return tell("That isn't a web link. Paste one that starts with https://, or a site like youtu.be/…")
  const existing = stash.saves.find((save) => save.canonical_url === analysis.canonical_url)
  if (existing) {
    stash.saves = [existing, ...stash.saves.filter((save) => save.id !== existing.id)]
    await change(existing, { last_saved_at: now() })
    return tell('Already in your stash. Moved it to the top.')
  }
  const at = now()
  const save = {
    id: crypto.randomUUID(), ...analysis, category: 'other', status: 'pending', category_is_manual: false,
    title: null, description_text: null, thumbnail_url: null, author: null, tags: [], summary: null,
    created_at: at, last_saved_at: at, opened_at: null, deleted_at: null,
  }
  stash.saves = [save, ...stash.saves]
  try {
    await authorized('POST', 'rest/v1/saves', { body: save, prefer: 'return=minimal' })
    tell('Saved to your stash.')
  } catch (error) {
    stash.saves = stash.saves.filter((s) => s.id !== save.id)
    tell(error.message)
  }
  return save
}

/** Deletes everywhere (a tombstone the phones pick up), with Undo. */
export async function remove(save) {
  // By id: saves in $state are proxies, never === the object they were made from.
  const index = stash.saves.findIndex((s) => s.id === save.id)
  stash.saves = stash.saves.filter((s) => s.id !== save.id)
  try {
    await authorized('PATCH', `rest/v1/saves?id=eq.${save.id}`, { body: { deleted_at: now() }, prefer: 'return=minimal' })
  } catch (error) {
    stash.saves = stash.saves.toSpliced(index, 0, save)
    return tell(error.message)
  }
  tell('Removed from your stash.', async () => {
    stash.saves = stash.saves.toSpliced(Math.min(index, stash.saves.length), 0, save)
    try {
      await authorized('PATCH', `rest/v1/saves?id=eq.${save.id}`, { body: { deleted_at: null }, prefer: 'return=minimal' })
    } catch (error) {
      stash.saves = stash.saves.filter((s) => s.id !== save.id)
      tell(error.message)
    }
  })
}

let noticeTimer
export function tell(text, undo) {
  clearTimeout(noticeTimer)
  stash.notice = { text, undo }
  noticeTimer = setTimeout(() => (stash.notice = null), undo ? 6000 : 4000)
}
