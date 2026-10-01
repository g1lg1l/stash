import { test } from 'node:test'
import assert from 'node:assert/strict'
import { byDay, rediscovery, relativeTime, search } from './feed.js'

const save = (fields) => ({
  id: crypto.randomUUID(), url: 'https://example.com', title: null, description_text: null, summary: null, author: null,
  category: 'other', source: 'web', tags: [], opened_at: null, last_saved_at: new Date().toISOString(), ...fields,
})

test('search matches every word in any field, ignoring case and accents', () => {
  const sensoji = save({ title: 'Sensō-ji', tags: ['tokyo'], category: 'places' })
  const pasta = save({ title: 'Cacio e pepe', category: 'food' })
  assert.deepEqual(search('SENSO tokyo', [sensoji, pasta]), [sensoji])
  assert.deepEqual(search('food', [sensoji, pasta]), [pasta])
  assert.deepEqual(search('senso pepe', [sensoji, pasta]), [])
  assert.deepEqual(search('  ', [sensoji, pasta]), [])
})

test('sections by calendar day, newest first', () => {
  const now = new Date(2026, 9, 1, 9, 41).getTime()
  const at = (y, m, d, h = 12) => save({ last_saved_at: new Date(y, m, d, h).toISOString() })
  const saves = [at(2026, 9, 1, 8), at(2026, 8, 30, 23), at(2026, 8, 24), at(2026, 8, 23, 23)]
  assert.deepEqual(
    byDay(saves, now).map((s) => [s.title, s.saves.length]),
    [['Today', 1], ['Yesterday', 1], ['Last 7 Days', 1], ['Earlier', 1]],
  )
})

test('worth another look: unseen, three days old or more, oldest first', () => {
  const now = Date.now()
  const daysAgo = (n, fields = {}) => save({ last_saved_at: new Date(now - n * 86400000).toISOString(), ...fields })
  const old = daysAgo(10), older = daysAgo(20), recent = daysAgo(1), seen = daysAgo(30, { opened_at: new Date().toISOString() })
  assert.deepEqual(rediscovery([recent, old, seen, older], now), [older, old])
})

test('relative times read like the apps', () => {
  const now = Date.parse('2026-10-01T12:00:00Z')
  assert.equal(relativeTime('2026-10-01T11:59:30Z', now), 'now')
  assert.equal(relativeTime('2026-10-01T07:00:00Z', now), '5 hours ago')
  assert.equal(relativeTime('2026-09-30T11:00:00Z', now), 'yesterday')
  assert.equal(relativeTime('2026-09-17T12:00:00Z', now), '2 weeks ago')
})
