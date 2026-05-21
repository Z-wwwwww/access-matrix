import { ref, computed, watch } from 'vue'

const STORAGE_KEY_MODE = 'theme'
const STORAGE_KEY_PALETTE = 'theme-palette'

/**
 * Palette catalog. The `warm` palette is rendered by the base `:root` /
 * `.dark` rules in main.css — no `data-palette` attribute is set for it.
 * Each entry exposes a 3-color swatch (background, card surface, primary
 * accent). Most palettes preview using light-mode tokens, but dark-by-
 * personality palettes (Black, Midnight) preview with their dark-mode
 * tokens so the picker conveys their identity at a glance.
 */
export const PALETTES = [
  // Warm Classic is the project's original design system — restored as the
  // default. No data-palette attribute set when this palette is active.
  {
    value: 'warm',
    label: 'Warm Classic',
    swatch: {
      bg: 'hsl(30, 15%, 94%)',
      card: 'hsl(30, 20%, 99%)',
      primary: 'hsl(16, 85%, 38%)'
    }
  },
  // Black previews using its dark-mode tokens (monochrome surfaces +
  // electric blue accent) so users can tell it apart from the chromatic-
  // light palettes at a glance.
  {
    value: 'black',
    label: 'Black',
    swatch: {
      bg: 'hsl(0, 0%, 4%)',
      card: 'hsl(0, 0%, 8%)',
      primary: 'hsl(217, 91%, 65%)'
    }
  },
  {
    value: 'indigo',
    label: 'Indigo',
    swatch: {
      bg: 'hsl(210, 40%, 98%)',
      card: 'hsl(0, 0%, 100%)',
      primary: 'hsl(244, 76%, 58%)'
    }
  },
  {
    value: 'sand',
    label: 'Sand',
    swatch: {
      bg: 'hsl(60, 9%, 98%)',
      card: 'hsl(0, 0%, 100%)',
      primary: 'hsl(28, 86%, 35%)'
    }
  },
  {
    value: 'mist',
    label: 'Mist',
    swatch: {
      bg: 'hsl(210, 40%, 98%)',
      card: 'hsl(0, 0%, 100%)',
      primary: 'hsl(200, 98%, 39%)'
    }
  },
  {
    value: 'sage',
    label: 'Sage',
    swatch: {
      bg: 'hsl(0, 0%, 98%)',
      card: 'hsl(0, 0%, 100%)',
      primary: 'hsl(160, 84%, 30%)'
    }
  },
  {
    value: 'graphite',
    label: 'Graphite',
    swatch: {
      bg: 'hsl(0, 0%, 98%)',
      card: 'hsl(0, 0%, 100%)',
      primary: 'hsl(346, 77%, 50%)'
    }
  },
  {
    value: 'midnight',
    label: 'Midnight',
    swatch: {
      bg: 'hsl(220, 30%, 4%)',
      card: 'hsl(220, 25%, 8%)',
      primary: 'hsl(189, 94%, 55%)'
    }
  }
]

const PALETTE_VALUES = PALETTES.map(p => p.value)

function getInitialMode() {
  const stored = localStorage.getItem(STORAGE_KEY_MODE)
  if (stored === 'dark' || stored === 'light') return stored
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getInitialPalette() {
  const stored = localStorage.getItem(STORAGE_KEY_PALETTE)
  return PALETTE_VALUES.includes(stored) ? stored : 'warm'
}

const mode = ref(getInitialMode())
const palette = ref(getInitialPalette())

function applyMode(value) {
  const root = document.documentElement
  root.classList.toggle('dark', value === 'dark')
  localStorage.setItem(STORAGE_KEY_MODE, value)
}

function applyPalette(value) {
  const root = document.documentElement
  if (value === 'warm') {
    root.removeAttribute('data-palette')
  } else {
    root.setAttribute('data-palette', value)
  }
  localStorage.setItem(STORAGE_KEY_PALETTE, value)
}

// Apply on module init so the very first paint already reflects user choice.
applyMode(mode.value)
applyPalette(palette.value)

// Module-level watchers — registered once, not per useTheme() call.
watch(mode, applyMode)
watch(palette, applyPalette)

export function useTheme() {
  function toggleTheme() {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  function setPalette(value) {
    if (PALETTE_VALUES.includes(value)) {
      palette.value = value
    }
  }

  return {
    theme: mode,
    toggleTheme,
    palette,
    setPalette,
    palettes: PALETTES,
    isDark: computed(() => mode.value === 'dark')
  }
}
