import request from './request'

// ================= 用户管理 =================
export const userPage = params => request.get('/sys/users/page', { params })
export const userRoleIds = id => request.get(`/sys/users/${id}/roles`)
export const userCreate = (user, roleIds) => request.post('/sys/users', { user, roleIds })
export const userUpdate = (user, roleIds) => request.put('/sys/users', { user, roleIds })
export const userResetPassword = (id, password) => request.put(`/sys/users/${id}/password`, { password })
export const userToggleStatus = (id, status) =>
  request.put(`/sys/users/${id}/status`, null, { params: { status } })

// ================= 角色权限 =================
export const roleList = () => request.get('/sys/roles')
export const roleMenuIds = id => request.get(`/sys/roles/${id}/menus`)
export const roleSave = (role, menuIds) => request.post('/sys/roles', { role, menuIds })

// ================= 菜单管理 =================
export const menuTree = () => request.get('/sys/menus/tree')
export const menuSave = data => request.post('/sys/menus', data)
export const menuDelete = id => request.delete(`/sys/menus/${id}`)

// ================= 系统参数 =================
export const paramList = () => request.get('/sys/params')
export const paramSave = (key, value) => request.put('/sys/params', { key, value })

// ================= 操作日志 =================
export const opLogPage = params => request.get('/sys/oplogs/page', { params })
