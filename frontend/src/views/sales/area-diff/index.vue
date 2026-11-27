<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="reload">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" plain @click="openImport()">批量导入</el-button>
          <el-button type="primary" @click="openCreate()">＋ 手工登记</el-button>
        </div>
        <el-button @click="reload">刷新</el-button>
      </div>
      <div class="hint">面积补差（多退少补）：合同面积 vs 实测面积，按单价生成差价款；少补→纳入回款收款，多退→生成退款待办。</div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card"><div class="label">待收·少补(元)</div><div class="value" style="color:#f56c6c">{{ money(stats.pendingCollect) }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card"><div class="label">待退·多退(元)</div><div class="value" style="color:#67c23a">{{ money(stats.pendingRefund) }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card"><div class="label">已结清(元)</div><div class="value" style="color:#185fa5">{{ money(Number(stats.settledCollect || 0) + Number(stats.settledRefund || 0)) }}</div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card"><div class="label">待结算 / 已结清(笔)</div><div class="value">{{ stats.pendingCount || 0 }} / {{ stats.settledCount || 0 }}</div></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <div class="filter-line">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="v in Object.keys(AREA_DIFF_STATUS)" :key="v" :label="AREA_DIFF_STATUS[v][0]" :value="v" />
        </el-select>
        <el-select v-model="query.direction" placeholder="方向" clearable style="width: 140px" @change="onSearch">
          <el-option v-for="v in Object.keys(AREA_DIFF_DIRECTION)" :key="v" :label="AREA_DIFF_DIRECTION[v][0]" :value="v" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="房号/客户/单号" clearable style="width: 180px" @keyup.enter="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="diffNo" label="补差单号" width="160" show-overflow-tooltip />
        <el-table-column prop="roomCode" label="房号" width="150" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="合同面积㎡" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.contractArea) }}</span></template>
        </el-table-column>
        <el-table-column label="实测面积㎡" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ num(row.measuredArea) }}</span></template>
        </el-table-column>
        <el-table-column label="面积差㎡" width="100" align="right">
          <template #default="{ row }">
            <span class="num" :style="{ color: diffColor(row) }">{{ (Number(row.diffArea) || 0) > 0 ? '+' : '' }}{{ num(row.diffArea) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价(元/㎡)" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.unitPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="差价款(元)" width="140" align="right">
          <template #default="{ row }">
            <span class="num" :style="{ color: diffColor(row), fontWeight: 600 }">{{ money(Math.abs(Number(row.diffAmount) || 0)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="110" align="center">
          <template #default="{ row }"><el-tag :type="dirMeta(row.direction)[1]" size="small">{{ dirMeta(row.direction)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="3%内" width="70" align="center">
          <template #default="{ row }"><el-tag v-if="row.withinTolerance" type="info" size="small">是</el-tag><span v-else>-</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status, AREA_DIFF_STATUS)[1]" size="small">{{ statusMeta(row.status, AREA_DIFF_STATUS)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="onSettle(row)">生成补差单</el-button>
            <el-button v-if="row.status === 'GENERATED'" link type="success" size="small" @click="onSettleDone(row)">标记已结清</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSearch"
        />
      </div>
    </el-card>

    <!-- 批量导入 -->
    <el-dialog v-model="imp.visible" title="批量导入面积补差" width="640px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        每行一条，逗号分隔：<b>房号, 合同面积, 实测面积, 单价</b>（合同面积/单价可留空自动带出；实测面积必填）。
      </el-alert>
      <el-input v-model="imp.csv" type="textarea" :rows="8" placeholder="PJ2026001-1#-002,139.5,145.8,28438&#10;PJ2026001-2#-101,120,115.2," />
      <el-form label-width="80px" style="margin-top:12px">
        <el-form-item label="备注">
          <el-input v-model="imp.remark" maxlength="100" placeholder="如：2026-09 实测报告批次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imp.visible = false">取消</el-button>
        <el-button type="primary" :loading="imp.saving" @click="onImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 手工登记 -->
    <el-dialog v-model="create.visible" title="手工登记面积补差" width="520px">
      <el-form label-width="100px">
        <el-form-item label="房号" required>
          <el-input v-model="create.roomCode" placeholder="如 PJ2026001-1#-002" />
        </el-form-item>
        <el-form-item label="合同面积㎡">
          <el-input-number v-model="create.contractArea" :controls="false" :min="0" :precision="2" style="width: 100%" placeholder="留空按房间面积" />
        </el-form-item>
        <el-form-item label="实测面积㎡" required>
          <el-input-number v-model="create.measuredArea" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="补差单价">
          <el-input-number v-model="create.unitPrice" :controls="false" :min="0" :precision="2" style="width: 100%" placeholder="留空按成交价折算" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="create.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="create.visible = false">取消</el-button>
        <el-button type="primary" :loading="create.saving" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchProjectOptions,
  areaDiffPage, areaDiffStats, areaDiffCreate, areaDiffImport,
  areaDiffSettle, areaDiffSettleDone, areaDiffDelete,
  AREA_DIFF_DIRECTION, AREA_DIFF_STATUS, statusMeta
} from '../../../api/sales'

const dirMeta = s => statusMeta(s, AREA_DIFF_DIRECTION)
const num = v => (v === null || v === undefined || v === '' ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }))
const diffColor = row => (Number(row.diffArea) || 0) > 0 ? '#f56c6c' : (Number(row.diffArea) || 0) < 0 ? '#67c23a' : '#909399'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ status: null, direction: null, keyword: '', current: 1, size: 20 })
const stats = reactive({ pendingCollect: 0, pendingRefund: 0, settledCollect: 0, settledRefund: 0, pendingCount: 0, settledCount: 0 })

