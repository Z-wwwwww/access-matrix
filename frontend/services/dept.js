import request from './request'

/** 部署ツリー全取得（ログイン後はキャッシュされる） */
export function getDeptTreeApi() {
  return request.get('/dept/tree')
}

/** 部署新規 */
export function addDeptApi(data) {
  return request.post('/admin/dept', data)
}

/** 部署更新 */
export function updateDeptApi(id, data) {
  return request.put('/admin/dept/' + id, data)
}

/** 部署削除（子部署あり / ユーザー所属ありの場合は拒否される） */
export function deleteDeptApi(id) {
  return request.delete('/admin/dept/' + id)
}
