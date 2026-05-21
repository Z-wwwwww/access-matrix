import request from './request'

/** 操作ログ一覧（ページング + フィルタ） */
export function getOpLogListApi(params) {
  return request.get('/admin/oplog/list', { params })
}

/** 操作ログ単票 */
export function getOpLogApi(id) {
  return request.get('/admin/oplog/' + id)
}
