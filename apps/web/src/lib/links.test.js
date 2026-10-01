import { test } from 'node:test'
import assert from 'node:assert/strict'
import { analyze, pastedLink } from './links.js'

// Produced by the Android UrlSourceDetector: the web must give every device the same dedupe key.
const fromAndroid = [
  ["https://www.instagram.com/reel/C8x2kLmN4pQ/?igsh=abc", "https://instagram.com/reel/C8x2kLmN4pQ", "instagram", "video"],
  ["https://instagram.com/p/C8x2kLmN4pQ/", "https://instagram.com/p/C8x2kLmN4pQ", "instagram", "post"],
  ["https://www.youtube.com/watch?v=kCc8FmEb1nY&t=42s&si=abc", "https://youtube.com/watch?v=kCc8FmEb1nY", "youtube", "video"],
  ["https://youtu.be/kCc8FmEb1nY?si=xyz", "https://youtube.com/watch?v=kCc8FmEb1nY", "youtube", "video"],
  ["https://youtube.com/shorts/Qm3vX8pLw2E", "https://youtube.com/watch?v=Qm3vX8pLw2E", "youtube", "video"],
  ["https://music.youtube.com/watch?v=abc", "https://music.youtube.com/watch?v=abc", "youtube", "music"],
  ["http://youtube.com/embed/kCc8FmEb1nY", "https://youtube.com/watch?v=kCc8FmEb1nY", "youtube", "video"],
  ["https://www.youtube.com/playlist?list=PL1&si=x", "https://youtube.com/playlist?list=PL1", "youtube", "video"],
  ["https://www.youtube.com/@channel", "https://youtube.com/@channel", "youtube", "video"],
  ["https://www.tiktok.com/@chef/video/7401928374650182913?is_from_webapp=1", "https://tiktok.com/@chef/video/7401928374650182913", "tiktok", "video"],
  ["https://vm.tiktok.com/ZMabc123/", "https://vm.tiktok.com/ZMabc123", "tiktok", "video"],
  ["https://old.reddit.com/r/swift/comments/abc/title/?share_id=9", "https://reddit.com/r/swift/comments/abc/title", "reddit", "post"],
  ["https://redd.it/abc", "https://redd.it/abc", "reddit", "post"],
  ["https://twitter.com/someone/status/20?s=20&t=abc", "https://x.com/i/status/20", "x", "post"],
  ["https://x.com/jack", "https://x.com/jack", "x", "post"],
  ["https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", "spotify", "music"],
  ["https://maps.google.com/?q=Senso-ji", "https://maps.google.com/?q=Senso-ji", "maps", "place"],
  ["https://www.google.com/maps/place/Senso-ji/@35.7,139.7,17z?entry=ttu", "https://google.com/maps/place/Senso-ji/@35.7,139.7,17z?entry=ttu", "maps", "place"],
  ["https://maps.app.goo.gl/AbC123", "https://maps.app.goo.gl/AbC123", "maps", "place"],
  ["https://maps.apple.com/?q=Kyoto&ll=35.0,135.7", "https://maps.apple.com/?ll=35.0%2C135.7&q=Kyoto", "maps", "place"],
  ["https://paulgraham.com/startupideas.html", "https://paulgraham.com/startupideas.html", "web", "article"],
  ["https://www.amazon.com/dp/B0CHX1W1XY?ref_=abc&tag=x", "https://amazon.com/dp/B0CHX1W1XY?ref_=abc&tag=x", "web", "product"],
  ["https://example.com", "https://example.com/", "web", "other"],
  ["http://www.example.com/post/#comments", "https://example.com/post", "web", "article"],
  ["https://example.com/post?utm_source=x&utm_medium=y&b=2&a=1", "https://example.com/post?a=1&b=2", "web", "article"],
  ["https://shop.example.com/item?color=red&id=5&gclid=1", "https://shop.example.com/item?color=red&id=5", "web", "article"],
  ["https://app.example.com/#/inbox", "https://app.example.com/#/inbox", "web", "other"],
  ["https://example.com:8443/a/b/", "https://example.com:8443/a/b", "web", "article"],
  ["https://example.com/search?q=caf%C3%A9+cr%C3%A8me&flag&empty=", "https://example.com/search?empty=&flag&q=caf%C3%A9%20cr%C3%A8me", "web", "article"],
  ["https://example.com/path%20with/caf%C3%A9?x=a,b(c)*d!e'f~g", "https://example.com/path%20with/caf%C3%A9?x=a%2Cb%28c%29*d%21e%27f%7Eg", "web", "article"],
  ["https://example.com/a^b|c?q=1%2B1", "https://example.com/a%5Eb%7Cc?q=1%2B1", "web", "article"],
  ["https://EXAMPLE.com/Page", "https://example.com/Page", "web", "article"],
  ["https://de.wikipedia.org/wiki/K%C3%B6ln", "https://de.wikipedia.org/wiki/K%C3%B6ln", "web", "article"],
  ["https://example.com/?a=1&a=0", "https://example.com/?a=0&a=1", "web", "other"],
  ["https://m.facebook.com/story.php?story_fbid=1&id=2&fbclid=z", "https://facebook.com/story.php?id=2&story_fbid=1", "web", "article"],
]

test('canonical keys, sources and types match the apps', () => {
  for (const [url, key, source, type] of fromAndroid) {
    assert.deepEqual(
      (({ canonical_url, source, content_type }) => [canonical_url, source, content_type])(analyze(url)),
      [key, source, type],
      url,
    )
  }
})

test('accepts links without a scheme and keeps the link as pasted', () => {
  assert.equal(analyze('  youtu.be/kCc8FmEb1nY\n').url, 'https://youtu.be/kCc8FmEb1nY')
  const shared = 'https://www.youtube.com/watch?v=kCc8FmEb1nY&t=42s&si=abc'
  assert.equal(analyze(shared).url, shared)
})

test('rejects what is not a web link', () => {
  for (const text of ['', 'not a url', 'mailto:hi@example.com', 'tel:+391234', 'spotify:track:123', 'hi@example.com',
    'ftp://example.com/file', 'localhost', 'https://', 'https://.com']) {
    assert.equal(analyze(text), null, text)
  }
})

test('finds the link inside pasted text', () => {
  assert.equal(pastedLink('Check this out 😍 https://www.instagram.com/reel/C8x/?igsh=abc via @friend'), 'https://www.instagram.com/reel/C8x/?igsh=abc')
  assert.equal(pastedLink('(see https://en.wikipedia.org/wiki/Pasta_(food)).'), 'https://en.wikipedia.org/wiki/Pasta_(food)')
  assert.equal(pastedLink('youtu.be/abc'), 'youtu.be/abc')
  assert.equal(pastedLink('just words'), null)
})
