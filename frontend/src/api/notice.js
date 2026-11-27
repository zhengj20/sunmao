import request from './request'

// ================= 系统公告 =================

// 公告分页（status/noticeType 可选过滤）
export const fetchNotices = params => request.get('/sys/notices/page', { params })

export const noticeDetail = id => request.get(`/sys/notices/${id}`)

export const createNotice = data => request.post('/sys/notices', data)

export const updateNotice = (id, data) => request.put(`/sys/notices/${id}`, data)

export const deleteNotice = id => request.delete(`/sys/notices/${id}`)

// 置顶 / 取消置顶
export const togglePin = (id, pinned) =>
  request.put(`/sys/notices/${id}/pin`, { pinned })
