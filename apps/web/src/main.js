import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'

// Light or dark when picked in Settings; otherwise the system's.
try {
  const theme = localStorage.getItem('theme')
  if (theme === 'light' || theme === 'dark') document.documentElement.dataset.theme = theme
} catch {}

mount(App, { target: document.getElementById('app') })
