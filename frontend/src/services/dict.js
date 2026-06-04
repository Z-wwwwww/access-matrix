import request from './request'

// ── read（下拉数据，登录即可。前端 dictStore + useDict 缓存消费）──────────
// GET /api/dict/{code} → { code, builtin, items:[{value,labelKey?,labelI18n?,cssClass,sort,enabled}] }
export function getDictApi(code) {
  return request.get('/dict/' + code)
}

// ── 管理（managed 字典，platform:dict:* / 前缀 /admin/dict）──────────────
export function listDictTypesApi() {
  return request.get('/admin/dict/list')
}

export function addDictTypeApi(data) {
  return request.post('/admin/dict', data)
}

export function updateDictTypeApi(id, data) {
  return request.put('/admin/dict/' + id, data)
}

export function deleteDictTypeApi(id) {
  return request.delete('/admin/dict/' + id)
}

export function listDictItemsApi(code) {
  return request.get('/admin/dict/' + code + '/items')
}

export function addDictItemApi(code, data) {
  return request.post('/admin/dict/' + code + '/item', data)
}

export function updateDictItemApi(id, data) {
  return request.put('/admin/dict/item/' + id, data)
}

export function deleteDictItemApi(id) {
  return request.delete('/admin/dict/item/' + id)
}
