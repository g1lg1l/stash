import { svelte } from '@sveltejs/vite-plugin-svelte'
import { defineConfig } from 'vite'

// Served from GitHub Pages at g1lg1l.github.io/stash/.
export default defineConfig({
  base: '/stash/',
  plugins: [svelte()],
})
