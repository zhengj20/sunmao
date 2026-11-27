import {
  Odometer, TrendCharts, List, Calendar, Flag, Aim, Promotion, Box,
  Stamp, Medal, Link, Connection, ShoppingCart, Notebook, UserFilled,
  HomeFilled, Tickets, CircleCheck, EditPen, Avatar, MagicStick,
  ChatDotRound, Timer, DataAnalysis, Collection, Share, Cpu, DataLine,
  OfficeBuilding, Location, PieChart, Money, Document, DocumentCopy,
  Refresh, Sort, ChatLineRound, Picture, Setting, Position, Filter,
  PriceTag, RefreshLeft, CreditCard, Files, DocumentChecked, Wallet,
  Postcard, Menu, Van, Key, User, Lock, Bell, Monitor, SetUp
} from '@element-plus/icons-vue'

// 图标映射（按菜单 path）。跨模块复用同一路径时以后定义者为准。
const ICONS = {
  // 工作台
  '/workbench': HomeFilled,
  '/workflow/todo': Tickets,
  '/workflow/done': CircleCheck,
  '/workflow/mine': EditPen,
  // 数字员工 / AI
  '/digital/employee': Avatar,
  '/ai/approval': Stamp,
  '/digital/skill': MagicStick,
  '/ai/chat': ChatDotRound,
  '/digital/run': Timer,
  '/ai/report': DataAnalysis,
  '/ai/kb': Collection,
  '/digital/workflow': Share,
  '/digital/approval': Stamp,
  '/digital/tool': SetUp,
  '/ai/model': Cpu,
  '/ai/audit': DataLine,
  // 经营驾驶舱
  '/cockpit/overview': Odometer,
  '/cockpit/group': OfficeBuilding,
  '/cockpit/region': Location,
  '/cockpit/project': Flag,
  '/cockpit/topic': DataAnalysis,
  '/cockpit/metric': Tickets,
  // 投资管理
  '/invest/cockpit': Odometer,
  '/invest/overview': PieChart,
  '/invest/calc': DataAnalysis,
  '/invest/value': Money,
  '/invest/profit': TrendCharts,
  // 招采管理
  '/proc/cockpit': Odometer,
  '/proc/overview': TrendCharts,
  '/proc/requirement': List,
  '/proc/plan': Calendar,
  '/proc/project': Flag,
  '/proc/entry': Aim,
  '/proc/tender': Promotion,
  '/proc/bid': Box,
  '/proc/evaluation': Stamp,
  '/proc/award': Medal,
  '/proc/direct': Link,
  '/proc/framework': Connection,
  '/proc/misc': ShoppingCart,
  '/proc/ledger': Notebook,
  '/proc/expert': UserFilled,
  // 成本管理
  '/cost/cockpit': Odometer,
  '/cost/overview': PieChart,
  '/cost/target': Aim,
  '/cost/plan': Calendar,
  '/cost/contract': Document,
  '/cost/payplan': List,
  '/cost/payment': Money,
  '/cost/change': Refresh,
  '/cost/supplement': DocumentCopy,
  '/cost/change-matter': Tickets,
  '/cost/dynamic': TrendCharts,
  '/cost/invalid': Sort,
  '/cost/indicator': DataAnalysis,
  '/cost/template': Notebook,
  // 项目计划
  '/plan/overview': PieChart,
  '/plan/cockpit': Odometer,
  '/plan/template': Notebook,
  '/plan/task': List,
  '/plan/specialty': Flag,
  '/plan/progress': DataLine,
  '/plan/adjust': Setting,
  '/plan/milestone': Position,
  '/plan/meeting': ChatLineRound,
  '/plan/image': Picture,
  '/plan/report': DataAnalysis,
  // 销售管理
  '/sales/cockpit': Odometer,
  '/sales/overview': PieChart,
  '/sales/funnel': Filter,
  '/sales/advisor': Avatar,
  '/sales/follow': UserFilled,
  '/sales/channel': Share,
  '/sales/target': Aim,
  '/mdm/building': OfficeBuilding,
  '/sales/room': HomeFilled,
  '/sales/discount': PriceTag,
  '/sales/subscription': Tickets,
  '/sales/contract': Document,
  '/sales/payment': Money,
  '/sales/area-diff': Sort,
  '/sales/refund': RefreshLeft,
  '/sales/report': DataAnalysis,
  // 全面预算 / 资金
  '/budget/annual': Calendar,
  '/budget/month': Tickets,
  '/budget/execute': DataLine,
  '/fund/plan': Money,
  '/fund/execute': CreditCard,
  '/fund/finance': Files,
  // 费用管理
  '/expense/overview': PieChart,
  '/expense/cockpit': Odometer,
  '/expense/budget': Calendar,
  '/expense/standard': DocumentChecked,
  '/expense/contract': Document,
  '/expense/matter': Tickets,
  '/expense/apply': EditPen,
  '/expense/loan': Money,
  '/expense/reimburse': Wallet,
  '/expense/invoice': Postcard,
  '/expense/payment': CreditCard,
  '/expense/allocation': Share,
  '/expense/report': DataAnalysis,
  // 主数据
  '/mdm/org': OfficeBuilding,
  '/mdm/employee': UserFilled,
  '/mdm/project': Flag,
  '/mdm/account': Files,
  '/mdm/category': Menu,
  '/mdm/expense-account': Money,
  '/mdm/supplier': Van,
  '/mdm/customer': Avatar,
  '/mdm/coderule': Key,
  '/mdm/quality': CircleCheck,
  // 系统管理 / 流程
  '/sys/user': User,
  '/sys/role': Lock,
  '/sys/menu': Menu,
  '/sys/param': Setting,
  '/sys/log': Document,
  '/sys/notice': Bell,
  '/workflow/design': Share,
  '/workflow/monitor': Monitor,
  '/workflow/designer': EditPen,
  // 集成平台
  '/int/api': Connection,
  '/int/adapter': Cpu,
  '/int/log': Document
}

