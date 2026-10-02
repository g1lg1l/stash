import { svelte } from '@sveltejs/vite-plugin-svelte'
import { defineConfig, loadEnv } from 'vite'

// Served from GitHub Pages at g1lg1l.github.io/stash/, which can't send headers: the policy goes in a meta tag.
// Build only, so the dev server's hot reload keeps its websocket. Scripts only from the page's own files,
// so an injected one can't reach the session in localStorage.
export default defineConfig(({ mode }) => {
  const api = loadEnv(mode, process.cwd()).VITE_SUPABASE_URL ?? ''
  const csp = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    'font-src https://fonts.gstatic.com',
    "img-src 'self' https: data:",
    `connect-src https://*.supabase.co ${api}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
  ].join('; ')
  return {
    base: '/stash/',
    plugins: [
      svelte(),
      {
        name: 'content-security-policy',
        apply: 'build',
        transformIndexHtml: () => [{ tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: csp }, injectTo: 'head-prepend' }],
      },
    ],
  }
})
