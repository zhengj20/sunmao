<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="snapMonth" placeholder="选择快照月份" style="width: 160px" @change="loadDetail">
            <el-option v-for="s in snapshots" :key="s.snapMonth" :label="s.snapMonth" :value="s.snapMonth" />
          </el-select>
          <el-button :icon="Refresh" @click="reload">刷新</el-button>
        </div>
        <span class="snap-tip">动态成本 = 已签合同 + 已确认变更 + 预估变更 + 非合约支出，事件驱动月度快照重算</span>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">目标成本（元）</div>
          <div class="value">{{ money(detail?.targetTotal) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">动态成本合计（元）</div>
          <div class="value" :style="dynamicOver ? 'color:#c45656' : ''">{{ money(detail?.dynamicTotal) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">已发生成本（元）</div>
          <div class="value">{{ money(occurred) }}</div>
          <div class="label sub">已签 {{ money(detail?.contractedTotal) }} + 确认变更 {{ money(detail?.confirmedChange) }} + 非合约 {{ money(detail?.nonContract) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">待发生余量（元）</div>
          <div class="value">{{ money(detail?.remaining) }}</div>
          <div class="label sub">含预估变更 {{ money(detail?.estimatedChange) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="warnings.length > 0" type="warning" :closable="false" show-icon class="warning-alert">
      <template #title>
        成本预警（{{ warnings.length }} 条）
      </template>
      <div class="warning-list">
        <div v-for="(w, idx) in warnings" :key="idx" class="warning-item" :class="'level-' + w.level.toLowerCase()">
          <el-tag :type="w.level === 'HIGH' ? 'danger' : 'warning'" size="small">{{ w.level === 'HIGH' ? '高' : '中' }}</el-tag>
          <span>{{ w.message }}</span>
        </div>
      </div>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <div class="table-head">
        <span class="title">科目级目标成本 vs 动态成本差异分析（{{ snapMonth || '暂无快照' }}）</span>
      </div>

      <div v-if="threeWayData" class="three-way-compare">
        <h4>三算对比</h4>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-card shadow="never" class="compare-card">
              <div class="compare-label">目标成本（元）</div>
              <div class="compare-value">{{ money(threeWayData.targetTotal) }}</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="compare-card">
              <div class="compare-label">动态成本（元）</div>
              <div class="compare-value" :style="threeWayData.targetVsDynamic > 0 ? 'color:#c45656' : 'color:#529b2e'">{{ money(threeWayData.dynamicTotal) }}</div>
              <div class="compare-diff">
                <span :style="threeWayData.targetVsDynamic > 0 ? 'color:#c45656' : 'color:#529b2e'">
                  {{ threeWayData.targetVsDynamic > 0 ? '+' : '' }}{{ money(threeWayData.targetVsDynamic) }}
                  ({{ threeWayData.targetVsDynamicRate }}%)
                </span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="compare-card">
              <div class="compare-label">实际发生成本（元）</div>
              <div class="compare-value" :style="threeWayData.targetVsActual > 0 ? 'color:#c45656' : 'color:#529b2e'">{{ money(threeWayData.actualTotal) }}</div>
              <div class="compare-diff">
                <span :style="threeWayData.targetVsActual > 0 ? 'color:#c45656' : 'color:#529b2e'">
                  {{ threeWayData.targetVsActual > 0 ? '+' : '' }}{{ money(threeWayData.targetVsActual) }}
                  ({{ threeWayData.targetVsActualRate }}%)
                </span>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="key"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        max-height="560"
      >
        <el-table-column prop="code" label="科目编码" width="110" />
        <el-table-column prop="name" label="科目名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'parent-name': !row.leaf }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="目标成本（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.targetAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已签合同（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.contractedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已确认变更（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.confirmedChange) }}</span></template>
        </el-table-column>
        <el-table-column label="预估变更（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.estimatedChange) }}</span></template>
        </el-table-column>
        <el-table-column label="动态成本（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ 'parent-amount': !row.leaf }">{{ money(dynamicOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="与目标差异（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :style="diffStyle(row)">{{ signedMoney(diffOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异率" width="90" align="right">
          <template #default="{ row }">
            <span class="num" :style="diffStyle(row)">{{ pctOf(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { money } from '../../../utils/format'
import { fetchProjectOptions, fetchAccountTree, fetchDynamicSnapshots, fetchDynamicDetail, fetchThreeWayCompare, fetchCostWarnings } from '../../../api/cost'

const loading = ref(false)
const projects = ref([])
const projectId = ref(null)
const snapshots = ref([])
const snapMonth = ref(null)
const detail = ref(null)
const accounts = ref([])
const treeData = ref([])
const threeWayData = ref(null)
const warnings = ref([])

/** 已发生 = 已签合同 + 已确认变更 + 非合约支出 */
const occurred = computed(() => {
  const d = detail.value
  if (!d) return null
  return (Number(d.contractedTotal) || 0) + (Number(d.confirmedChange) || 0) + (Number(d.nonContract) || 0)
})
const dynamicOver = computed(() => {
  const d = detail.value
  return d && Number(d.dynamicTotal) > Number(d.targetTotal)
})

const buildTree = () => {
  const map = new Map((detail.value?.details || []).map(d => [d.accountCode, d]))
  const walk = node => {
    const d = map.get(node.code)
    const row = {
      key: 'a' + node.id, code: node.code, name: node.name, leaf: !!node.leaf,
      targetAmount: d?.targetAmount ?? null,
      contractedAmount: d?.contractedAmount ?? null,
      confirmedChange: d?.confirmedChange ?? null,
      estimatedChange: d?.estimatedChange ?? null,
      children: (node.children || []).map(walk)
    }
    if (row.children.length) {
      row.targetAmount = sumField(row, 'targetAmount')
      row.contractedAmount = sumField(row, 'contractedAmount')
      row.confirmedChange = sumField(row, 'confirmedChange')
      row.estimatedChange = sumField(row, 'estimatedChange')
    }
    return row
  }
  return (accounts.value || []).map(walk)
}
const sumField = (row, field) => {
  const v = row.children.reduce((s, c) => s + (Number(c[field]) || 0), 0)
  return v || null
}

const dynamicOf = row =>
  row.contractedAmount != null || row.confirmedChange != null || row.estimatedChange != null
    ? (Number(row.contractedAmount) || 0) + (Number(row.confirmedChange) || 0) + (Number(row.estimatedChange) || 0)
    : null
const diffOf = row => {
  const dyn = dynamicOf(row)
  if (dyn == null && row.targetAmount == null) return null
  return dyn - (Number(row.targetAmount) || 0)
}
const pctOf = row => {
  const diff = diffOf(row)
  if (diff == null || !Number(row.targetAmount)) return '-'
  return ((diff / Number(row.targetAmount)) * 100).toFixed(2) + '%'
}
const diffStyle = row => {
  const diff = diffOf(row)
  return diff > 0 ? 'color:#c45656' : diff < 0 ? 'color:#529b2e' : ''
}
/** 带符号金额 */
const signedMoney = v => (v == null ? '-' : (v > 0 ? '+' : '') + money(v))

const loadProjects = async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    await onProjectChange()
  }
}

const onProjectChange = async () => {
  snapMonth.value = null
  const [snaps, tree] = await Promise.all([
    fetchDynamicSnapshots({ projectId: projectId.value }),
    fetchAccountTree({ projectId: projectId.value })
  ])
  snapshots.value = snaps || []
  accounts.value = tree || []
  if (snapshots.value.length) snapMonth.value = snapshots.value[0].snapMonth
  await loadDetail()
}

const loadDetail = async () => {
  if (!snapMonth.value) { detail.value = null; threeWayData.value = null; warnings.value = []; treeData.value = buildTree(); return }
  loading.value = true
  try {
    const [d, tw, warn] = await Promise.all([
      fetchDynamicDetail({ projectId: projectId.value, month: snapMonth.value }),
      fetchThreeWayCompare(projectId.value).catch(() => null),
      fetchCostWarnings(projectId.value).catch(() => null)
    ])
    detail.value = d
    threeWayData.value = tw
    warnings.value = warn?.warnings || []
  } finally {
    loading.value = false
  }
  treeData.value = buildTree()
}

const reload = async () => {
  const snaps = await fetchDynamicSnapshots({ projectId: projectId.value })
  snapshots.value = snaps || []
  await loadDetail()
}

onMounted(loadProjects)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; }
.snap-tip { font-size: 12.5px; color: #6b7280; }
.stat-row { margin-bottom: 12px; }
.stat-card .sub { margin-top: 2px; }
.table-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid #ebeef5; }
.table-head .title { font-size: 13px; color: #6b7280; }
.parent-name { font-weight: 600; color: #0c447c; }
.parent-amount { font-weight: 600; color: #0c447c; }
.three-way-compare { margin-bottom: 16px; }
.three-way-compare h4 { margin: 0 0 10px; font-size: 14px; color: #303133; }
.compare-card { text-align: center; }
.compare-label { color: #606266; font-size: 13px; }
.compare-value { font-size: 18px; font-weight: 600; color: #303133; margin: 8px 0; }
.compare-diff { font-size: 12px; color: #909399; }
.warning-alert { margin-bottom: 12px; }
.warning-list { margin-top: 8px; }
.warning-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.warning-item.level-high { color: #c45656; }
.warning-item.level-medium { color: #e6a23c; }
</style>
