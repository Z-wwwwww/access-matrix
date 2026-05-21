import request from './request'

/** 権限ディクショナリの一覧（ページング） */
export function getPermissionListApi(params) {
  return request.get('/admin/permission/list', { params })
}

/** モジュール別グルーピング（ロール権限設定 UI 用） */
export function getPermissionsByModuleApi() {
  return request.get('/admin/permission/by-module')
}

/** 権限新規 */
export function addPermissionApi(data) {
  return request.post('/admin/permission', data)
}

/** 権限更新 */
export function updatePermissionApi(id, data) {
  return request.put('/admin/permission/' + id, data)
}

/** 権限削除（内蔵は拒否） */
export function deletePermissionApi(id) {
  return request.delete('/admin/permission/' + id)
}
