/**
 * 菜单收藏 —— 按账号分隔，存浏览器 localStorage。
 *
 *   key 格式: menu-favorites:<userId>
 *
 * 多个组件共享同一份响应式状态（模块级单例），但内容随 auth.userId
 * 切换自动重新加载（登入/登出/账号切换都会触发）。
 *
 * 采用 `reactive(new Set())` 而非 `ref([])`：Vue 3 原生支持 Set/Map 的
 * `.add` / `.delete` / `.has` 反应式追踪，避免 ref<Array> 在模块级单例 +
 * 首次 ensureInit 整体 reassign 场景下偶发的下游 computed 不重算问题
 * （症状：点击收藏后需刷新页面才能置顶 / 取消后星星状态不刷新）。
 */
import { reactive, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

const STORAGE_PREFIX = 'menu-favorites:'

const favoriteSet = reactive(new Set())
let currentUserId = null
let initialized = false

function storageKey(userId) {
  return userId ? `${STORAGE_PREFIX}${userId}` : null
}

function readFromStorage(userId) {
  const key = storageKey(userId)
  if (!key) return []
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.map(String) : []
  } catch {
    return []
  }
}

function writeToStorage(userId) {
  const key = storageKey(userId)
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify([...favoriteSet]))
  } catch {
    // ignore quota / serialization errors
  }
}

function loadFavorites(userId) {
  favoriteSet.clear()
  for (const id of readFromStorage(userId)) {
    favoriteSet.add(id)
  }
}

function ensureInit() {
  if (initialized) return
  initialized = true

  const auth = useAuthStore()
  currentUserId = auth.userId || null
  loadFavorites(currentUserId)

  watch(
    () => auth.userId,
    (uid) => {
      currentUserId = uid || null
      loadFavorites(currentUserId)
    }
  )
}

export function useFavoriteMenus() {
  ensureInit()

  function isFavorite(id) {
    return favoriteSet.has(String(id))
  }

  function setFavorite(id, flag) {
    const key = String(id)
    const has = favoriteSet.has(key)
    if (flag && !has) {
      favoriteSet.add(key)
      writeToStorage(currentUserId)
    } else if (!flag && has) {
      favoriteSet.delete(key)
      writeToStorage(currentUserId)
    }
  }

  function toggleFavorite(id) {
    setFavorite(id, !isFavorite(id))
  }

  return {
    favoriteSet,
    isFavorite,
    setFavorite,
    toggleFavorite
  }
}
