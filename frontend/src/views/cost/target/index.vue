<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="versionId" placeholder="选择目标成本版本" style="width: 300px" @change="loadDetail">
            <el-option v-for="v in versions" :key="v.id" :label="v.versionCode + '（' + dictLabel('target_stage', v.stage) + '）'" :value="v.id" />
          </el-select>
        </div>
        <div class="actions">
          <el-button @click="onExport" :disabled="!versionId">导出Excel</el-button>
          <el-button @click="onImport" :disabled="!editable">导入Excel</el-button>
          <el-button @click="openCompare" :disabled="versions.length < 2">版本对比</el-button>
          <el-button type="primary" @click="createVisible = true">新建版本</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">目标总成本（元）</div>
          <div class="value">{{ money(currentVersion?.totalAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">建筑面积（㎡）</div>
          <div class="value">{{ money(currentVersion?.buildArea) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">单方成本（元/㎡）</div>
          <div class="value">{{ money(unitCost) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">版本状态</div>
          <div class="value" style="font-size: 15px; padding-top: 4px">
            <el-tag :type="statusColor(currentVersion?.status)" v-if="currentVersion">
              {{ TARGET_STATUS[currentVersion.status] || currentVersion.status }}
            </el-tag>
            <span v-else>-</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <div class="table-head">
        <span class="title">科目明细（末级科目可编辑工程量与单价，金额 = 工程量 × 单价，上级科目自动汇总）</span>
        <div v-if="editable">
          <el-button type="primary" size="small" :loading="saving" @click="onSaveItems">保存明细</el-button>
          <el-button size="small" @click="onSubmit">提交审批</el-button>
        </div>
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
        <el-table-column prop="name" label="科目名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'parent-name': !row.leaf }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="controlMode" label="控制方式" width="80" align="center" />
        <el-table-column label="工程量" width="120" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="row.leaf && editable"
              v-model="row.quantity"
              :controls="false"
              :min="0"
              size="small"
              style="width: 100px"
              @change="recalcTree"
            />
            <span v-else class="num">{{ row.quantity ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column label="单价（元）" width="120" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="row.leaf && editable"
              v-model="row.unitPrice"
              :controls="false"
              :min="0"
              size="small"
              style="width: 100px"
              @change="recalcTree"
            />
            <span v-else class="num">{{ money(row.unitPrice) }}</span>
            <el-button v-if="row.leaf && editable && !row.unitPrice" link type="primary" size="small" @click="suggestIndicator(row)" style="margin-left: 4px">引用</el-button>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="150" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ 'parent-amount': !row.leaf }">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单方（元/㎡）" width="110" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.unitCost) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="indicatorRef" label="指标来源" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.indicatorRef || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建版本 -->
    <el-dialog v-model="createVisible" title="新建目标成本版本" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="版本阶段" required>
          <el-select v-model="createForm.stage" style="width: 100%">
            <el-option v-for="(label, key) in DICTS.target_stage" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="建筑面积" required>
          <el-input-number v-model="createForm.buildArea" :controls="false" :min="0" style="width: 100%" placeholder="㎡" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 版本对比 -->
    <el-dialog v-model="compareVisible" title="目标成本版本对比" width="860px" top="6vh">
      <div class="compare-bar">
        <el-select v-model="compareForm.v1" placeholder="基准版本 V1" style="width: 300px">
          <el-option v-for="v in versions" :key="v.id" :label="v.versionCode + '（' + dictLabel('target_stage', v.stage) + '）'" :value="v.id" />
        </el-select>
        <span class="vs">对比</span>
        <el-select v-model="compareForm.v2" placeholder="对比版本 V2" style="width: 300px">
          <el-option v-for="v in versions" :key="v.id" :label="v.versionCode + '（' + dictLabel('target_stage', v.stage) + '）'" :value="v.id" />
        </el-select>
        <el-button type="primary" @click="onCompare">开始对比</el-button>
      </div>
      <el-table
        v-if="compareData.length"
        :data="compareData"
        row-key="key"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        max-height="420"
      >
        <el-table-column prop="code" label="科目编码" width="100" />
        <el-table-column prop="name" label="科目名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="V1 金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount1) }}</span></template>
        </el-table-column>
        <el-table-column label="V2 金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount2) }}</span></template>
        </el-table-column>
        <el-table-column label="差额（元）" width="140" align="right">
          <template #default="{ row }">
            <span class="num" :style="diffColor(row.amount2 - row.amount1)">{{ money(diff(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="增幅" width="100" align="right">
          <template #default="{ row }">
            <span class="num" :style="diffColor(row.amount2 - row.amount1)">{{ pct(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="选择两个版本后开始对比" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, statusColor, dictLabel, DICTS } from '../../../utils/format'
import {
  fetchProjectOptions, fetchAccountTree, fetchTargetVersions, fetchTargetItems,
  createTargetVersion, saveTargetItems, submitTargetVersion,
  exportTargetExcel, importTargetExcel, suggestIndicators
} from '../../../api/cost'

const TARGET_STATUS = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已生效', LOCKED: '已锁定' }

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const projectId = ref(null)
const versions = ref([])
const versionId = ref(null)
const accounts = ref([])
const items = ref([])
const createVisible = ref(false)
const createForm = reactive({ stage: 'CONSTRUCTION', buildArea: null, remark: '' })
const compareVisible = ref(false)
const compareForm = reactive({ v1: null, v2: null })
const compareData = ref([])

const currentVersion = computed(() => versions.value.find(v => v.id === versionId.value) || null)
const editable = computed(() => currentVersion.value?.status === 'DRAFT')
const totalAmount = computed(() => {
  const roots = treeData.value
  return roots.reduce((s, r) => s + (Number(r.amount) || 0), 0)
})
const unitCost = computed(() => {
  const area = Number(currentVersion.value?.buildArea) || 0
  return area ? totalAmount.value / area : null
})

/** 科目树骨架 + 目标成本明细 合并为展示树 */
const buildTree = list => {
  const itemMap = new Map(items.value.map(i => [i.accountCode, i]))
  const walk = node => {
    const it = itemMap.get(node.code)
    const row = {
      key: 'a' + node.id,
      accountId: node.id, code: node.code, name: node.name,
      leaf: !!node.leaf, controlMode: node.controlMode || '',
      quantity: it?.quantity ?? null, unit: it?.unit || '',
      unitPrice: it?.unitPrice ?? null, amount: it?.amount ?? null,
      unitCost: it?.unitCost ?? null, indicatorRef: it?.indicatorRef || '',
      remark: it?.remark || '', children: []
    }
    ;(node.children || []).forEach(c => row.children.push(walk(c)))
    return row
  }
  const rows = (list || []).map(walk)
  sumUp(rows)
  return rows
}

/** 自底向上汇总父级金额，并计算末级单方 */
const sumUp = rows => {
  rows.forEach(r => {
    if (r.children?.length) {
      sumUp(r.children)
      r.amount = r.children.reduce((s, c) => s + (Number(c.amount) || 0), 0)
      r.unitCost = calcUnitCost(r.amount)
    } else {
      r.unitCost = calcUnitCost(r.amount)
    }
  })
}
const calcUnitCost = amount => {
  const area = Number(currentVersion.value?.buildArea) || 0
  return area && amount ? amount / area : null
}

const treeData = ref([])

const recalcTree = () => { sumUp(treeData.value) }

/** 引用指标库建议值 */
const suggestIndicator = async row => {
  if (!row.leaf || !row.code) return
  try {
    const indicators = await suggestIndicators({ accountCode: row.code })
    if (indicators && indicators.length > 0) {
      const ind = indicators[0]
      if (ind.value && !row.unitPrice) {
        row.unitPrice = Number(ind.value)
        row.indicatorRef = ind.source || ind.indicatorName
        recalcTree()
        ElMessage.success(`已引用指标：${ind.indicatorName} = ${ind.value} ${ind.unit || ''}`)
      } else {
        ElMessage.info('该科目已有单价或指标库无匹配数据')
      }
    } else {
      ElMessage.info('指标库暂无该科目的参考数据')
    }
  } catch { /* 忽略 */ }
}

const loadProjects = async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    await onProjectChange()
  }
}

const onProjectChange = async () => {
  versions.value = []
  versionId.value = null
  items.value = []
  const [vs, tree] = await Promise.all([
    fetchTargetVersions({ projectId: projectId.value }),
    fetchAccountTree({ projectId: projectId.value })
  ])
  versions.value = vs || []
  accounts.value = tree || []
  if (versions.value.length) versionId.value = versions.value[0].id
  await loadDetail()
}

const loadDetail = async () => {
  if (!versionId.value) { items.value = []; treeData.value = buildTree(accounts.value); return }
  loading.value = true
  try {
    items.value = (await fetchTargetItems(versionId.value)) || []
  } finally {
    loading.value = false
  }
  treeData.value = buildTree(accounts.value)
}

const onCreate = async () => {
  if (!createForm.stage || !createForm.buildArea) return ElMessage.warning('请填写版本阶段与建筑面积')
  saving.value = true
  try {
    const id = await createTargetVersion({ projectId: projectId.value, ...createForm })
    ElMessage.success('版本已创建，科目明细按项目科目树初始化')
    createVisible.value = false
    createForm.stage = 'CONSTRUCTION'
    createForm.buildArea = null
    createForm.remark = ''
    const vs = await fetchTargetVersions({ projectId: projectId.value })
    versions.value = vs || []
    versionId.value = id || (versions.value[0]?.id ?? null)
    await loadDetail()
  } finally {
    saving.value = false
  }
}

/** 收集末级科目明细批量保存 */
const collectLeafs = rows => rows.flatMap(r => r.children?.length ? collectLeafs(r.children) : [r])
const onSaveItems = async () => {
  const leafs = collectLeafs(treeData.value).filter(r => r.quantity != null || r.unitPrice != null || r.amount != null)
  if (!leafs.length) return ElMessage.warning('暂无可保存的明细')
  saving.value = true
  try {
    await saveTargetItems(versionId.value, leafs.map(r => ({
      accountId: r.accountId, accountCode: r.code, accountName: r.name,
      quantity: r.quantity, unit: r.unit, unitPrice: r.unitPrice,
      amount: r.amount, unitCost: r.unitCost, indicatorRef: r.indicatorRef, remark: r.remark
    })))
    ElMessage.success('明细已保存')
    const vs = await fetchTargetVersions({ projectId: projectId.value })
    versions.value = vs || []
    await loadDetail()
  } finally {
    saving.value = false
  }
}

const onSubmit = async () => {
  await ElMessageBox.confirm('提交审批后版本将进入审批流程，确认提交？', '提示', { type: 'warning' })
  await submitTargetVersion(versionId.value)
  ElMessage.success('已提交审批')
  const vs = await fetchTargetVersions({ projectId: projectId.value })
  versions.value = vs || []
}

const onExport = async () => {
  try {
    const data = await exportTargetExcel(versionId.value)
    if (!data || !data.length) return ElMessage.warning('暂无导出数据')
    // 简单的 CSV 导出
    const headers = Object.keys(data[0])
    const csv = [headers.join(',')]
    data.forEach(row => csv.push(headers.map(h => row[h] ?? '').join(',')))
    const blob = new Blob(['\ufeff' + csv.join('\n')], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `目标成本_${currentVersion.value?.versionCode || 'export'}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

const onImport = async () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.csv,.xlsx,.xls'
  input.onchange = async e => {
    const file = e.target.files[0]
    if (!file) return
    try {
      const text = await file.text()
      const lines = text.split('\n').filter(l => l.trim())
      if (lines.length < 2) return ElMessage.warning('文件无有效数据')
      const headers = lines[0].split(',').map(h => h.trim())
      const rows = []
      for (let i = 1; i < lines.length; i++) {
        const vals = lines[i].split(',')
        const row = {}
        headers.forEach((h, idx) => row[h] = vals[idx]?.trim() || '')
        rows.push(row)
      }
      const count = await importTargetExcel(versionId.value, rows)
      ElMessage.success(`成功导入 ${count} 条明细`)
      await loadDetail()
    } catch { ElMessage.error('导入失败，请检查文件格式') }
  }
  input.click()
}

const openCompare = () => {
  compareForm.v1 = versionId.value
  compareForm.v2 = versions.value.find(v => v.id !== versionId.value)?.id ?? null
  compareData.value = []
  compareVisible.value = true
}

const buildMap = list => new Map(list.map(i => [i.accountCode, i]))
const onCompare = async () => {
  if (!compareForm.v1 || !compareForm.v2) return ElMessage.warning('请选择两个版本')
  if (compareForm.v1 === compareForm.v2) return ElMessage.warning('请选择不同的版本')
  const [l1, l2] = await Promise.all([fetchTargetItems(compareForm.v1), fetchTargetItems(compareForm.v2)])
  const m1 = buildMap(l1 || []), m2 = buildMap(l2 || [])
  const walk = node => {
    const row = {
      key: 'a' + node.id, code: node.code, name: node.name, leaf: !!node.leaf,
      amount1: m1.get(node.code)?.amount ?? null,
      amount2: m2.get(node.code)?.amount ?? null,
      children: (node.children || []).map(walk)
    }
    if (row.children.length) {
      row.amount1 = row.children.reduce((s, c) => s + (Number(c.amount1) || 0), 0) || null
      row.amount2 = row.children.reduce((s, c) => s + (Number(c.amount2) || 0), 0) || null
    }
    return row
  }
  compareData.value = (accounts.value || []).map(walk)
}

const diff = row => {
  if (row.amount1 == null && row.amount2 == null) return null
  return (Number(row.amount2) || 0) - (Number(row.amount1) || 0)
}
const pct = row => {
  const d = diff(row)
  if (d == null || !Number(row.amount1)) return '-'
  return ((d / Number(row.amount1)) * 100).toFixed(2) + '%'
}
const diffColor = d => (d > 0 ? 'color:#c45656' : d < 0 ? 'color:#529b2e' : '')

onMounted(loadProjects)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; }
.stat-row { margin-bottom: 12px; }
.table-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid #ebeef5; }
.table-head .title { font-size: 13px; color: #6b7280; }
.parent-name { font-weight: 600; color: #0c447c; }
.parent-amount { font-weight: 600; color: #0c447c; }
.compare-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.compare-bar .vs { color: #6b7280; }
</style>
