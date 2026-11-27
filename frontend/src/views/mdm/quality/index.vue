<template>
  <div class="page-container">
    <!-- 报告卡片 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">问题总数</div>
          <div class="value num">{{ report?.total ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col v-for="(cnt, sev) in (report?.bySeverity || {})" :key="'sev-' + sev" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">严重度 · {{ SEV_LABEL[sev] || sev }}</div>
          <div class="value num" :class="sev === 'WARN' ? 'warn' : ''">{{ cnt }}</div>
        </el-card>
      </el-col>
      <el-col v-for="(cnt, et) in (report?.byEntityType || {})" :key="'et-' + et" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">{{ ENTITY_LABEL[et] || et }}</div>
          <div class="value num">{{ cnt }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="topIssues.length" shadow="never" class="top-card">
      <div class="top-title">Top 问题类型</div>
      <div class="top-list">
        <el-tag v-for="t in topIssues" :key="t.issueType" type="warning" effect="plain" class="top-tag">
          {{ ISSUE_LABEL[t.issueType] || t.issueType }}：{{ t.count }}
        </el-tag>
      </div>
    </el-card>

    <!-- 工具条 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="query.severity" placeholder="严重度" clearable style="width: 130px" @change="search">
            <el-option label="警告" value="WARN" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已修复" value="FIXED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
          <el-select v-model="query.entityType" placeholder="主数据类型" clearable style="width: 140px" @change="search">
            <el-option label="供应商" value="SUPPLIER" />
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="scanning" @click="onScan">执行巡检</el-button>
      </div>
    </el-card>

    <!-- 问题列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="主数据类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ ENTITY_LABEL[row.entityType] || row.entityType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entityId" label="实体ID" width="100" align="right" />
        <el-table-column label="问题类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.issueType === 'DUPLICATE' ? 'danger' : 'warning'">
              {{ ISSUE_LABEL[row.issueType] || row.issueType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="问题描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="严重度" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.severity === 'WARN' ? 'warning' : 'danger'">{{ SEV_LABEL[row.severity] || row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ STATUS_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扫描时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.scannedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-button link type="success" @click="onResolve(row, 'FIXED')">标记已修复</el-button>
              <el-button link type="info" @click="onResolve(row, 'IGNORED')">忽略</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { runQualityScan, fetchQualityIssues, resolveIssue, fetchQualityReport } from '../../../api/quality'
import { fmtTime } from '../../../utils/format'

const SEV_LABEL = { WARN: '警告' }
const STATUS_LABEL = { OPEN: '待处理', FIXED: '已修复', IGNORED: '已忽略' }
const ENTITY_LABEL = { SUPPLIER: '供应商', CUSTOMER: '客户', PROJECT: '项目' }
const ISSUE_LABEL = { MISSING_VALUE: '缺失值', DUPLICATE: '重复' }

const statusType = s => (s === 'OPEN' ? 'warning' : s === 'FIXED' ? 'success' : 'info')

// ============ 报告卡片 ============
const report = ref(null)
const topIssues = computed(() => report.value?.topIssues || [])
const loadReport = async () => {
  try {
    report.value = await fetchQualityReport()
  } catch {
    report.value = null
  }
}

// ============ 问题列表 ============
const loading = ref(false)
const scanning = ref(false)
const rows = ref([])
const query = reactive({ severity: '', status: '', entityType: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = await fetchQualityIssues({
      current: page.current,
      size: page.size,
      severity: query.severity || undefined,
      status: query.status || undefined,
      entityType: query.entityType || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.current = 1
  load()
}

// ============ 执行巡检 ============
const onScan = async () => {
  scanning.value = true
  try {
    const res = await runQualityScan()
    ElMessage.success(`巡检完成：新增 ${res?.created ?? 0} 条，跳过重复 ${res?.skipped ?? 0} 条`)
    page.current = 1
    load()
    loadReport()
  } finally {
    scanning.value = false
  }
}

// ============ 处置 ============
const onResolve = (row, status) => {
  ElMessageBox.confirm(
    `确认将「${row.detail}」标记为「${STATUS_LABEL[status]}」？`,
    '处置确认',
    { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  )
    .then(async () => {
      await resolveIssue(row.id, status)
      ElMessage.success('处置成功')
      load()
      loadReport()
    })
    .catch(() => {})
}

onMounted(() => {
  load()
  loadReport()
})
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .label { font-size: 13px; color: #6b7280; margin-bottom: 6px; }
.stat-card .value { font-size: 22px; font-weight: 700; color: #1f2937; }
.stat-card .value.warn { color: #e6a23c; }
.top-card { margin-bottom: 12px; }
.top-title { font-size: 13.5px; font-weight: 700; color: #0c447c; margin-bottom: 10px; padding-left: 8px; border-left: 3px solid #185fa5; }
.top-list { display: flex; gap: 8px; flex-wrap: wrap; }
.top-tag { margin: 0; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filter-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: #c0c4cc; }
</style>
