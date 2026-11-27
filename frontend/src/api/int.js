import request from './request'

// ================= API注册 =================

// 接口清单分页（apiCode/status）
export const apiPage = params => request.get('/int/apis/page', { params })

export const apiCreate = data => request.post('/int/apis', data)

export const apiUpdate = (id, data) => request.put(`/int/apis/${id}`, data)

export const apiDelete = id => request.delete(`/int/apis/${id}`)

// ================= 适配器 =================

// 适配器分页（adapterCode/adapterType/status）
export const adapterPage = params => request.get('/int/adapters/page', { params })

export const adapterCreate = data => request.post('/int/adapters', data)

export const adapterUpdate = (id, data) => request.put(`/int/adapters/${id}`, data)

export const adapterDelete = id => request.delete(`/int/adapters/${id}`)

// 测试连接
export const adapterTest = id => request.post(`/int/adapters/${id}/test`)

// ================= 调用日志 =================

// 日志分页（adapterCode/success）
export const logPage = params => request.get('/int/logs/page', { params })

// 按适配器统计成功数/失败数/平均耗时
export const logStats = () => request.get('/int/logs/stats')
