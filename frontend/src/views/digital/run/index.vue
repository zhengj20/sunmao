<template>
  <div class="page-container">
    <div class="run-header">
      <div>
        <h2>任务中心</h2>
        <p class="run-sub">数字员工全部执行记录 — 手动 / 定时 / 条件触发 · 过程步骤完整回放</p>
      </div>
      <el-button :icon="Refresh" @click="loadList">刷新</el-button>
    </div>

    <!-- 执行概览 -->
    <div class="run-stats" v-if="stats">
      <div class="stat-card"><b>{{ stats.total }}</b><i>累计执行</i></div>
      <div class="stat-card"><b :class="{ run: stats.running > 0 }">{{ stats.running }}</b><i>执行中</i></div>
      <div class="stat-card"><b>{{ stats.successRate == null ? '—' : stats.successRate + '%' }}</b><i>成功率</i></div>
      <div class="stat-card"><b class="tiny">{{ fmtDur(stats.avgDurationMs) }}</b><i>平均耗时</i></div>
      <div class="stat-card" :title="`累计 Token 消耗（输入+输出）：${stats.totalTokens || 0}`"><b>{{ stats.llmRuns }}</b><i>LLM 分析</i></div>
      <div class="stat-card" :title="`累计 Token 消耗（输入+输出）：${stats.totalTokens || 0}`"><b>{{ stats.ruleRuns }}</b><i>规则引擎</i></div>
      <div class="stat-trend" title="近 7 天执行趋势">
        <ChartBox v-if="stats.trend && stats.trend.length" :option="trendOption" height="86px" />
        <div v-else class="muted small">近 7 天暂无执行数据</div>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filters">
        <el-select v-model="query.employeeId" placeholder="全部员工" clearable style="width: 180px" @change="onSearch">
          <el-option v-for="e in employees" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="(v, k) in RUN_STATUS" :key="k" :label="v[0]" :value="k" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="员工" width="170">
          <template #default="{ row }">
            <div class="emp-cell">
              <RoleAvatar :theme="avatarOf(row.employeeId)" :size="34" />
              <div>
                <b>{{ empName(row.employeeId) }}</b>
                <div class="muted small">{{ row.skillName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务" min-width="150" show-overflow-tooltip />
        <el-table-column label="触发" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="triggerMeta(row.triggerType)[1]">{{ triggerMeta(row.triggerType)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="runStatusMeta(row.status)[1]">
              <span v-if="row.status === 'RUNNING'" class="spin-inline"></span>
              {{ runStatusMeta(row.status)[0] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="160">
          <template #default="{ row }">{{ fmt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ fmtDur(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="模式" width="150" align="center">
          <template #default="{ row }">
            <template v-if="row.aiMode === 'LLM'">
              <el-tag size="small" type="primary" effect="light">LLM</el-tag>
              <div class="muted small">{{ row.modelName || '' }}</div>
            </template>
            <el-tag v-else size="small" effect="light">规则引擎</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewRun(row)">过程</el-button>
            <el-button link size="small" @click="viewResult(row)">结果</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 15, 30]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 过程回放 -->
    <el-dialog v-model="viewVisible" :title="viewData ? `执行过程 · ${viewData.taskName}` : '执行过程'" width="620px">
      <template v-if="viewData">
        <div class="view-meta">
          <el-tag size="small" :type="runStatusMeta(viewData.status)[1]">{{ runStatusMeta(viewData.status)[0] }}</el-tag>
          <el-tag size="small" :type="triggerMeta(viewData.triggerType)[1]">{{ triggerMeta(viewData.triggerType)[0] }}触发</el-tag>
          <el-tag v-if="viewData.aiMode" size="small" :type="viewData.aiMode === 'LLM' ? 'primary' : 'info'">
            {{ viewData.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}
          </el-tag>
          <span class="muted small">{{ fmt(viewData.startedAt) }} · 耗时 {{ fmtDur(viewData.durationMs) }}</span>
        </div>
        <div class="view-emp">
          <RoleAvatar :theme="avatarOf(viewData.employeeId)" :size="44" />
          <div>
            <b>{{ empName(viewData.employeeId) }}</b>
            <div class="muted small">执行任务「{{ viewData.taskName }}」· 技能「{{ viewData.skillName }}」</div>
          </div>
        </div>
        <RunSteps :steps="viewData.steps" />
        <div v-if="viewData.error" class="run-err">错误：{{ viewData.error }}</div>
      </template>
    </el-dialog>

    <!-- 结果 -->
    <el-dialog v-model="resultVisible" title="执行结果" width="620px">
      <div class="result-bar" v-if="resultData">
        <el-tag size="small" :type="runStatusMeta(resultData.status)[1]">{{ runStatusMeta(resultData.status)[0] }}</el-tag>
        <span class="muted small">{{ resultData.taskName }} · {{ fmt(resultData.startedAt) }}</span>
        <el-button link type="primary" size="small" @click="copyOutput">复制结果</el-button>
      </div>
      <pre v-if="resultData" class="result-pre">{{ resultData.output || '（无输出）' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import RoleAvatar from '../../../components/RoleAvatar.vue'
import RunSteps from '../../../components/RunSteps.vue'
import ChartBox from '../../../components/ChartBox.vue'
import { runPage, runDetail, runStats, empList, RUN_STATUS, triggerMeta, runStatusMeta } from '../../../api/digital'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const employees = ref([])
const stats = ref(null)
const query = reactive({ employeeId: null, status: null, current: 1, size: 15 })

const hasRunning = computed(() => stats.value ? stats.value.running > 0 : rows.value.some(r => r.status === 'RUNNING'))
let autoTimer = null

const loadList = async () => {
  loading.value = true
  try {
    const p = await runPage({
      current: query.current, size: query.size,
      employeeId: query.employeeId || undefined, status: query.status || undefined
    })
    rows.value = p?.records || []
    total.value = Number(p?.total) || 0
  } finally {
    loading.value = false
    startAuto() // 有执行中记录时保持自动刷新
  }
}

// ================= 执行概览 =================
const loadStats = async () => {
  try { stats.value = (await runStats()) || null } catch (e) { /* 概览失败不阻塞列表 */ }
}

const trendOption = computed(() => {
  const t = stats.value?.trend || []
  return {
    grid: { left: 8, right: 8, top: 18, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { show: true, top: 0, right: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 10 } },
    xAxis: {
      type: 'category', data: t.map(x => x.date),
      axisLine: { lineStyle: { color: '#d5e0ec' } }, axisLabel: { fontSize: 10, color: '#7b8a9c' }
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eef3f8' } }, axisLabel: { fontSize: 10 } },
    series: [
      { name: '完成', type: 'bar', stack: 't', data: t.map(x => x.done), itemStyle: { color: '#185fa5' }, barMaxWidth: 18 },
      { name: '失败', type: 'bar', stack: 't', data: t.map(x => x.failed), itemStyle: { color: '#e05656' }, barMaxWidth: 18 }
    ]
  }
})
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.employeeId = null; query.status = null; onSearch() }

const empName = id => employees.value.find(e => e.id === id)?.name || `员工#${id}`
const avatarOf = id => employees.value.find(e => e.id === id)?.avatar || 'custom'

const viewVisible = ref(false)
const viewData = ref(null)
const viewRun = async r => {
  viewVisible.value = true
  // 拉取最新详情（行数据可能滞后，执行中记录尤其需要实时步骤）
  viewData.value = r
  try { viewData.value = (await runDetail(r.id)) || r } catch (e) { /* 保留行数据兜底 */ }
}
const resultVisible = ref(false)
const resultData = ref(null)
const viewResult = async r => {
  resultData.value = r
  resultVisible.value = true
  try { resultData.value = (await runDetail(r.id)) || r } catch (e) { /* 保留行数据兜底 */ }
}
const copyOutput = async () => {
  const text = resultData.value?.output || ''
  if (!text) return ElMessage.warning('暂无可复制内容')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

const fmtDur = ms => ms == null ? '—' : (ms >= 1000 ? (ms / 1000).toFixed(1) + 's' : ms + 'ms')
const fmt = s => s ? String(s).replace('T', ' ').slice(5, 16) : '—'

// 有执行中记录时自动轮询刷新（列表 + 概览）
const startAuto = () => {
  stopAuto()
  if (hasRunning.value) autoTimer = setInterval(() => { loadList(); loadStats() }, 3000)
}
const stopAuto = () => { if (autoTimer) { clearInterval(autoTimer); autoTimer = null } }

onMounted(async () => {
  employees.value = (await empList()) || []
  loadStats()
  await loadList()
})
onBeforeUnmount(stopAuto)
</script>

<style scoped>
.run-header {
  display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 10px;
  padding: 6px 4px 14px;
}
.run-header h2 { margin: 0; font-size: 19px; color: #0c2d4e; }
.run-sub { margin: 6px 0 0; font-size: 12.5px; color: #7b8a9c; }
.filter-card { margin-bottom: 12px; }
.filters { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }

.run-stats {
  display: grid; grid-template-columns: repeat(6, minmax(96px, 150px)) minmax(220px, 1fr);
  gap: 10px; margin-bottom: 12px; align-items: stretch;
}
.stat-card {
  background: #fff; border: 1px solid #e3ecf5; border-radius: 10px;
  padding: 10px 12px; text-align: center;
}
.stat-card b { display: block; font-size: 19px; color: #0c2d4e; }
.stat-card b.tiny { font-size: 14px; font-family: "SF Mono", Consolas, monospace; }
.stat-card b.run { color: #185fa5; }
.stat-card i { font-style: normal; font-size: 11.5px; color: #9aa7b5; }
.stat-trend {
  background: #fff; border: 1px solid #e3ecf5; border-radius: 10px;
  padding: 6px 10px; display: flex; align-items: center; justify-content: center;
}
.result-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }

.emp-cell { display: flex; align-items: center; gap: 8px; }
.emp-cell b { font-size: 13px; color: #12293f; }
.spin-inline {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  border: 1.5px solid #b9cddd; border-top-color: #185fa5; margin-right: 4px;
  animation: sp-rot 0.8s linear infinite; vertical-align: middle;
}
@keyframes sp-rot { to { transform: rotate(360deg); } }

.view-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.view-emp {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
  padding: 10px 12px; background: #f7fafd; border-radius: 10px;
}
.run-err { margin-top: 12px; color: #c45656; font-size: 12.5px; }
.result-pre {
  margin: 0; font-size: 13px; line-height: 1.8; white-space: pre-wrap; word-break: break-all;
  color: #33445a; max-height: 420px; overflow: auto;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.muted { color: #909399; }
.small { font-size: 12px; }
</style>
