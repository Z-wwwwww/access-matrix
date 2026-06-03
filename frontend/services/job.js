import request from './request'

// 定時タスク管理。バックエンドはコード定義のジョブのみ扱うため、作成/削除 API は無い。
// 設定の変更・起停・即時実行・実行ログ照会のみ。

export function getJobListApi(params) {
  return request.get('/admin/job/list', { params })
}

export function getJobLogListApi(params) {
  return request.get('/admin/job/log/list', { params })
}

export function updateJobApi(id, data) {
  return request.put('/admin/job/' + id, data)
}

export function enableJobApi(id) {
  return request.post('/admin/job/' + id + '/enable')
}

export function disableJobApi(id) {
  return request.post('/admin/job/' + id + '/disable')
}

export function runJobApi(id) {
  return request.post('/admin/job/' + id + '/run')
}
