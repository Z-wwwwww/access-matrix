import request from './request'

// ── 辞書マスタ (親) ──

/**
 * 辞書マスタ一覧取得（ページネーション）
 */
export function getDictIndexApi(params) {
  return request.get('/dict/index', { params })
}

/**
 * 辞書マスタ新規追加
 */
export function addDictApi(data) {
  return request.post('/dict/add', data)
}

/**
 * 辞書マスタ情報更新
 */
export function updateDictApi(data) {
  return request.put('/dict/edit', data)
}

/**
 * 辞書マスタ削除（単体・一括、カンマ区切りID）
 */
export function deleteDictApi(ids) {
  return request.delete('/dict/delete/' + ids)
}

// ── 辞書データ (子) ──

/**
 * 辞書データ一覧取得（dictId でフィルタ）
 */
export function getDictDataIndexApi(params) {
  return request.get('/dictdata/index', { params })
}

/**
 * 辞書データ新規追加
 */
export function addDictDataApi(data) {
  return request.post('/dictdata/add', data)
}

/**
 * 辞書データ情報更新
 */
export function updateDictDataApi(data) {
  return request.put('/dictdata/edit', data)
}

/**
 * 辞書データ削除（単体・一括、カンマ区切りID）
 */
export function deleteDictDataApi(ids) {
  return request.delete('/dictdata/delete/' + ids)
}

/**
 * 辞書データ ステータス切替（1=有効 / 2=無効）
 */
export function updateDictDataStatusApi(data) {
  return request.put('/dictdata/status', data)
}
