import request from './request'

// 当前用户的菜单树（按角色自动过滤、保留父链）
// 新基盘后端：GET /api/menu/me
export function getMenuListApi() {
  return request.get('/menu/me')
}

// 菜单管理 CRUD（Stage 4 落地的管理端点，前缀 /admin/menu）
export function getMenuIndexApi() {
  return request.get('/admin/menu/list')
}

export function addMenuApi(data) {
  return request.post('/admin/menu', data)
}

export function editMenuApi(data) {
  return request.put('/admin/menu/' + data.id, data)
}

export function deleteMenuApi(id) {
  return request.delete('/admin/menu/' + id)
}
