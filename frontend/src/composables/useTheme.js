import { ref, computed, watch } from 'vue'

const STORAGE_KEY_MODE = 'theme'
const STORAGE_KEY_PALETTE = 'theme-palette'

/**
 * Accent catalog for the single warm theme. All variants share the warm
 * off-white surfaces (#f4efe9 bg + #ffffff cards) defined by the base `:root`
 * / `.dark` rules in main.css — only the brand accent differs. `warm`
 * (terracotta) is the default and sets no `data-palette` attribute; the others
 * are applied as `data-palette="brick|ochre|pine|slate"`. Each entry exposes a
 * 3-color swatch (background, card surface, accent) for the picker.
 */
const WARM_BG = '#f4efe9'
const WARM_CARD = '#ffffff'
export const PALETTES = [
  { value: 'warm',  label: '陶土铁锈', swatch: { bg: WARM_BG, card: WARM_CARD, primary: '#b5532f' } },
  { value: 'brick', label: '砖红',     swatch: { bg: WARM_BG, card: WARM_CARD, primary: '#a8392f' } },
  { value: 'ochre', label: '赭黄',     swatch: { bg: WARM_BG, card: WARM_CARD, primary: '#9a6b2a' } },
  { value: 'pine',  label: '暗松绿',   swatch: { bg: WARM_BG, card: WARM_CARD, primary: '#3f6f5e' } },
  { value: 'slate', label: '灰紫',     swatch: { bg: WARM_BG, card: WARM_CARD, primary: '#5a5470' } }
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
