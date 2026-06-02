/**
 * 站内通知 store。
 *
 * `unread` 是铃铛红点的唯一数据源:SSE 推来的未读数直接覆盖它(见
 * useNotificationStream),用户标记已读时本地自减。列表按需懒加载(打开
 * 下拉时 fetchList)。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUnreadCountApi,
  getNotificationListApi,
  markReadApi,
  markAllReadApi,
} from '../../services/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  const list = ref([])
  const loading = ref(false)

  // 通知クリック → ドロワーを開くための“受け渡し”。bizId(対象実体の id)を URL に
  // 出さず store 経由で渡すので、遷移先(例 /demo/task)の URL/keep-alive キャッシュ
  // キーが変わらない = ドロワー多重化が起きない。
  const pendingNav = ref(null)   // { path, bizType, id }
  function setPendingNav(v) { pendingNav.value = v }
  /** path が一致すれば中身を返して消費(一度きり)。一致しなければ null。 */
  function takePendingNav(path) {
    if (pendingNav.value && pendingNav.value.path === path) {
      const v = pendingNav.value
      pendingNav.value = null
      return v
    }
    return null
  }

  /** SSE 推送直接调用,覆盖式更新(后端已从 DB 重算,值是精确的)。 */
  function setUnread(n) {
    unread.value = Math.max(0, Number(n) || 0)
  }

  async function fetchUnread() {
    const res = await getUnreadCountApi()
    if (res.data.code === 0) setUnread(res.data.data)
  }

  async function fetchList() {
    loading.value = true
    try {
      const res = await getNotificationListApi({ page: 1, size: 20 })
      if (res.data.code === 0) list.value = res.data.data.records || []
    } finally {
      loading.value = false
    }
  }

  async function markRead(id) {
    const res = await markReadApi(id)
    if (res.data.code === 0) {
      const item = list.value.find((x) => x.id === id)
      if (item && item.readFlag !== 1) {
        item.readFlag = 1
        unread.value = Math.max(0, unread.value - 1)
      }
    }
  }

  async function markAllRead() {
    const res = await markAllReadApi()
    if (res.data.code === 0) {
      list.value.forEach((x) => (x.readFlag = 1))
      unread.value = 0
    }
  }

  return {
    unread, list, loading, setUnread, fetchUnread, fetchList, markRead, markAllRead,
    pendingNav, setPendingNav, takePendingNav,
  }
})
