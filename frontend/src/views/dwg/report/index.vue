<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="(label, key) in DRAWING_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="单号/标题" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-alert type="info" :closable="false" show-icon class="hint-alert">
      审查报告中心：针对每张已审查（或审查中）的图纸生成<strong>可打印 / 可下载</strong>的 HTML 报告，
      含图纸信息、AI 预审意见、审查问题清单（含解决状态）、审查结论与签署区，便于归档与对外交付。
    </el-alert>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="drawingCode" label="审查单号" width="150" show-overflow-tooltip />
        <el-table-column prop="title" label="图纸标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="专业" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ disciplineLabel(row.discipline) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="阶段" width="90" align="center">
          <template #default="{ row }">{{ stageLabel(row.stage) }}</template>
        </el-table-column>
        <el-table-column label="问题（总/待/已）" width="140" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.totalIssues || 0 }}</span>
            <span class="muted"> / </span>
            <span class="num" :class="{ 'val-warn': (row.openIssues || 0) > 0 }">{{ row.openIssues || 0 }}</span>
            <span class="muted"> / </span>
            <span class="num ok">{{ row.resolvedIssues || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="drawingStatusMeta(row.status)[1]" size="small">{{ drawingStatusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :loading="row._loading" @click="openReport(row)">查看报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, fetchDrawings, fetchDrawing, buildReportHtml,
  DISCIPLINES, DRAWING_STATUS, disciplineLabel, stageLabel, drawingStatusMeta
} from '../../../api/dwg'

const loading = ref(false)
const projects = ref([])
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const p = await fetchDrawings(query)
    rows.value = (p?.records || []).map(r => ({ ...r, _loading: false }))
    total.value = Number(p?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.kw = ''; onSearch() }

/** 生成并在新标签页打开审查报告（可打印/下载） */
const openReport = async row => {
  row._loading = true
  try {
    const d = await fetchDrawing(row.id)
    const html = buildReportHtml(d.drawing, d.issues || [], d.advisory || '')
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const win = window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    if (!win) {
      const a = document.createElement('a')
      a.href = url; a.download = `审查报告_${row.drawingCode}.html`
      document.body.appendChild(a); a.click(); document.body.removeChild(a)
      ElMessage.success('报告已下载')
    } else {
      ElMessage.success('报告已在新标签页打开，可打印或下载')
    }
  } finally {
    row._loading = false
  }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint-alert { margin-bottom: 12px; }
.pagination-bar { margin-top: 12px; text-align: right; }
.num { font-weight: 600; }
.muted { color: #a0a4ab; }
.ok { color: #529b2e; }
.val-warn { color: #c45656; }
</style>
