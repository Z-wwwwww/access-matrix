import request from './request'

/** デバッグ用：現ユーザーのデータスコープ決定を取得 */
export function getMyScopeApi() {
  return request.get('/scope/me')
}
