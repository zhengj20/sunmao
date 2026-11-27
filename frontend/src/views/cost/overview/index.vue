<template>
  <div class="page-container">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="head-row">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 220px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="query.kw" placeholder="合同编号 / 名称 / 承包商" clearable style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <span class="hint">点击任意合同行可展开查看「签约 → 结算」全貌</span>
        </div>
      </div>
    </el-card>

    <!-- 汇总带 -->
    <el-row :gutter="12" class="stat-row" v-loading="loading">
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">合同总数</div><div class="value num">{{ summary.count }} <span class="unit">个</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">签约总额（元）</div><div class="value num">{{ money(summary.signed) }}</div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">确认变更（元）</div><div class="value num" :style="summary.change > 0 ? 'color:#c45656' : ''">{{ money(summary.change) }}</div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">已付总额（元）</div><div class="value num">{{ money(summary.paid) }}</div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">应付未付（元）</div><div class="value num" style="color:#e6a23c">{{ money(summary.unpaid) }}</div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">已结算</div><div class="value num" :style="summary.settledCount ? 'color:#529b2e' : ''">{{ summary.settledCount }} <span class="unit">个</span></div></el-card></el-col>
    </el-row>

    <!-- 合同列表（可展开下钻） -->
    <el-card shadow="never" class="table-card" v-loading="loading">
      <div class="card-title">合同履约总览（{{ rows.length }}）</div>
      <el-table :data="rows" row-key="id" :expand-row-keys="expandedKeys" @expand-change="onExpandChange" class="ov-table" empty-text="暂无合同">
        <el-table-column type="expand" width="38">
          <template #default="{ row }">
            <div class="detail" v-loading="!detailCache[row.id]?.loaded">
              <div class="detail-actions" v-if="['EFFECTIVE','SETTLED'].includes(row.status)">
                <el-button v-if="row.status === 'EFFECTIVE'" type="primary" size="small" :loading="saving" @click="onSettle(row)">结算</el-button>
                <el-button type="success" size="small" :loading="saving" @click="onRetention(row)">质保金释放</el-button>
                <span class="da-hint">结算额 = 签约 + 确认变更 − 扣款 = <span class="num">{{ money(row.total) }}</span> 元</span>
              </div>
              <div class="da-hint" v-else-if="row.status === 'DRAFT'">合同尚未生效，暂无付款 / 结算动作。</div>

              <el-row :gutter="12" class="detail-grid">
                <el-col :span="12">
                  <div class="d-block">
                    <div class="d-title">付款计划（{{ (detailCache[row.id]?.plans || []).length }}）</div>
                    <el-table :data="detailCache[row.id]?.plans || []" border size="small" empty-text="暂无付款计划">
                      <el-table-column label="期次" prop="termNo" width="48" align="center" />
                      <el-table-column label="期次说明" prop="termDesc" min-width="120" show-overflow-tooltip />
                      <el-table-column label="计划（元）" width="110" align="right"><template #default="{ r }"><span class="num">{{ money(r.planAmount) }}</span></template></el-table-column>
                      <el-table-column label="已付（元）" width="100" align="right"><template #default="{ r }"><span class="num">{{ money(r.paidAmount) }}</span></template></el-table-column>
                      <el-table-column label="状态" width="76" align="center"><template #default="{ r }"><el-tag size="small" :type="Number(r.paidAmount) >= Number(r.planAmount) ? 'success' : 'info'">{{ Number(r.paidAmount) >= Number(r.planAmount) ? '已付' : '进行中' }}</el-tag></template></el-table-column>
                    </el-table>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="d-block">
                    <div class="d-title">履约进度</div>
                    <div class="d-progress">
                      <div class="dp-line"><span>工期进度</span><el-progress :percentage="row.schedulePct ?? 0" :stroke-width="10" color="#185fa5" style="flex:1" /><span class="dp-pct num">{{ row.schedulePct == null ? '--' : row.schedulePct + '%' }}</span></div>
                      <div class="dp-line"><span>质保金释放</span><el-progress :percentage="row.retentionPct" :stroke-width="10" color="#529b2e" style="flex:1" :status="row.retentionPct >= 100 ? 'success' : undefined" /><span class="dp-pct num">{{ row.retentionPct }}%</span></div>
                    </div>
                    <div class="d-meta">工期 {{ fmtDate(row.startDate) }} ~ {{ fmtDate(row.endDate) }} · 质保比例 {{ row.retentionRate ?? 0 }}% · 已释放 <span class="num">{{ money(row.retentionReleased) }}</span> / 应释放 <span class="num">{{ money(row.retentionTotal) }}</span></div>
                  </div>
                  <div class="d-block" style="margin-top:14px">
                    <div class="d-title">变更概览</div>
                    <div class="d-co">
                      <div class="dco-item"><span>确认笔数</span><b class="num">{{ row.changeCount }} 笔</b></div>
                      <div class="dco-item"><span>确认累计</span><b class="num" :style="row.change > 0 ? 'color:#c45656' : ''">{{ money(row.change) }}</b></div>
                      <div class="dco-item"><span>变更率</span><b class="num">{{ row.changeRate }}%</b></div>
                      <div class="dco-item"><span>预警阈值</span><b class="num" :style="detailCache[row.id]?.changeRate?.warn ? 'color:#c45656' : ''">{{ detailCache[row.id]?.changeRate?.warnRate ?? '--' }}%{{ detailCache[row.id]?.changeRate?.warn ? ' · 已超' : '' }}</b></div>
                    </div>
                  </div>
                </el-col>
              </el-row>

              <el-row :gutter="12" class="detail-grid" style="margin-top:12px">
                <el-col :span="12">
                  <div class="d-block">
                    <div class="d-title">变更记录</div>
                    <el-table :data="changesOf(row.id)" border size="small" max-height="260" empty-text="暂无变更">
                      <el-table-column label="变更单" prop="changeCode" width="160" show-overflow-tooltip />
                      <el-table-column label="类型" width="86" align="center"><template #default="{ r }"><el-tag size="small" effect="plain">{{ dictLabel('change_type', r.changeType) }}</el-tag></template></el-table-column>
                      <el-table-column label="标题" prop="title" min-width="130" show-overflow-tooltip />
                      <el-table-column label="确认（元）" width="100" align="right"><template #default="{ r }"><span class="num">{{ money(r.confirmedAmount) }}</span></template></el-table-column>
                      <el-table-column label="状态" width="86" align="center"><template #default="{ r }"><el-tag size="small" :type="statusColor(r.status)">{{ r.status }}</el-tag></template></el-table-column>
                    </el-table>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="d-block">
                    <div class="d-title">付款记录</div>
                    <el-table :data="paymentsOf(row.id)" border size="small" max-height="260" empty-text="暂无付款">
                      <el-table-column label="付款单" prop="paymentCode" width="160" show-overflow-tooltip />
                      <el-table-column label="类型" width="86" align="center"><template #default="{ r }"><el-tag size="small" effect="plain">{{ dictLabel('payment_type', r.paymentType) }}</el-tag></template></el-table-column>
                      <el-table-column label="实付（元）" width="110" align="right"><template #default="{ r }"><span class="num">{{ money(r.paidAmount) }}</span></template></el-table-column>
                      <el-table-column label="付款日期" width="104" align="center"><template #default="{ r }">{{ fmtDate(r.paidDate) }}</template></el-table-column>
                      <el-table-column label="状态" width="86" align="center"><template #default="{ r }"><el-tag size="small" :type="statusColor(r.status)">{{ r.status }}</el-tag></template></el-table-column>
                    </el-table>
                  </div>
                </el-col>
              </el-row>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="合同" min-width="210">
          <template #default="{ row }">
            <div class="c-name">{{ row.name }}</div>
            <div class="c-code num">{{ row.contractCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="承包商" prop="supplierName" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center"><template #default="{ row }"><el-tag size="small" effect="plain">{{ dictLabel('contract_type', row.contractType) }}</el-tag></template></el-table-column>
        <el-table-column label="阶段" width="92" align="center"><template #default="{ row }"><el-tag size="small" :type="stageColor(row.status)">{{ STAGE[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="签约（元）" width="118" align="right"><template #default="{ row }"><span class="num">{{ money(row.signed) }}</span></template></el-table-column>
        <el-table-column label="确认变更（元）" width="120" align="right"><template #default="{ row }"><span class="num" :style="row.change > 0 ? 'color:#c45656' : ''">{{ money(row.change) }}</span></template></el-table-column>
        <el-table-column label="付款进度" min-width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.paymentPct" :stroke-width="10" :color="row.paymentPct >= 100 ? '#529b2e' : '#185fa5'" style="margin-bottom:4px" />
            <div class="cell-sub">已付 <span class="num">{{ money(row.paid) }}</span> / 应付 <span class="num">{{ money(row.total) }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="履约进度" min-width="140">
          <template #default="{ row }">
            <div class="cell-sub">工期 <b class="num">{{ row.schedulePct == null ? '--' : row.schedulePct + '%' }}</b> · 质保金 <b class="num">{{ row.retentionPct }}%</b></div>
            <el-progress :percentage="row.schedulePct ?? 0" :stroke-width="6" color="#185fa5" />
          </template>
        </el-table-column>
        <el-table-column label="变更率" width="80" align="center"><template #default="{ row }"><span class="num" :style="row.changeRate >= 5 ? 'color:#c45656;font-weight:700' : ''">{{ row.changeRate }}%</span></template></el-table-column>
        <el-table-column label="结算" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SETTLED'" size="small" type="success" effect="dark">已结算</el-tag>
            <el-tag v-else size="small" effect="plain">未结算</el-tag>
            <div class="cell-sub" v-if="row.settledAmount">金额 <span class="num">{{ money(row.settledAmount) }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="78" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="toggleRow(row)">{{ expandedKeys.includes(row.id) ? '收起' : '详情' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 结算对话框 -->
    <el-dialog v-model="settleVisible" title="合同结算" width="480px">
      <el-alert type="info" :closable="false" show-icon class="settle-tip">
        结算金额须等于「签约金额 + 确认变更 − 扣款」= <span class="num">{{ money(settleForm.amount) }}</span> 元，否则后端将驳回。
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="结算金额" required><el-input-number v-model="settleForm.amount" :controls="false" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSettle">确认结算</el-button>
      </template>
    </el-dialog>

    <!-- 质保金释放对话框 -->
    <el-dialog v-model="retentionVisible" title="质保金释放" width="480px">
      <el-form label-width="90px">
        <el-form-item label="释放金额" required><el-input-number v-model="retentionForm.amount" :controls="false" :precision="2" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="可释放余额"><span class="num">{{ money(Math.max(0, retentionForm.cap - retentionForm.released)) }}</span></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retentionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doRetention">确认释放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { money, fmtDate, statusColor, dictLabel } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions,
  fetchChanges, fetchPayments, fetchContractPaymentPlans, fetchContractChangeRate,
  settleContract, releaseRetention
} from '../../../api/cost'

const STAGE = { DRAFT: '编制', APPROVING: '评审中', EFFECTIVE: '履约中', SETTLED: '已结算', CLOSED: '已关闭' }
const stageColor = s => ({ DRAFT: 'info', APPROVING: 'warning', EFFECTIVE: 'primary', SETTLED: 'success', CLOSED: 'info' }[s] || 'info')

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const query = reactive({ projectId: null, kw: '' })

const contracts = ref([])
const allChanges = ref([])
const allPayments = ref([])
const expandedKeys = ref([])
const detailCache = reactive({}) // { contractId: { plans, changeRate, loaded } }

const settleVisible = ref(false)
const retentionVisible = ref(false)
const settleForm = reactive({ id: null, amount: null })
const retentionForm = reactive({ id: null, amount: null, cap: 0, released: 0 })

// ---------- 聚合计算 ----------
const groupSum = (list, key, fn) => {
  const m = new Map()
  for (const it of list) {
    const k = it[key]
    if (k == null) continue
    m.set(k, (m.get(k) || 0) + (fn(it) || 0))
  }
  return m
}
const calcSchedule = (s, e) => {
  if (!s || !e) return null
  const start = new Date(s).getTime(), end = new Date(e).getTime()
  if (end <= start) return null
  return Math.max(0, Math.min(100, Math.round((Date.now() - start) / (end - start) * 100)))
}

const baseRows = computed(() => {
  const changeBy = groupSum(allChanges.value, 'contractId', c => c.status === 'CONFIRMED' ? (Number(c.confirmedAmount) || 0) - (Number(c.deductAmount) || 0) : 0)
  const paidBy = groupSum(allPayments.value, 'contractId', p => Number(p.paidAmount) || 0)
  const countBy = groupSum(allChanges.value.filter(c => c.status === 'CONFIRMED'), 'contractId', () => 1)
  return contracts.value.map(c => {
    const signed = Number(c.signedAmount) || 0
    const change = changeBy.get(c.id) || 0
    const paid = paidBy.get(c.id) || 0
    const total = signed + change
    const unpaid = Math.max(0, total - paid)
    const paymentPct = total > 0 ? Math.min(100, Math.round(paid / total * 100)) : 0
    const schedulePct = calcSchedule(c.startDate, c.endDate)
    const retentionTotal = signed * ((Number(c.retentionRate) || 0) / 100)
    const retentionReleased = Number(c.retentionReleased) || 0
    const retentionPct = retentionTotal > 0 ? Math.min(100, Math.round(retentionReleased / retentionTotal * 100)) : 0
    const changeCount = countBy.get(c.id) || 0
    const changeRate = signed > 0 ? Math.round(change / signed * 1000) / 10 : 0
    return { ...c, signed, change, paid, total, unpaid, paymentPct, schedulePct, retentionTotal, retentionReleased, retentionPct, changeCount, changeRate }
  })
})

const rows = computed(() => {
  const kw = (query.kw || '').trim().toLowerCase()
  if (!kw) return baseRows.value
  return baseRows.value.filter(r =>
    (r.name || '').toLowerCase().includes(kw) ||
    (r.contractCode || '').toLowerCase().includes(kw) ||
    (r.supplierName || '').toLowerCase().includes(kw)
  )
})

const summary = computed(() => {
  const sum = k => rows.value.reduce((s, r) => s + (Number(r[k]) || 0), 0)
  return {
    count: rows.value.length,
    signed: sum('signed'),
    change: sum('change'),
    paid: sum('paid'),
    unpaid: sum('unpaid'),
    settledCount: rows.value.filter(r => r.status === 'SETTLED').length
  }
})

const changesOf = cid => allChanges.value.filter(c => c.contractId === cid)
const paymentsOf = cid => allPayments.value.filter(p => p.contractId === cid)

// ---------- 数据加载 ----------
const loadData = async () => {
  if (!query.projectId) { contracts.value = []; allChanges.value = []; allPayments.value = []; return }
  loading.value = true
  try {
    const [cs, chPage, payPage] = await Promise.all([
      fetchContractOptions({ projectId: query.projectId }),
      fetchChanges({ projectId: query.projectId, current: 1, size: 1000 }),
      fetchPayments({ projectId: query.projectId, current: 1, size: 1000 })
    ])
    contracts.value = cs || []
    allChanges.value = chPage?.records || []
    allPayments.value = payPage?.records || []
    expandedKeys.value = []
    for (const k in detailCache) delete detailCache[k]
  } finally {
    loading.value = false
  }
}
const onProjectChange = async () => { await loadData() }
const onSearch = () => { /* kw 过滤为前端 computed，无需请求 */ }

// ---------- 下钻懒加载 ----------
const onExpandChange = (row, expandedRows) => {
  expandedKeys.value = expandedRows.map(r => r.id)
  if (expandedRows.includes(row) && !detailCache[row.id]?.loaded) loadDetail(row.id)
}
const toggleRow = row => {
  const i = expandedKeys.value.indexOf(row.id)
  expandedKeys.value = i >= 0 ? expandedKeys.value.filter(k => k !== row.id) : [...expandedKeys.value, row.id]
}
const loadDetail = async contractId => {
  if (detailCache[contractId]?.loaded) return
  const [plans, rate] = await Promise.all([
    fetchContractPaymentPlans(contractId).catch(() => []),
    fetchContractChangeRate(contractId).catch(() => null)
  ])
  detailCache[contractId] = { plans: plans || [], changeRate: rate || null, loaded: true }
}

// ---------- 结算 / 质保金 ----------
const onSettle = row => { settleForm.id = row.id; settleForm.amount = row.total; settleVisible.value = true }
const doSettle = async () => {
  if (settleForm.amount == null) return ElMessage.warning('请填写结算金额')
  saving.value = true
  try {
    await settleContract(settleForm.id, { settledAmount: settleForm.amount })
    ElMessage.success('结算完成')
    settleVisible.value = false
    delete detailCache[settleForm.id]
    await loadData()
  } finally { saving.value = false }
}
const onRetention = row => {
  retentionForm.id = row.id
  retentionForm.cap = row.retentionTotal
  retentionForm.released = row.retentionReleased
  retentionForm.amount = Math.max(0, row.retentionTotal - row.retentionReleased)
  retentionVisible.value = true
}
const doRetention = async () => {
  if (retentionForm.amount == null || retentionForm.amount <= 0) return ElMessage.warning('请填写释放金额')
  saving.value = true
  try {
    await releaseRetention(retentionForm.id, { releaseAmount: retentionForm.amount })
    ElMessage.success('质保金已释放')
    retentionVisible.value = false
    delete detailCache[retentionForm.id]
    await loadData()
  } finally { saving.value = false }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) query.projectId = projects.value[0].id
  await loadData()
})
</script>

<style scoped>
.head-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; }

.stat-row { margin-bottom: 12px; }
.stat-card .unit { font-size: 13px; font-weight: 500; color: #6b7280; margin-left: 2px; }

.card-title { font-size: 14px; font-weight: 600; color: #0c447c; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid #185fa5; }

.ov-table { cursor: default; }
.ov-table :deep(.el-table__row) { cursor: pointer; }
.c-name { font-size: 13.5px; font-weight: 600; color: #1f2937; }
.c-code { font-size: 12px; color: #909399; margin-top: 2px; }
.cell-sub { font-size: 11.5px; color: #6b7280; }
.cell-sub b { color: #185fa5; font-weight: 600; }

.detail { padding: 14px 18px 6px; }
.detail-actions { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; padding: 10px 12px; background: #f4f8fc; border-radius: 6px; }
.da-hint { font-size: 12.5px; color: #6b7280; }
.detail-grid .el-col { min-height: 120px; }
.d-block { background: #fafbfc; border: 1px solid #eef1f5; border-radius: 6px; padding: 12px; }
.d-title { font-size: 13px; font-weight: 600; color: #0c447c; margin-bottom: 10px; }
.d-progress { display: flex; flex-direction: column; gap: 10px; }
.dp-line { display: flex; align-items: center; gap: 10px; }
.dp-line > span:first-child { font-size: 12.5px; color: #6b7280; width: 74px; flex: none; }
.dp-pct { font-size: 12.5px; color: #185fa5; font-weight: 700; width: 42px; text-align: right; flex: none; }
.d-meta { font-size: 12px; color: #909399; margin-top: 10px; line-height: 1.6; }
.d-co { display: flex; flex-direction: column; gap: 7px; }
.dco-item { display: flex; justify-content: space-between; font-size: 12.5px; color: #6b7280; }
.dco-item b { color: #1f2937; font-weight: 600; }

.settle-tip { margin-bottom: 14px; }
</style>