const load = async () => {
  loading.value = true
  try {
    const page = await areaDiffPage({ current: query.current, size: query.size, projectId: projectId.value || undefined, status: query.status || undefined, direction: query.direction || undefined, keyword: query.keyword || undefined })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally { loading.value = false }
}
const loadStats = async () => {
  const s = await areaDiffStats(projectId.value || undefined).catch(() => null)
  if (s) Object.assign(stats, s)
}
const onSearch = () => { query.current = 1; load() }
const reload = () => { load(); loadStats() }

// 批量导入
const imp = reactive({ visible: false, saving: false, csv: '', remark: '' })
const openImport = () => { imp.csv = ''; imp.remark = ''; imp.visible = true }
const onImport = async () => {
  if (!projectId.value) return ElMessage.warning('请选择项目')
  if (!imp.csv.trim()) return ElMessage.warning('请填写导入内容')
  imp.saving = true
  try {
    const r = await areaDiffImport({ projectId: projectId.value, csv: imp.csv, remark: imp.remark })
    imp.visible = false
    ElMessage.success(`导入完成：成功 ${r.imported} 条，跳过 ${r.skipped} 条`)
    if (r.errors && r.errors.length) ElMessageBox.alert(r.errors.slice(0, 10).join('\n'), '导入提示（部分跳过）', { confirmButtonText: '知道了' })
    reload()
  } finally { imp.saving = false }
}

// 手工登记
const create = reactive({ visible: false, saving: false, roomCode: '', contractArea: null, measuredArea: null, unitPrice: null, remark: '' })
const openCreate = () => { Object.assign(create, { roomCode: '', contractArea: null, measuredArea: null, unitPrice: null, remark: '' }); create.visible = true }
const onCreate = async () => {
  if (!projectId.value) return ElMessage.warning('请选择项目')
  if (!create.roomCode) return ElMessage.warning('请填写房号')
  if (!create.measuredArea) return ElMessage.warning('请填写实测面积')
  create.saving = true
  try {
    await areaDiffCreate({ projectId: projectId.value, roomCode: create.roomCode, contractArea: create.contractArea || undefined, measuredArea: create.measuredArea, unitPrice: create.unitPrice || undefined, remark: create.remark || undefined })
    create.visible = false
    ElMessage.success('已登记，系统已自动计算面积差与差价款')
    reload()
  } finally { create.saving = false }
}

// 结算
const onSettle = async row => {
  const tip = row.direction === 'COLLECT' ? '将生成应收计划并纳入回款（少补收款）' : '将生成退款待办（多退退款）'
  await ElMessageBox.confirm(tip, '生成补差单', { confirmButtonText: '确认', cancelButtonText: '取消' })
  await areaDiffSettle(row.id)
  ElMessage.success('已生成补差单')
  reload()
}
const onSettleDone = async row => {
  const label = row.direction === 'COLLECT' ? '已收款' : '已退款'
  await ElMessageBox.confirm(`确认该补差已${label}？`, '标记已结清', { confirmButtonText: '确认', cancelButtonText: '取消' })
  await areaDiffSettleDone(row.id)
  ElMessage.success('已标记结清')
  reload()
}
const onDelete = async row => {
  await ElMessageBox.confirm('确认删除该补差记录？', '删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
  await areaDiffDelete(row.id)
  ElMessage.success('已删除')
  reload()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) { projectId.value = projects.value[0].id; reload() }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 8px; }
.filter-line { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.stat-card .label { font-size: 12px; color: #909399; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #1f2937; margin-top: 4px; }
.num { font-variant-numeric: tabular-nums; }
</style>
