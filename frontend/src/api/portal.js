import request from './request'

// 工作台聚合：待办数/公告/经营指标/我的草稿/最新事件
export const workbench = () => request.get('/portal/workbench')

// 驾驶舱：经营卡片 + 项目红黄绿灯 + 近6个月趋势
export const cockpitOverview = () => request.get('/cockpit/overview')
