import request from './request'

/** 部署ツリー全取得（ログイン後はキャッシュされる） */
export function getDeptTreeApi() {
  return request.get('/dept/tree')
}

/** 部署新規 */
export function addDeptApi(data) {
  return request.post('/admin/dept', data)
}

/**
 * 部署更新。停用（status 1→0）時に SCOPE_CUSTOM ロールから参照されていると
 * backend が IN_USE (code=703, data.roles=件数) を返すので、呼び出し側で
 * 再確認 → {force:true} で再送信する想定（削除と同じハンドシェイク）。
 */
export function updateDeptApi(id, data, opts = {}) {
  return request.put('/admin/dept/' + id, data, opts.force ? { params: { force: true } } : {})
}

/**
 * 部署削除。{force:true} で部分木全体（子部署 + 所属ユーザーの dept_id を NULL）を強制クリア。
 * 通常呼び出しで利用中の場合は backend が IN_USE (code=703) を返すので、
 * 呼び出し側で再確認 → force=true で再送信する想定。
 */
export function deleteDeptApi(id, opts = {}) {
  return request.delete('/admin/dept/' + id, opts.force ? { params: { force: true } } : {})
}
