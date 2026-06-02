/**
 * 站内通知 service —— 未読数 / 一覧 / 既読 / 全既読 + SSE ticket。
 *
 * 自スコープ:バックエンドが現在ユーザー(recipient_user_id)で絞るので userId は不要。
 * SSE ストリームは axios を通さない(EventSource は Bearer ヘッダを付けられない)。
 * useNotificationStream が一回限りの ticket で `/proxy_url/notification/stream` に接続する。
 */
import request from './request'

/** 現在ユーザーの未読数。 */
export function getUnreadCountApi() {
  return request.get('/notification/unread-count')
}

/** ページング受信箱。readFlag 省略=全部 / 0=未読 / 1=既読。 */
export function getNotificationListApi(params) {
  return request.get('/notification/list', { params })
}

/** 1 件を既読にする。 */
export function markReadApi(id) {
  return request.post(`/notification/${id}/read`)
}

/** すべて既読にする。 */
export function markAllReadApi() {
  return request.post('/notification/read-all')
}

/** 一回限りの SSE ticket を取得(認証済み)。res.data.data = ticket 文字列。 */
export function getSseTicketApi() {
  return request.post('/notification/sse-ticket')
}
