// Smart categories (#19). pg_cron calls this every minute while saves wait in private.smart_categories_due;
// Gemini picks one of the apps' categories for up to 50 of them at a time, so the free tier's daily requests go
// a long way. Open without a JWT (config.toml), and harmless: a call only works through saves already due.
import postgres from 'npm:postgres@3'

// No prepared statements, in case the URL goes through the pooler in transaction mode.
const sql = postgres(Deno.env.get('SUPABASE_DB_URL')!, { prepare: false })
const key = Deno.env.get('GEMINI_STASH_API_KEY')!
const model = 'gemini-3.5-flash-lite'

// The apps' categories (REQUIREMENTS.md), and what goes in each.
const categories: Record<string, string> = {
  food: 'recipes, cooking, restaurants, drinks',
  travel: 'trips, destinations, hotels, flights, travel guides',
  fitness: 'workouts, sport, running, yoga, healthy eating',
  ideas: 'inspiration, mindset, creativity, philosophy, talks, design, DIY',
  business: 'work, careers, startups, marketing, money, investing, productivity',
  tech: 'software, programming, AI, gadgets, apps, the internet',
  shopping: 'products to buy, deals, fashion, wish lists',
  music: 'songs, albums, artists, playlists, concerts',
  learning: 'tutorials, courses, explainers, science, history, books, languages',
  places: 'a specific place to go: a restaurant, bar, shop, museum or venue on a map',
  other: 'none of the above fits well',
}

const instructions = `You file the links someone saved for later. For each one, pick the category it belongs to.
Titles and descriptions can be in any language, often Italian or English. Pick "other" only when nothing fits.
Categories:
${Object.entries(categories).map(([name, what]) => `- ${name}: ${what}`).join('\n')}
Answer with one entry per link, by its n.`

type Save = { id: string; url: string; source: string; content_type: string; title: string | null; description_text: string | null; author: string | null }

Deno.serve(async () => {
  // Claimed by setting categorized_at, so a run that overlaps this one doesn't send them again.
  const saves = await sql<Save[]>`
    update public.saves as s set categorized_at = now()
    where s.categorized_at is null and s.id in (select id from private.smart_categories_due limit 50)
    returning s.id, s.url, s.source, s.content_type, s.title, s.description_text, s.author`
  if (!saves.length) return new Response(null, { status: 204 })
  try {
    const picks = await pick(saves)
    // categorized_at moves again, which tells keep_smart_category this write is the model's.
    await sql`
      update public.saves as s set category = p.category, categorized_at = now()
      from jsonb_to_recordset(${JSON.stringify(picks)}::jsonb) as p(id uuid, category text)
      where s.id = p.id and not s.category_is_manual`
    return Response.json({ categorized: picks.length })
  } catch (error) {
    // Due again on the next run: rate limited, or Gemini is down. A save the model skipped stays as it is.
    await sql`update public.saves set categorized_at = null where id in ${sql(saves.map((s) => s.id))}`
    console.error(error)
    return new Response(String(error), { status: 502 })
  }
})

async function pick(saves: Save[]): Promise<{ id: string; category: string }[]> {
  const links = saves.map((s, n) => ({
    n, title: s.title, description: s.description_text?.slice(0, 300), author: s.author, link: place(s.url), source: s.source, type: s.content_type,
  }))
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: 'POST',
    headers: { 'x-goog-api-key': key, 'content-type': 'application/json' },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: instructions }] },
      contents: [{ role: 'user', parts: [{ text: JSON.stringify(links) }] }],
      generationConfig: {
        temperature: 0,
        responseMimeType: 'application/json',
        responseJsonSchema: {
          type: 'array',
          items: {
            type: 'object',
            properties: { n: { type: 'integer' }, category: { type: 'string', enum: Object.keys(categories) } },
            required: ['n', 'category'],
          },
        },
      },
    }),
  })
  if (!response.ok) throw new Error(`Gemini answered ${response.status}: ${await response.text()}`)
  const answer = await response.json()
  const items: { n: number; category: string }[] = JSON.parse(answer.candidates[0].content.parts[0].text)
  return items.filter(({ n, category }) => saves[n] && Object.hasOwn(categories, category)).map(({ n, category }) => ({ id: saves[n].id, category }))
}

/** Host and path: what a link says about itself, without share and tracking tokens. */
function place(url: string) {
  try {
    const { host, pathname } = new URL(url)
    return host + pathname
  } catch {
    return url.slice(0, 200)
  }
}
