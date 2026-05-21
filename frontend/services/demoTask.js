import request from './request'

/**
 * Demo business: Task module (data-scope walkthrough).
 *
 * Backend lives under `business-demo` Maven module, controller path `/demo/task`.
 * Filenames here follow the AGENTS.md convention of `{businessModule}{Resource}.js`
 * (e.g. demoTask, demoFoo) so business code stays grouped by prefix without
 * subdirectory churn.
 */

/** タスク一覧（ページング、データ範囲は呼び出し元の current scope に従う） */
export function getDemoTaskListApi(params) {
  return request.get('/demo/task/list', { params })
}

/** タスク単票 */
export function getDemoTaskApi(id) {
  return request.get('/demo/task/' + id)
}

/** タスク新規 */
export function addDemoTaskApi(data) {
  return request.post('/demo/task', data)
}

/** タスク更新 */
export function updateDemoTaskApi(id, data) {
  return request.put('/demo/task/' + id, data)
}

/** タスク削除 */
export function deleteDemoTaskApi(id) {
  return request.delete('/demo/task/' + id)
}
