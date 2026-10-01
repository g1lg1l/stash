// What a pasted link is, judged from the URL alone: a port of the apps' UrlSourceDetector.
// canonical_url is the dedupe key every device compares, so it must come out byte for byte as on
// Android (OkHttp's encoding rules) and iOS. links.test.js pins the exact strings.

const schemePattern = /^[a-zA-Z][a-zA-Z0-9+.-]*:/

/** Any http(s) link, with or without a scheme ("youtu.be/abc"). Null for anything that isn't a web link. */
export function analyze(string) {
  const trimmed = string.trim()
  if (!trimmed || /\s/.test(trimmed)) return null
  const scheme = trimmed.match(schemePattern)?.[0].slice(0, -1).toLowerCase()
  let withScheme
  if (scheme === undefined) {
    if (trimmed.includes('@')) return null // A bare "a@b.com" is an email.
    withScheme = `https://${trimmed}`
  } else if (scheme === 'http' || scheme === 'https') {
    withScheme = trimmed
  } else {
    return null // mailto:, tel:, spotify:
  }
  let url
  try {
    url = new URL(withScheme)
  } catch {
    return null
  }
  const host = url.hostname
  if (!host.includes('.') || host.startsWith('.') || host.endsWith('.')) return null

  const bareHost = removeFirstPrefix(host, 'www.', 'm.', 'mobile.')
  const path = encodedPath(url)
  const source = sourceOf(bareHost, path)
  return {
    url: url.href,
    canonical_url: canonicalKey(url, bareHost, path, source),
    source,
    content_type: contentTypeOf(source, bareHost, path.toLowerCase()),
  }
}

