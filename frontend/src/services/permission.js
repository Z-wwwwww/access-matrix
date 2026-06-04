import request from './request'

/**
 * 権限字典の読み取り専用 API。
 *
 * 「常量法」導入後、字典は backend の PermissionConsistencyGuard が
 * コード由来の PermissionRegistry から起動時に upsert する。
 * 前端からの CRUD は廃止された（add/update/delete エンドポイントは存在しない）。
 */

/** 権限字典の一覧（ページング） — 管理画面はもう存在しないが、デバッグ用途で残置。 */
export function getPermissionListApi(params) {
  return request.get('/admin/permission/list', { params })
}

/** モジュール別グルーピング（ロール権限設定 UI で使用）。 */
export function getPermissionsByModuleApi() {
  return request.get('/admin/permission/by-module')
}
