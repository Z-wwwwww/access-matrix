/**
 * 即时红点的连接管理 —— SSE 为主(即时),轮询为兜底(可靠)。
 *
 * - SSE:EventSource 不能带 Authorization 头,所以先用已登录态 POST 换一张
 *   一次性 ticket 再连;ticket 用后即焚,故关掉原生自动重连(会复用失效 URL),
 *   改为自管指数退避 + 重新申请 ticket。连上/重连后立刻拉一次未读数对齐。
 * - 轮询兜底:每 20s 拉一次未读数。万一 SSE 被反代缓冲/掐断(开发期 Vite
 *   代理、生产期 Nginx 未放行 text/event-stream 都可能),红点仍会自动更新,
 *   无需手动刷新——只是延迟从“即时”退化到“最多 20s”。
 */
import { onMounted, onUnmounted } from 'vue'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'
import { getSseTicketApi } from '@/services/notification'

const STREAM_URL = '/proxy_url/notification/stream'  // 同 services/request.js 的 baseURL,走 Vite 代理
const POLL_MS = 20000

export function useNotificationStream() {
  const store = useNotificationStore()
  const auth = useAuthStore()
  let es = null
  let retry = 0
  let stopped = false
  let reconnectTimer = null
  let pollTimer = null

  async function connect() {
    if (stopped || !auth.isAuthenticated) return
    try {
      const res = await getSseTicketApi()
      // 组件可能在这次 await 期间就卸载了(切路由/登出)。onUnmounted 那时看到的
      // `es` 还是 null,关不掉这条尚未创建的连接;若不在这里重新判一次 stopped,
      // 下面就会凭空建出一条谁也持有不了的 EventSource ——它不会再被 close,
      // 后端那侧的 SseEmitter 也要挂到 30 分钟超时才释放。
      if (stopped) return
      if (res.data.code !== 0) return scheduleReconnect()
      es = new EventSource(`${STREAM_URL}?ticket=${encodeURIComponent(res.data.data)}`)
      es.onopen = () => {
        retry = 0
        store.fetchUnread()          // 连上即对齐(也兜住断连期间漏推的)
      }
      es.addEventListener('unread', (e) => {
        store.setUnread(e.data)
        retry = 0
      })
      es.onerror = () => {
        es?.close()
        es = null
        scheduleReconnect()
      }
    } catch {
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (stopped) return
    const delay = Math.min(1000 * 2 ** retry++, 30000) // 1s,2s,4s … 封顶 30s
    reconnectTimer = setTimeout(connect, delay)
  }

  onMounted(() => {
    store.fetchUnread()                                  // 首屏立即出角标
    connect()                                            // 即时通道
    pollTimer = setInterval(() => {                      // 兜底通道
      if (!stopped && auth.isAuthenticated) store.fetchUnread()
    }, POLL_MS)
  })

  onUnmounted(() => {
    stopped = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (pollTimer) clearInterval(pollTimer)
    es?.close()
  })
}
