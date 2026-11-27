import request from './request'

// ================= 主数据数据质量巡检 =================

// 执行巡检并落库（重复扫描自动去重）
export const runQualityScan = () => request.post('/mdm/quality/scan')

// 问题分页（severity/status/entityType 过滤）
export const fetchQualityIssues = params => request.get('/mdm/quality/issues', { params })

// 处置问题：FIXED 已修复 / IGNORED 已忽略
export const resolveIssue = (id, status) =>
  request.put(`/mdm/quality/issues/${id}/resolve`, { status })

// 汇总报告：severity/entityType 计数 + Top 问题
export const fetchQualityReport = () => request.get('/mdm/quality/report')
