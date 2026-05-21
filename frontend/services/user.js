import request from './request'

/** ユーザー一覧（ページング） */
export function getUserListApi(params) {
  return request.get('/admin/user/list', { params })
}

/** ユーザー単票 */
export function getUserApi(id) {
  return request.get('/admin/user/' + id)
}

/** ユーザー新規作成 */
export function addUserApi(data) {
  return request.post('/admin/user', data)
}

/** ユーザー更新 */
export function updateUserApi(id, data) {
  return request.put('/admin/user/' + id, data)
}

/** ユーザー削除 */
export function deleteUserApi(id) {
  return request.delete('/admin/user/' + id)
}

/** 該当ユーザーが保持しているロール ID 一覧 */
export function getUserRolesApi(id) {
  return request.get('/admin/user/' + id + '/roles')
}

/** ユーザーへロールを再割り当て（全置換） */
export function assignUserRolesApi(id, roleIds) {
  return request.put('/admin/user/' + id + '/roles', { roleIds })
}

/** 部署変更 */
export function changeUserDeptApi(id, deptId) {
  return request.put('/admin/user/' + id + '/dept', { deptId })
}

/** ステータス変更（1=有効 / 0=無効） */
export function changeUserStatusApi(id, status) {
  return request.put('/admin/user/' + id + '/status', { status })
}

/** 強制ログアウト（管理者のみ。*:* 必須） */
export function forceLogoutApi(id) {
  return request.post('/admin/auth/force-logout/' + id)
}

/** パスワードリセット（旧パスワード不要） */
export function resetPasswordApi(payload) {
  return request.post('/admin/auth/reset-password', payload)
}

/** ロック解除 */
export function unlockUserApi(payload) {
  return request.post('/admin/auth/unlock', payload)
}
