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

/**
 * ロール削除。{force:true} で user_role 含む全関連リンクを強制クリアする。
 * 通常呼び出し（force 省略）で利用中の場合は backend が IN_USE (code=703) を返すので、
 * 呼び出し側で再確認 → force=true で再送信する想定。
 */
export function deleteRoleApi(id, opts = {}) {
  return request.delete('/admin/role/' + id, opts.force ? { params: { force: true } } : {})
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
