import request from './request'

export const login = data => request.post('/auth/login', data)
export const fetchMyMenus = () => request.get('/auth/menus')
export const fetchMyPerms = () => request.get('/auth/perms')
