import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'menu-favorites'

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return []
  }
}

const favoriteIds = ref(load())

watch(
  favoriteIds,
  (val) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
    } catch {
      // ignore
    }
  },
  { deep: true }
)

export function useFavoriteMenus() {
  const favoriteSet = computed(() => new Set(favoriteIds.value.map(String)))

  function isFavorite(id) {
    return favoriteSet.value.has(String(id))
  }

  function setFavorite(id, flag) {
    const key = String(id)
    const idx = favoriteIds.value.indexOf(key)
    if (flag && idx === -1) favoriteIds.value.push(key)
    if (!flag && idx !== -1) favoriteIds.value.splice(idx, 1)
  }

  function toggleFavorite(id) {
    setFavorite(id, !isFavorite(id))
  }

  return {
    favoriteIds,
    favoriteSet,
    isFavorite,
    setFavorite,
    toggleFavorite
  }
}
