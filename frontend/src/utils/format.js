import dayjs from 'dayjs'

/** 金额格式化：千分位 + 2位小数 */
export const money = v => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return v
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 金额单位：万 */
export const wan = v => {
  if (v === null || v === undefined || v === '') return '-'
  return money(Number(v) / 10000)
}

export const fmtDate = v => (v ? dayjs(v).format('YYYY-MM-DD') : '-')
export const fmtTime = v => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-')

/** 通用状态 → 标签颜色 */
export const statusColor = s => {
  const map = {
    DRAFT: 'info', APPROVING: 'warning', APPROVED: 'success', REJECTED: 'danger',
    EFFECTIVE: 'success', SETTLED: '', CLOSED: 'info', PAID: 'success',
    CONFIRMED: 'success', CONFIRMING: 'warning', ESTIMATING: 'info',
    ESTIMATE_APPROVING: 'warning', RUNNING: 'warning', PENDING: 'warning',
    WITHDRAWN: 'info', ACTIVE: 'success', PREPARING: 'info', CONSTRUCTION: '',
    DELIVERED: 'success', LIQUIDATION: 'info', LOCKED: '', MERGED: 'info',
    AWARDED: 'success', TENDERING: '', EVALUATING: '', PUBLISHED: '', OPENED: '',
    SENT: 'success', UNSENT: 'info', PASSED: 'success', FAILED: 'danger',
    WARN: 'warning', BLOCKED: 'danger'
  }
  return map[s] || 'info'
}

/** 常用字典翻译（与后端 mdm_dict/sys 枚举对齐） */
export const DICTS = {
  project_status: { PREPARING: '筹备', CONSTRUCTION: '在建', DELIVERED: '交付', LIQUIDATION: '清算' },
  procure_method: { PUBLIC_BID: '公开招标', INVITE_BID: '邀请招标', COMPETITIVE: '竞争性谈判', INQUIRY: '询比价', DIRECT: '直接委托', FRAMEWORK: '战略集采' },
  contract_type: { GC: '工程总包', ZC: '专业分包', CL: '材料设备', FW: '咨询服务' },
  change_type: { SJBG: '设计变更', GCQZ: '工程签证', QSSY: '洽商事宜' },
  payment_type: { PREPAY: '预付款', PROGRESS: '进度款', SETTLE: '结算款', RETAIN: '质保金' },
  target_stage: { LAND: '拿地版', SCHEME: '方案版', CONSTRUCTION: '施工图版', BASE: '基准版' },
  lib_type: { OFFICIAL: '正式库', PRESELECT: '预选库', BLACKLIST: '黑名单' },
  price_version: { FORECAST: '预测价', REGISTERED: '备案价', DEAL: '成交价' }
}

export const dictLabel = (dictKey, value) => DICTS[dictKey]?.[value] || value || '-'

/** 审批流 bizType → 中文名 */
export const BIZ_TYPE = {
  COST_TARGET: '目标成本', COST_PLAN: '合约规划', COST_CONTRACT: '合同评审',
  COST_CHANGE: '变更确认', COST_PAYMENT: '付款申请', COST_INVALID: '无效成本',
  COST_SUPPLEMENT: '补充协议',
  PROC_REQUIREMENT: '采购需求', PROC_PROJECT: '招采立项', PROC_ENTRY: '入围名单',
  PROC_AWARD: '定标审批', PROC_DIRECT: '直接委托'
}