const linkPattern = /https?:\/\/[^\s<>"]+/gi

/** The first http(s) link inside pasted text ("Look at this 😍 https://…"), else the text itself if it's a link. */
export function pastedLink(text) {
  for (const [match] of text.matchAll(linkPattern)) {
    const link = trimTrailingPunctuation(match)
    if (analyze(link)) return link
  }
  return analyze(text) ? text.trim() : null
}

// ponytail: trailing-punctuation heuristic like most linkifiers, as in the apps.
function trimTrailingPunctuation(link) {
  let end = link.length
  const count = (s, c) => s.split(c).length - 1
  while (end > 0) {
    const c = link[end - 1]
    const head = link.slice(0, end)
    const unbalancedParen = c === ')' && count(head, '(') < count(head, ')')
    if (`.,;:!?'"”’»]}`.includes(c) || unbalancedParen) end--
    else break
  }
  return link.slice(0, end)
}

function sourceOf(host, path) {
  const matches = (...domains) => domains.some((d) => host === d || host.endsWith(`.${d}`))
  if (matches('instagram.com', 'instagr.am')) return 'instagram'
  if (matches('youtube.com', 'youtu.be', 'youtube-nocookie.com')) return 'youtube'
  if (matches('tiktok.com')) return 'tiktok'
  if (matches('reddit.com', 'redd.it')) return 'reddit'
  if (matches('x.com', 'twitter.com')) return 'x'
  if (matches('spotify.com', 'spotify.link')) return 'spotify'
  if (
    matches('maps.app.goo.gl', 'maps.apple.com', 'maps.google.com') ||
    (matches('google.com') && path.startsWith('/maps')) ||
    (host === 'goo.gl' && path.startsWith('/maps'))
  ) return 'maps'
  return 'web'
}

function contentTypeOf(source, host, path) {
  switch (source) {
    case 'youtube': return host.startsWith('music.') ? 'music' : 'video'
    case 'tiktok': return 'video'
    case 'instagram': return ['/reel/', '/reels/', '/tv/'].some((p) => path.includes(p)) ? 'video' : 'post'
    case 'reddit':
    case 'x': return 'post'
    case 'spotify': return 'music'
    case 'maps': return 'place'
    default:
      // Metadata refines this later (og:type); the URL only gives strong hints.
      if (['/product/', '/products/', '/dp/', '/gp/product/', '/item/', '/itm/'].some((p) => path.includes(p))) return 'product'
      return path === '' || path === '/' ? 'other' : 'article'
  }
}

/** Query items that never identify a resource. */
const trackingItems = new Set(['fbclid', 'gclid', 'dclid', 'msclkid', 'mc_cid', 'mc_eid', 'igsh', 'igshid', '_ga', 'ref_src', 'ref_url'])

function canonicalKey(url, bareHost, encoded, source) {
  let host = bareHost
  let path = encoded
  let query = queryItems(url.search)
  const parts = encoded.split('/').filter(Boolean).map(decode)

  switch (source) {
    case 'youtube': {
      // youtu.be/ID, /shorts/ID, /embed/ID, /live/ID and /watch?v=ID are the same video.
      let id = null
      if (bareHost === 'youtu.be') id = parts[0] ?? null
      else if (parts.length >= 2 && ['shorts', 'embed', 'live', 'v'].includes(parts[0])) id = parts[1]
      else if (parts[0] === 'watch') id = query.find(([name]) => name === 'v')?.[1] ?? null
      if (id !== null) {
        host = bareHost.startsWith('music.') ? bareHost : 'youtube.com'
        path = '/watch'
        query = [['v', id]]
      } else {
        if (bareHost === 'youtu.be') host = 'youtube.com'
        query = query.filter(([name]) => name === 'list') // Playlists are identified by list=.
      }
      break
    }
    case 'x':
      host = 'x.com'
      // x.com/<anyone>/status/ID resolves to the same post whatever the handle.
      if (parts.length >= 3 && parts[1] === 'status') path = `/i/status/${parts[2]}`
      query = []
      break
    case 'reddit':
      if (bareHost !== 'redd.it') host = 'reddit.com' // old., new., np. are the same post
      query = []
      break
    case 'instagram':
    case 'tiktok':
    case 'spotify':
      query = [] // Their query strings are share and tracking tokens only.
      break
    default:
      query = query.filter(([name]) => {
        const lower = name.toLowerCase()
        return !lower.startsWith('utm_') && !trackingItems.has(lower)
      })
  }

  if (path.length > 1 && path.endsWith('/')) path = path.slice(0, -1)
  let key = `https://${host}${url.port ? `:${url.port}` : ''}${path}`
  if (query.length) {
    query.sort(([a, x], [b, y]) => (a < b ? -1 : a > b ? 1 : (x ?? '') < (y ?? '') ? -1 : (x ?? '') > (y ?? '') ? 1 : 0))
    key += '?' + query.map(([name, value]) => encodeQuery(name) + (value === null ? '' : `=${encodeQuery(value)}`)).join('&')
  }
  // Plain anchors (#section) are the same page; hash routes (#/page, #!/page) are not.
  const fragment = url.hash ? decode(url.hash.slice(1)) : null
  if (fragment && (fragment.startsWith('/') || fragment.startsWith('!'))) key += `#${fragment}`
  return key
}

// OkHttp keeps `^` and `|` encoded in paths, where WHATWG URLs leave them as they are.
const encodedPath = (url) => url.pathname.replace(/\^/g, '%5E').replace(/\|/g, '%7C')

/** Decoded name/value pairs as OkHttp reads them: `+` is a space, and `?flag` has no value (null), unlike `?flag=`. */
function queryItems(search) {
  if (!search) return []
  return search.slice(1).split('&').map((piece) => {
    const at = piece.indexOf('=')
    const decodeQuery = (s) => decode(s.replace(/\+/g, ' '))
    return at === -1 ? [decodeQuery(piece), null] : [decodeQuery(piece.slice(0, at)), decodeQuery(piece.slice(at + 1))]
  })
}

// OkHttp's query component encode set, plus `%`, `+` and anything outside printable ASCII.
const queryReserved = new Set(' !"#$&\'(),/:;<=>?@[]\\^`{|}~%+')

function encodeQuery(text) {
  let out = ''
  for (const char of text) {
    const code = char.codePointAt(0)
    if (code > 0x20 && code < 0x7f && !queryReserved.has(char)) out += char
    else for (const byte of new TextEncoder().encode(char)) out += `%${byte.toString(16).toUpperCase().padStart(2, '0')}`
  }
  return out
}

function decode(text) {
  try {
    return decodeURIComponent(text)
  } catch {
    return text
  }
}

function removeFirstPrefix(text, ...prefixes) {
  const prefix = prefixes.find((p) => text.startsWith(p))
  return prefix ? text.slice(prefix.length) : text
}
