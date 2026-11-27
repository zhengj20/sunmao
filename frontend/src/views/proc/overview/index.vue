<template>
  <div class="page-container">
    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="立项编号 / 标的名称" clearable style="width:180px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width:160px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="采购方式">
            <el-select v-model="query.method" placeholder="全部方式" clearable style="width:130px">
              <el-option v-for="m in PROC_METHODS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="tip">点击节点可跳转对应环节办理，点击整行查看全链路</div>
      </div>
    </el-card>

    <!-- 地铁图看板 -->
    <el-card shadow="never" class="board-card">
      <div class="board-head">
        <div class="legend">
          <span class="lg done"><i></i>已完成</span>
          <span class="lg active"><i></i>当前节点</span>
          <span class="lg overdue"><i></i>超期</span>
          <span class="lg pending"><i></i>待进行</span>
        </div>
        <div class="total">共 <b class="num">{{ total }}</b> 条招采</div>
      </div>

      <div class="board" v-loading="loading">
        <el-empty v-if="!loading && !list.length" description="暂无招采项目" :image-size="90" />
        <div
          v-for="row in list"
          :key="row.procProjectId"
          class="lane"
          @click="openDetail(row)"
        >
          <div class="lane-head">
            <div class="lane-title">
              <span class="name">{{ row.title }}</span>
              <span class="code">{{ row.projectCode }}</span>
            </div>
            <div class="lane-meta">
              <span>{{ row.projectName || projectName(row.projectId) }}</span>
              <span class="sep">·</span>
              <span>{{ methodLabel(row.procureMethod) }}</span>
              <span class="sep">·</span>
              <span class="num">预算 {{ money(row.budgetAmount) }}</span>
            </div>
            <div class="lane-state">
              <el-tag :type="stateTag(row)" size="small" effect="light">{{ stateLabel(row) }}</el-tag>
              <span class="pct num">{{ reached(row) }}/{{ NODES.length }}</span>
            </div>
          </div>
          <div class="lane-sub">
            <span v-if="row.winnerSupplierName">中标 <b>{{ row.winnerSupplierName }}</b></span>
            <span v-if="row.awardAmount != null" class="num">定标 {{ money(row.awardAmount) }}</span>
            <span v-if="row.awardDeviationPct != null" class="num" :class="devColor(row.awardDeviationPct)">偏差 {{ sign(row.awardDeviationPct) }}%</span>
            <span v-if="row.cycleDays != null" class="num">周期 {{ row.cycleDays }} 天</span>
            <span v-if="!row.winnerSupplierName && row.awardAmount == null" class="muted">尚未定标</span>
          </div>
          <div class="lane-line">
            <template v-for="(n, i) in NODES" :key="n.key">
              <div class="st" :class="stationClass(row, i)" :title="`${n.label}：${chainState(row, i)}（点击跳转）`" @click.stop="goNode(n, row)">
                <span class="dot"></span>
                <span class="lb">{{ n.label }}</span>
              </div>
              <span v-if="i < NODES.length - 1" class="tr" :class="trackClass(row, i)"></span>
            </template>
          </div>
        </div>
      </div>

      <div class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- 全链路详情抽屉 -->
    <el-drawer v-model="detail.visible" title="招采全链路（一标一档）" size="600px">
      <div v-loading="detail.loading" class="drawer-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="立项编号">{{ detail.row.projectCode }}</el-descriptions-item>
          <el-descriptions-item label="采购方式">{{ methodLabel(detail.row.procureMethod) }}</el-descriptions-item>
          <el-descriptions-item label="业务项目">{{ detail.row.projectName || projectName(detail.row.projectId) }}</el-descriptions-item>
          <el-descriptions-item label="当前环节">
            <el-tag :type="stateTag(detail.row)" size="small" effect="light">{{ stateLabel(detail.row) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标的名称" :span="2">{{ detail.row.title }}</el-descriptions-item>
          <el-descriptions-item label="预算金额(元)"><span class="num">{{ money(detail.row.budgetAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="定标价(元)"><span class="num">{{ money(detail.row.awardAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="中标供应商" :span="2">{{ detail.row.winnerSupplierName || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="block-title">全链路节点</div>
        <div class="chain">
          <div v-for="(n, i) in NODES" :key="n.key" class="chain-node" :class="chainNodeClass(detail.row, i)">
            <div class="cn-head">
              <span class="cn-dot"></span>
              <span class="cn-label">{{ n.label }}</span>
              <span class="cn-state">{{ chainState(detail.row, i) }}</span>
            </div>
            <div class="cn-desc">{{ chainDesc(n, i) }}</div>
            <el-button link type="primary" size="small" @click="goNode(n, detail.row)">前往「{{ n.label }}」管理 →</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { money } from '../../../utils/format'
import {
  overviewPage, ledgerDetail, fetchProjectOptions,
  PROC_METHODS, methodLabel
} from '../../../api/proc'

const router = useRouter()

// 9 站地铁图：key 对应后端 xxxDone 字段，path 为该环节管理页
const NODES = [
  { key: 'req',      label: '需求', path: '/proc/requirement' },
  { key: 'plan',     label: '计划', path: '/proc/plan' },
  { key: 'proj',     label: '立项', path: '/proc/project' },
  { key: 'entry',    label: '入围', path: '/proc/entry' },
  { key: 'tender',   label: '发标', path: '/proc/tender' },
  { key: 'bid',      label: '回标', path: '/proc/bid' },
  { key: 'eval',     label: '评标', path: '/proc/evaluation' },
  { key: 'award',    label: '定标', path: '/proc/award' },
  { key: 'contract', label: '合同', path: '/cost/contract' }
]

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, method: '' })
const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await overviewPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.method = ''
  load()
}

// ============ 地铁图状态推导 ============
const doneFlags = row => NODES.map(n => Number(row?.[n.key + 'Done']) === 1)
/** 已到达的最远节点下标（无则 -1） */
const headIndex = row => {
  const f = doneFlags(row)
  for (let i = f.length - 1; i >= 0; i--) if (f[i]) return i
  return -1
}
const reached = row => {
  const h = headIndex(row)
  return h >= 0 ? h + 1 : 0
}
const isComplete = row => headIndex(row) === NODES.length - 1

// 超期判定：当前节点已滞留天数超过阈值（阈值可在下方调整）
const OVERDUE_DAYS = 30
const daysOpen = row => {
  const raw = row?.lastProgressAt
  if (!raw) return 0
  const t = new Date(String(raw).replace(' ', 'T')).getTime()
  if (isNaN(t)) return 0
  return Math.max(0, Math.floor((Date.now() - t) / 86400000))
}
const isOverdue = row => !isComplete(row) && daysOpen(row) > OVERDUE_DAYS

const stationClass = (row, i) => {
  const h = headIndex(row)
  if (h === -1) return 'pending'
  if (i < h) return 'done'
  if (i === h) {
    if (isComplete(row)) return 'done complete'
    return isOverdue(row) ? 'active overdue' : 'active'
  }
  return 'pending'
}
const trackClass = (row, i) => ((i + 1) <= headIndex(row) ? 'done' : '')
const stateLabel = row => {
  const h = headIndex(row)
  if (h === -1) return '准备中'
  if (h === NODES.length - 1) return '已完成'
  return NODES[h].label + '中'
}
const stateTag = row => {
  const h = headIndex(row)
  if (h === -1) return 'info'
  return h === NODES.length - 1 ? 'success' : 'warning'
}
const sign = v => { v = Number(v || 0); return (v > 0 ? '+' : '') + v.toFixed(1) }
const devColor = v => (v == null || v === '' ? '' : Number(v) > 0 ? 'dev-up' : Number(v) < 0 ? 'dev-down' : '')

// ============ 节点跳转 ============
const goNode = (n, row) => {
  const q = row?.procProjectId ? { procProjectId: row.procProjectId } : {}
  router.push({ path: n.path, query: q })
}

// ============ 全链路抽屉 ============
const detail = reactive({ visible: false, loading: false, row: {}, chain: {} })
const chainState = (row, i) => {
  const h = headIndex(row)
  if (i === h && h !== -1) return isComplete(row) ? '已完成' : (isOverdue(row) ? `超期（滞留 ${daysOpen(row)} 天）` : '进行中')
  return doneFlags(row)[i] ? '已完成' : '待进行'
}
const chainNodeClass = (row, i) => {
  const h = headIndex(row)
  if (i === h && h !== -1) {
    if (isComplete(row)) return 'done'
    return isOverdue(row) ? 'active overdue' : 'active'
  }
  return doneFlags(row)[i] ? 'done' : 'pending'
}
const chainDesc = (n, i) => {
  const c = detail.chain || {}
  const row = detail.row || {}
  switch (n.key) {
    case 'req':      return Number(row.reqDone) === 1 ? '已提交采购需求' : '暂无需求记录'
    case 'plan':     return Number(row.planDone) === 1 ? '已纳入采购计划' : '暂无计划记录'
    case 'proj':     return Number(row.projDone) === 1 ? `立项已审批（${row.projectCode}）` : '立项审批中'
    case 'entry':    return (c.entries?.length) ? `${c.entries.length} 家入围` : '暂无入围记录'
    case 'tender':   return c.tender ? `发标编号 ${c.tender.tenderCode || ''}`.trim() : '暂无发标记录'
    case 'bid':      return (c.bids?.length) ? `${c.bids.length} 家回标` : '暂无回标记录'
    case 'eval':     return c.evaluation ? '评标已完成' : '暂无评标记录'
    case 'award':    return c.award ? `定标价 ${money(c.award.awardAmount)}｜中标 ${c.award.winnerSupplierName || ''}`.trim() : '暂无定标记录'
    case 'contract': return Number(row.contractDone) === 1 ? '已生成合同' : '未生成合同'
    default:         return ''
  }
}
const openDetail = async row => {
  detail.row = row
  detail.chain = {}
  detail.visible = true
  detail.loading = true
  try {
    const d = await ledgerDetail(row.procProjectId)
    detail.chain = {
      entries: d?.entries || [],
      tender: d?.tender || null,
      bids: d?.bids || [],
      evaluation: d?.evaluation || null,
      award: d?.award || null
    }
  } finally {
    detail.loading = false
  }
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.tip { color: #909399; font-size: 12.5px; align-self: center; }
.block-title { margin: 18px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
.drawer-body { padding: 0 4px; }

/* 看板 */
.board-card .el-card__body { padding: 12px 14px 0; }
.board-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; padding: 2px 4px 12px; }
.legend { display: flex; gap: 14px; }
.lg { display: inline-flex; align-items: center; gap: 6px; color: #6b7280; font-size: 12.5px; }
.lg i { width: 12px; height: 12px; border-radius: 50%; display: inline-block; }
.lg.done i { background: var(--reip-primary); }
.lg.active i { background: var(--reip-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-9); }
.lg.pending i { background: #fff; border: 2px solid #d9dce1; }
.total { color: #6b7280; font-size: 13px; }

.board { display: grid; gap: 10px; }
.lane {
  background: #fff; border: 1px solid #e6e8eb; border-radius: 8px;
  padding: 12px 16px; cursor: pointer; transition: border-color .12s, box-shadow .12s;
}
.lane:hover { border-color: var(--el-color-primary-light-5); box-shadow: 0 2px 10px rgba(24, 95, 165, .08); }
.lane-head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 14px; }
.lane-title { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.lane-title .name { font-size: 14px; font-weight: 600; color: #1f2937; }
.lane-title .code { font-size: 12px; color: #909399; font-family: "SF Mono", Consolas, monospace; }
.lane-meta { display: flex; align-items: center; gap: 6px; color: #6b7280; font-size: 12.5px; flex: 1; flex-wrap: wrap; }
.lane-meta .sep { color: #d1d5db; }
.lane-state { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }
.lane-state .pct { color: #6b7280; font-size: 12.5px; font-family: "SF Mono", Consolas, monospace; }
.lane-sub { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; font-size: 12.5px; color: #4b5563; }
.lane-sub b { color: #1f2937; font-weight: 600; }
.lane-sub .muted { color: #909399; }
.lane-sub .dev-up { color: #f56c6c; font-weight: 600; }
.lane-sub .dev-down { color: #67c23a; font-weight: 600; }

/* 地铁线 */
.lane-line { display: flex; align-items: flex-start; }
.st { display: flex; flex-direction: column; align-items: center; gap: 6px; width: 34px; flex: 0 0 auto; cursor: pointer; }
.st .dot { width: 12px; height: 12px; border-radius: 50%; background: #fff; border: 2px solid #d9dce1; }
.st .lb { color: #909399; font-size: 12px; white-space: nowrap; }
.tr { flex: 1 1 auto; height: 2px; background: #e6e8eb; margin-top: 5px; }

.st.done .dot { background: var(--el-color-success); border-color: var(--el-color-success); }
.st.done .lb { color: #374151; }
.tr.done { background: var(--el-color-success); }

.st.active .dot { background: var(--reip-primary); border-color: var(--reip-primary); box-shadow: 0 0 0 4px var(--el-color-primary-light-9); animation: lanePulse 2s ease-in-out infinite; }
.st.active .lb { color: #0c447c; font-weight: 600; }
.st.complete .dot { box-shadow: 0 0 0 4px var(--el-color-success-light-9); }

.st.overdue .dot { background: var(--el-color-danger); border-color: var(--el-color-danger); box-shadow: 0 0 0 4px var(--el-color-danger-light-9); animation: lanePulse 2s ease-in-out infinite; }
.st.overdue .lb { color: var(--el-color-danger); font-weight: 600; }

.st:hover .lb { color: var(--reip-primary); }
@keyframes lanePulse {
  0%, 100% { box-shadow: 0 0 0 4px var(--el-color-primary-light-9); }
  50% { box-shadow: 0 0 0 7px var(--el-color-primary-light-9); }
}
@media (prefers-reduced-motion: reduce) {
  .st.active .dot { animation: none; }
}

/* 抽屉链路 */
.chain { display: grid; gap: 8px; }
.chain-node { border: 1px solid #eef0f2; border-radius: 8px; padding: 10px 12px; background: #fafbfc; }
.chain-node.active { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.chain-node.overdue { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.chain-node.done { background: #fff; }
.cn-head { display: flex; align-items: center; gap: 8px; }
.cn-dot { width: 10px; height: 10px; border-radius: 50%; background: #fff; border: 2px solid #d9dce1; }
.chain-node.done .cn-dot { background: var(--el-color-success); border-color: var(--el-color-success); }
.chain-node.active .cn-dot { background: var(--reip-primary); border-color: var(--reip-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-9); }
.chain-node.overdue .cn-dot { background: var(--el-color-danger); border-color: var(--el-color-danger); box-shadow: 0 0 0 3px var(--el-color-danger-light-9); }
.cn-label { font-size: 13.5px; font-weight: 600; color: #1f2937; min-width: 40px; }
.cn-state { font-size: 12px; color: #909399; }
.chain-node.active .cn-state { color: var(--reip-primary); }
.chain-node.overdue .cn-state { color: var(--el-color-danger); }
.cn-desc { margin: 6px 0 6px 18px; font-size: 12.5px; color: #6b7280; }
</style>