// 分组定义（key 为模块名，数组顺序即展示顺序）。
// 未在此列出的菜单项会自动归入“其他”，保证不丢菜单。
const GROUPS = {
  工作台: [
    { label: '概览', paths: ['/workbench'] },
    { label: '我的任务', paths: ['/workflow/todo', '/workflow/done', '/workflow/mine'] }
  ],
  数字员工: [
    { label: '团队', paths: ['/digital/employee'] },
    { label: '智能助理', paths: ['/ai/chat', '/ai/approval', '/ai/report'] },
    { label: '能力配置', paths: ['/digital/skill', '/digital/tool', '/ai/kb', '/ai/model', '/digital/workflow'] },
    { label: '运行监控', paths: ['/digital/approval', '/digital/run', '/ai/audit'] }
  ],
  经营驾驶舱: [
    { label: '总览', paths: ['/cockpit/overview'] },
    { label: '多维驾驶舱', paths: ['/cockpit/group', '/cockpit/region', '/cockpit/project'] },
    { label: '分析与指标', paths: ['/cockpit/topic', '/cockpit/metric'] }
  ],
  投资管理: [
    { label: '总览', paths: ['/invest/cockpit', '/invest/overview'] },
    { label: '测算与货值', paths: ['/invest/calc', '/invest/value', '/invest/profit'] }
  ],
  招采管理: [
    { label: '总览监控', paths: ['/proc/cockpit', '/proc/overview'] },
    { label: '采购启动', paths: ['/proc/requirement', '/proc/plan', '/proc/project', '/proc/entry'] },
    { label: '招投标', paths: ['/proc/tender', '/proc/bid', '/proc/evaluation', '/proc/award'] },
    { label: '特殊采购', paths: ['/proc/direct', '/proc/framework', '/proc/misc'] },
    { label: '台账与专家', paths: ['/proc/ledger', '/proc/expert'] }
  ],
  成本管理: [
    { label: '总览', paths: ['/cost/cockpit', '/cost/overview'] },
    { label: '目标与动态', paths: ['/cost/target', '/cost/plan', '/cost/dynamic', '/cost/invalid'] },
    { label: '合同', paths: ['/cost/contract', '/cost/supplement', '/cost/template'] },
    { label: '付款', paths: ['/cost/payplan', '/cost/payment'] },
    { label: '变更', paths: ['/cost/change', '/cost/change-matter'] },
    { label: '指标库', paths: ['/cost/indicator'] }
  ],
  项目计划: [
    { label: '总览', paths: ['/plan/overview', '/plan/cockpit'] },
    { label: '计划编制', paths: ['/plan/template', '/plan/task', '/plan/specialty', '/plan/adjust'] },
    { label: '进度管理', paths: ['/plan/progress', '/plan/milestone', '/plan/image'] },
    { label: '协同与报表', paths: ['/plan/meeting', '/plan/report'] }
  ],
  销售管理: [
    { label: '总览', paths: ['/sales/cockpit', '/sales/overview'] },
    { label: '获客渠道', paths: ['/sales/funnel', '/sales/follow', '/sales/channel', '/sales/advisor'] },
    { label: '房源价格', paths: ['/mdm/building', '/sales/room', '/sales/discount'] },
    { label: '交易签约', paths: ['/sales/target', '/sales/subscription', '/sales/contract', '/sales/payment'] },
    { label: '售后与报表', paths: ['/sales/area-diff', '/sales/refund', '/sales/report'] }
  ],
  全面预算: [
    { label: '预算', paths: ['/budget/annual', '/budget/month', '/budget/execute'] },
    { label: '资金', paths: ['/fund/plan', '/fund/execute', '/fund/finance'] }
  ],
  费用管理: [
    { label: '总览', paths: ['/expense/overview', '/expense/cockpit'] },
    { label: '预算与标准', paths: ['/expense/budget', '/expense/standard', '/expense/contract'] },
    { label: '申请报销', paths: ['/expense/matter', '/expense/apply', '/expense/loan', '/expense/reimburse'] },
    { label: '发票付款', paths: ['/expense/invoice', '/expense/payment', '/expense/allocation'] },
    { label: '分析报表', paths: ['/expense/report'] }
  ],
  主数据: [
    { label: '组织人员', paths: ['/mdm/org', '/mdm/employee'] },
    { label: '项目科目', paths: ['/mdm/project', '/mdm/account', '/mdm/expense-account', '/mdm/category'] },
    { label: '客商', paths: ['/mdm/supplier', '/mdm/customer'] },
    { label: '规则与质量', paths: ['/mdm/coderule', '/mdm/quality'] }
  ],
  系统管理: [
    { label: '账户权限', paths: ['/sys/user', '/sys/role', '/sys/menu'] },
    { label: '系统配置', paths: ['/sys/param', '/sys/notice', '/sys/log'] },
    { label: '流程管理', paths: ['/workflow/design', '/workflow/designer', '/workflow/monitor'] }
  ],
  集成平台: [
    { label: '接口', paths: ['/int/api', '/int/adapter'] },
    { label: '日志', paths: ['/int/log'] }
  ]
}

/**
 * 将模块的子菜单按配置分组。
 * @returns {Array<{label, items:{path,name,icon}[]}>|null} 无配置时返回 null（走旧的平铺菜单）
 */
export function getSidebarGroups(moduleName, children) {
  const config = GROUPS[moduleName]
  if (!config) return null

  const byPath = {}
  children.forEach(c => { byPath[c.path] = c })
  const used = new Set()
  const groups = []

  for (const g of config) {
    const items = []
    for (const p of g.paths) {
      const item = byPath[p]
      if (!item) continue
      used.add(p)
      items.push({ path: item.path, name: item.name, icon: ICONS[p] || null })
    }
    if (items.length) groups.push({ label: g.label, items })
  }

  // 配置未覆盖的菜单项归入“其他”，保证不丢菜单
  const rest = children.filter(c => !used.has(c.path))
  if (rest.length) {
    groups.push({ label: '其他', items: rest.map(c => ({ path: c.path, name: c.name, icon: ICONS[c.path] || null })) })
  }

  return groups.length ? groups : null
}
