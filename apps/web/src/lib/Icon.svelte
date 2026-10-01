<script>
  import { icons } from './icons.js'

  let { name, size = 24 } = $props()
</script>

<svg viewBox="0 0 960 960" width={size} height={size} fill="currentColor" aria-hidden="true"><path d={icons[name]} /></svg>

<style>
  svg {
    flex: none;
  }
</style>
