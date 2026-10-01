// Feed logic ported from the apps (Feed.kt / Rediscovery): rows are the server's snake_case saves.

export const categories = {
  food: { name: 'Food', tint: '#FF9F0A', icon: 'restaurant' },
  travel: { name: 'Travel', tint: '#30B0C7', icon: 'flight' },
  fitness: { name: 'Fitness', tint: '#34C759', icon: 'directions_run' },
  ideas: { name: 'Ideas', tint: '#FFC300', icon: 'lightbulb' },
  business: { name: 'Business', tint: '#5E5CE6', icon: 'work' },
  tech: { name: 'Tech', tint: '#0A84FF', icon: 'memory' },
  shopping: { name: 'Shopping', tint: '#FF375F', icon: 'shopping_bag' },
  music: { name: 'Music', tint: '#BF5AF2', icon: 'music_note' },
  learning: { name: 'Learning', tint: '#00C7BE', icon: 'menu_book' },
  places: { name: 'Places', tint: '#FF453A', icon: 'pin_drop' },
  other: { name: 'Other', tint: '#8E8E93', icon: 'link' },
}

/** Unknown values from a newer app fall back, as on the phones. */
export const category = (save) => categories[save.category] ?? categories.other

const sources = { instagram: 'Instagram', youtube: 'YouTube', tiktok: 'TikTok', reddit: 'Reddit', x: 'X', spotify: 'Spotify', maps: 'Maps', web: 'Web' }
export const sourceName = (save) => sources[save.source] ?? 'Link'

/** Never empty: falls back to the host, then the full URL. */
export function displayTitle(save) {
  if (save.title) return save.title
  try {
    return new URL(save.url).hostname.replace(/^www\./, '')
  } catch {
    return save.url
  }
}

/** Author when known, otherwise where it came from. */
export const byline = (save) => save.author ?? sourceName(save)

/** Lowercase without accents, so "senso" finds "Sensō-ji". */
export const fold = (text) => text.normalize('NFD').replace(/\p{Mn}+/gu, '').toLowerCase()

/** Every word must match some field. Keeps the input order, so newest first stays newest first. */
// ponytail: linear scan per keystroke, fine for thousands of saves, as on the phones.
export function search(query, saves) {
  const terms = query.split(/[ \t\n]/).filter(Boolean).map(fold)
  if (!terms.length) return []
  return saves.filter((save) => {
    const fields = [save.title, save.description_text, save.summary, save.author, category(save).name, sourceName(save), save.url, ...save.tags]
      .filter((field) => field != null)
      .map(fold)
    return terms.every((term) => fields.some((field) => field.includes(term)))
  })
}

const day = 24 * 3600 * 1000
/** Local midnight, `back` days before `now`: calendar days, so a DST change doesn't shift them. */
const midnight = (now, back = 0) => {
  const date = new Date(now)
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - back).getTime()
}

/** Today / Yesterday / Last 7 Days / Earlier. Expects saves sorted newest first. */
export function byDay(saves, now = Date.now()) {
  const [today, yesterday, weekAgo] = [midnight(now), midnight(now, 1), midnight(now, 7)]
  const sections = []
  for (const save of saves) {
    const at = Date.parse(save.last_saved_at)
    const title = at >= today ? 'Today' : at >= yesterday ? 'Yesterday' : at >= weekAgo ? 'Last 7 Days' : 'Earlier'
    if (sections.at(-1)?.title === title) sections.at(-1).saves.push(save)
    else sections.push({ title, saves: [save] })
  }
  return sections
}

/** "Worth another look": unseen saves old enough to have slipped your mind, oldest first. Opening one retires it. */
export function rediscovery(saves, now = Date.now(), limit = 6) {
  const cutoff = now - 3 * day
  return saves
    .filter((save) => save.opened_at == null && Date.parse(save.last_saved_at) <= cutoff)
    .sort((a, b) => Date.parse(a.last_saved_at) - Date.parse(b.last_saved_at))
    .slice(0, limit)
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** "5 hours ago", "yesterday", "2 weeks ago", as the apps say it. */
export function relativeTime(iso, now = Date.now()) {
  const minutes = Math.floor((now - Date.parse(iso)) / 60000)
  if (minutes < 1) return 'now'
  const [amount, unit] =
    minutes < 60 ? [minutes, 'minute']
    : minutes < 24 * 60 ? [Math.floor(minutes / 60), 'hour']
    : minutes < 7 * 24 * 60 ? [Math.floor(minutes / (24 * 60)), 'day']
    : minutes < 30 * 24 * 60 ? [Math.floor(minutes / (7 * 24 * 60)), 'week']
    : minutes < 365 * 24 * 60 ? [Math.floor(minutes / (30 * 24 * 60)), 'month']
    : [Math.floor(minutes / (365 * 24 * 60)), 'year']
  return relative.format(-amount, unit)
}

export function greeting(now = new Date()) {
  const hour = now.getHours()
  return hour < 5 ? 'Good evening' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}
