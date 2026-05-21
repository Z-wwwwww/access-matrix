import request from './request'

/** ロール一覧（ページング） */
export function getRoleListApi(params) {
  return request.get('/admin/role/list', { params })
}

/** ロール単票 */
export function getRoleApi(id) {
  return request.get('/admin/role/' + id)
}

/** ロール新規作成 */
export function addRoleApi(data) {
  return request.post('/admin/role', data)
}

/** ロール更新 */
export function updateRoleApi(id, data) {
  return request.put('/admin/role/' + id, data)
}

/** ロール削除（内蔵ロールは拒否される） */
export function deleteRoleApi(id) {
  return request.delete('/admin/role/' + id)
}

/** ロール保持の権限 ID 一覧 */
export function getRolePermissionsApi(id) {
  return request.get('/admin/role/' + id + '/permissions')
}

/** ロールに権限を再割り当て（全置換） */
export function bindRolePermissionsApi(id, ids) {
  return request.put('/admin/role/' + id + '/permissions', { ids })
}

/** ロール紐付けメニュー ID 一覧 */
export function getRoleMenusApi(id) {
  return request.get('/admin/role/' + id + '/menus')
}

/** ロールにメニューを再割り当て（全置換） */
export function bindRoleMenusApi(id, ids) {
  return request.put('/admin/role/' + id + '/menus', { ids })
}

/** ロールに紐付く部署 ID 一覧（data_scope=CUSTOM 用） */
export function getRoleDeptsApi(id) {
  return request.get('/admin/role/' + id + '/depts')
}

/** ロールに部署を再割り当て（全置換） */
export function bindRoleDeptsApi(id, ids) {
  return request.put('/admin/role/' + id + '/depts', { ids })
}
