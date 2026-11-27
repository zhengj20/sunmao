<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="form.type" placeholder="报告类型" style="width: 150px" @change="onTypeChange">
            <el-option v-for="t in REPORT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <template v-if="form.type !== 'EMPLOYEE'">
            <el-select v-model="form.projectId" placeholder="项目（可选，空=集团）" clearable filterable style="width: 220px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-date-picker v-model="form.month" type="month" placeholder="月份（可选）" value-format="YYYY-MM" clearable style="width: 150px" />
          </template>
          <el-select v-else v-model="form.selectedTaskId" placeholder="选择任务" clearable filterable style="width: 250px">
            <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <el-button type="primary" :loading="generating" @click="onGenerate">生成报告</el-button>
        </div>
      </div>
    </el-card>

    <!-- 生成的报告 -->
    <template v-if="report">
      <el-card shadow="never" class="result-card">
        <template #header>
          <div class="card-head">
            <span class="card-title">{{ report.title }}</span>
            <el-tag :type="aiModeMeta(report.aiMode)[1]" effect="light" size="small">{{ aiModeMeta(report.aiMode)[0] }}</el-tag>
          </div>
        </template>

        <div v-for="(sec, i) in report.sections" :key="i" class="section">
          <div class="section-name">{{ sec.name }}</div>
          <section-body :data="sec.data" />
        </div>

        <div v-if="report.conclusions && report.conclusions.length" class="conclusion">
          <div class="section-name">规则结论</div>
          <ul class="conclusion-list">
            <li v-for="(c, i) in report.conclusions" :key="i">{{ c }}</li>
          </ul>
        </div>

        <el-alert v-if="report.advisory" type="info" :closable="false" show-icon class="advisory">
          <template #title>{{ report.advisory }}</template>
        </el-alert>
      </el-card>
    </template>
    <el-empty v-else description="选择报告类型后点击「生成报告」" :image-size="90" class="empty" />

    <!-- 报告列表 -->
    <el-card shadow="never" class="table-card">
      <template #header><span class="card-title">报告列表</span></template>
      <el-table v-loading="listLoading" :data="rows" border stripe>
        <el-table-column prop="taskCode" label="任务码" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'DONE' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 历史报告详情 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="52%">
      <section-body :data="drawerReport" :full="true" />
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, h, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  reportGenerate, reportList, reportDetail, reportByEmployee, fetchProjectOptions,
  REPORT_TYPES, aiModeMeta
} from '../../../api/ai'
import { taskList } from '../../../api/digital'

// 指标 key → 中文名
const KV_LABELS = {
  signedAmount: '签约额', receiptAmount: '回款额', receiptRate: '回款率', turnoverRate: '去化率',
  targetCost: '目标成本', dynamicCost: '动态成本', deviationRate: '偏差率', dynamicProfit: '动态利润', actualCost: '实际成本'
}
const kvLabel = k => KV_LABELS[k] || k

// 通用章节渲染：content 文本 / rows 列表 / key-value
const SectionBody = {
  props: { data: { type: Object, default: () => ({}) }, full: Boolean },
  setup(props) {
    return () => {
      const d = props.data || {}
      if (d.content != null) {
        return h('div', { class: 'llm-content' }, d.content)
      }
      if (Array.isArray(d.rows)) {
        const cols = d.rows.length ? Object.keys(d.rows[0]) : []
        return h('div', [
          h('el-table', { data: d.rows, border: true, stripe: true, size: 'small', 'empty-text': '暂无数据' },
            () => cols.map(c => h('el-table-column', { prop: c, label: c, minWidth: 120, 'show-overflow-tooltip': true }))
          )
        ])
      }
      const entries = Object.entries(d).filter(([, v]) => v != null)
      if (!entries.length) return h('div', { class: 'muted' }, '暂无数据')
      return h('div', { class: 'kv-grid' },
        entries.map(([k, v]) =>
          h('div', { class: 'kv-item', key: k }, [
            h('div', { class: 'kv-label' }, kvLabel(k)),
            h('div', { class: 'kv-value num' }, typeof v === 'number' ? money(v) : String(v))
          ])
        )
      )
    }
  }
}

const projects = ref([])
const tasks = ref([])
const form = reactive({ type: 'SALES', projectId: null, month: '', selectedTaskId: null })
const generating = ref(false)
const report = ref(null)

const rows = ref([])
const total = ref(0)
const listLoading = ref(false)
const query = reactive({ current: 1, size: 20 })
const loadList = async () => {
  listLoading.value = true
  try {
    const page = await reportList({ current: query.current, size: query.size })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    listLoading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }

const onTypeChange = async val => {
  if (val === 'EMPLOYEE' && !tasks.value.length) {
    tasks.value = (await taskList()) || []
  }
}

const onGenerate = async () => {
  if (!form.type) return
  if (form.type === 'EMPLOYEE' && !form.selectedTaskId) return ElMessage.warning('请选择任务')
  generating.value = true
  report.value = null
  try {
    if (form.type === 'EMPLOYEE') {
      report.value = await reportByEmployee(form.selectedTaskId)
    } else {
      report.value = await reportGenerate(form.type, form.projectId || undefined, form.month || undefined)
    }
    loadList()
  } catch (e) {
    report.value = null
  } finally {
    generating.value = false
  }
}

const drawerVisible = ref(false)
const drawerTitle = ref('')
const drawerReport = ref(null)
const onView = async row => {
  const task = await reportDetail(row.id)
  drawerTitle.value = task.title || row.title || '报告详情'
  let parsed = null
  try { parsed = task.output ? JSON.parse(task.output) : null } catch (e) { parsed = null }
  drawerReport.value = parsed || { sections: [], conclusions: [] }
  drawerVisible.value = true
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.result-card { margin-bottom: 12px; }
.empty { padding: 20px 0; }
.section { margin-bottom: 16px; }
.section-name { font-weight: 700; color: #0c447c; font-size: 13.5px; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #185fa5; }
.conclusion { margin-bottom: 12px; }
.conclusion-list { margin: 0; padding-left: 18px; }
.conclusion-list li { margin-bottom: 5px; line-height: 1.6; }
.advisory { margin-top: 4px; }
:deep(.kv-grid) { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
:deep(.kv-item) { background: #f5f8fc; border-radius: 6px; padding: 10px 12px; }
:deep(.kv-label) { font-size: 12px; color: #6b7280; }
:deep(.kv-value) { font-size: 18px; font-weight: 700; color: #185fa5; margin-top: 2px; }
:deep(.llm-content) { line-height: 1.7; color: #1f2937; white-space: pre-wrap; background: #f5f8fc; border-radius: 6px; padding: 12px; }
:deep(.muted) { color: #909399; }
</style>
