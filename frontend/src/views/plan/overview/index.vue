<template>
  <div class="page-container" v-loading="loading">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <span class="toolbar-title">计划总览（主项地铁图 / 专项进度 / 施工进度 / 指标预警）</span>
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目查看计划总览" :image-size="80" />

    <template v-else-if="data">
      <!-- KPI 指标卡 -->
      <el-row :gutter="12" class="kpi-row">
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">总进度</div>
          <div class="k-value" style="color:#185fa5">{{ num(kpi.overallProgress) }}<i>%</i></div>
          <div class="k-sub">已完成 {{ kpi.completedCount ?? 0 }} / 共 {{ kpi.taskTotal ?? 0 }} 项</div>
        </el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">里程碑达成率</div>
          <div class="k-value" style="color:#185fa5">{{ num(kpi.achievementRate) }}<i>%</i></div>
          <div class="k-sub">达成 {{ kpi.milestoneCompleted ?? 0 }} / 共 {{ kpi.milestoneTotal ?? 0 }}</div>
        </el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">进行中任务</div>
          <div class="k-value">{{ kpi.inProgressCount ?? 0 }}</div>
          <div class="k-sub" :style="Number(kpi.overdueCount) > 0 ? 'color:#c45656' : ''">超期 {{ kpi.overdueCount ?? 0 }} 项</div>
        </el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">红灯任务</div>
          <div class="k-value" :style="Number(kpi.redCount) > 0 ? 'color:#c45656' : 'color:#67c23a'">{{ kpi.redCount ?? 0 }}</div>
          <div class="k-sub">黄灯 {{ kpi.yellowCount ?? 0 }} 项</div>
        </el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">下一里程碑</div>
          <div class="k-value" style="font-size:16px">{{ nextMs ? nextMs.taskName : '全部达成' }}</div>
          <div class="k-sub" :style="nextMs && Number(nextMs.remainDays) < 0 ? 'color:#c45656;font-weight:700' : ''">
            <template v-if="nextMs">
              {{ fmtDate(nextMs.planEnd) }} · {{ Number(nextMs.remainDays) >= 0 ? `剩余 ${nextMs.remainDays} 天` : `已超 ${-Number(nextMs.remainDays)} 天` }}
            </template>
            <template v-else>恭喜</template>
          </div>
        </el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="kpi-card">
          <div class="k-label">当前阶段</div>
          <div class="k-value" style="font-size:16px">{{ kpi.currentPhase || '-' }}</div>
          <div class="k-sub">进行中的一级任务</div>
        </el-card></el-col>
      </el-row>

      <!-- 主项计划地铁图 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span class="card-title">主项计划地铁图（{{ subway.length }} 个里程碑，点击站点查看详情）</span>
            <div class="legend">
              <span><i class="lg-dot done" />已完成</span>
              <span><i class="lg-dot doing" />进行中</span>
              <span><i class="lg-dot red" />滞后</span>
              <span><i class="lg-dot yellow" />预警</span>
              <span><i class="lg-dot plan" />未开始</span>
            </div>
          </div>
        </template>
        <SubwayMap v-if="subway.length" :stations="subway" :overall-pct="num(kpi.overallProgress)" @select="openStation" />
        <el-empty v-else description="暂无里程碑" :image-size="60" />
      </el-card>

      <!-- 专项进度 + 施工进度 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">专项计划进度（按责任条线）</span></template>
            <SpecialtyProgress :rows="data.specialty || []" />
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">施工进度（楼栋 × 部位 · 已确认形象进度）</span></template>
            <ConstructionMatrix :rows="data.construction || []" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 指标统计与预警 -->
      <el-row :gutter="12" class="section-row">
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">红黄绿任务分布</span></template>
            <div class="dist">
              <div v-for="d in distRows" :key="d.alertStatus" class="dist-row">
                <span class="dist-name" :style="{ color: d.color }">{{ d.name }}</span>
                <div class="dist-track"><div class="dist-fill" :style="{ width: d.pct + '%', background: d.color }" /></div>
                <span class="dist-cnt num">{{ d.cnt }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">延期原因归类（滞后里程碑）</span></template>
            <div v-if="delayRows.length" class="dist">
              <div v-for="d in delayRows" :key="d.name" class="dist-row">
                <span class="dist-name">{{ d.name }}</span>
                <div class="dist-track"><div class="dist-fill" :style="{ width: d.pct + '%', background: '#e6a23c' }" /></div>
                <span class="dist-cnt num">{{ d.cnt }}</span>
              </div>
            </div>
            <el-empty v-else description="无滞后里程碑" :image-size="50" />
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">责任单位执行排名</span></template>
            <el-table :data="alertData.ranking || []" size="small" empty-text="暂无排名数据" max-height="260">
              <el-table-column prop="rank" label="#" width="44" align="center" />
              <el-table-column prop="orgName" label="责任单位" min-width="110" show-overflow-tooltip />
              <el-table-column prop="total" label="任务" width="60" align="center" />
              <el-table-column label="红/黄/绿" width="92" align="center">
                <template #default="{ row }"><span style="color:#c45656">{{ row.red }}</span> / <span style="color:#e6a23c">{{ row.yellow }}</span> / <span style="color:#67c23a">{{ row.green }}</span></template>
              </el-table-column>
              <el-table-column label="完成率" min-width="110">
                <template #default="{ row }"><el-progress :percentage="Number(row.onTimeRate) || 0" :stroke-width="10" /></template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="14">
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="card-title">红灯督办清单（{{ alertData.red?.length || 0 }}）</span>
                <el-tag v-if="alertData.pushRed" size="small" type="danger" effect="plain">红灯已推送集团运营</el-tag>
              </div>
            </template>
            <el-table :data="alertData.red || []" border stripe size="small" empty-text="无红灯任务" max-height="300">
              <el-table-column prop="taskName" label="任务" min-width="130" show-overflow-tooltip />
              <el-table-column prop="specialty" label="条线" width="70" align="center"><template #default="{ row }">{{ row.specialty || '-' }}</template></el-table-column>
              <el-table-column label="计划完成" width="100" align="center"><template #default="{ row }">{{ fmtDate(row.planEnd) }}</template></el-table-column>
              <el-table-column label="滞后(天)" width="80" align="center">
                <template #default="{ row }"><span style="color:#c45656;font-weight:700">{{ row.overdueDays }}</span></template>
              </el-table-column>
              <el-table-column prop="respUserName" label="责任人" width="90" align="center"><template #default="{ row }">{{ row.respUserName || '-' }}</template></el-table-column>
              <el-table-column label="推送" width="70" align="center">
                <template #default="{ row }"><el-tag v-if="row.pushed" size="small" type="danger" effect="plain">已推</el-tag><span v-else class="dim">-</span></template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never">
            <template #header><span class="card-title">黄灯关注清单（{{ alertData.yellow?.length || 0 }}）</span></template>
            <el-table :data="alertData.yellow || []" border stripe size="small" empty-text="无黄灯任务" max-height="300">
              <el-table-column prop="taskName" label="任务" min-width="130" show-overflow-tooltip />
              <el-table-column prop="specialty" label="条线" width="70" align="center"><template #default="{ row }">{{ row.specialty || '-' }}</template></el-table-column>
              <el-table-column label="计划完成" width="100" align="center"><template #default="{ row }">{{ fmtDate(row.planEnd) }}</template></el-table-column>
              <el-table-column label="滞后(天)" width="80" align="center">
                <template #default="{ row }"><span style="color:#e6a23c;font-weight:700">{{ row.overdueDays }}</span></template>
              </el-table-column>
              <el-table-column prop="respUserName" label="责任人" width="90" align="center"><template #default="{ row }">{{ row.respUserName || '-' }}</template></el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 站点详情 -->
    <el-dialog v-model="dlgVisible" title="里程碑详情" width="600px" destroy-on-close>
      <template v-if="dlg.station">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="里程碑">{{ dlg.station.taskName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(dlg.station.status, TASK_STATUS)[1]" size="small">{{ statusMeta(dlg.station.status, TASK_STATUS)[0] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计划起止" :span="2">{{ fmtDate(dlg.station.planStart) }} ~ {{ fmtDate(dlg.station.planEnd) }}</el-descriptions-item>
          <el-descriptions-item label="实际起止" :span="2">{{ fmtDate(dlg.station.actualStart) }} ~ {{ fmtDate(dlg.station.actualEnd) }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ Number(dlg.station.progressPct) || 0 }}%</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ dlg.task?.respUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="基线完成">{{ fmtDate(dlg.station.baselineEnd) }}</el-descriptions-item>
          <el-descriptions-item label="滞后天数">
            <span :style="Number(dlg.station.overdueDays) > 0 ? 'color:#c45656;font-weight:700' : ''">{{ dlg.station.overdueDays ?? 0 }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="impact">
          <div class="impact-head">下游连锁影响（{{ dlg.impact?.downstreamCount || 0 }} 个节点）</div>
          <el-table :data="dlg.impact?.downstream || []" size="small" max-height="200" empty-text="无下游影响">
            <el-table-column prop="taskName" label="节点" min-width="120" show-overflow-tooltip />
            <el-table-column label="计划完成" width="100" align="center"><template #default="{ row }">{{ fmtDate(row.planEnd) }}</template></el-table-column>
            <el-table-column label="进度" width="70" align="center"><template #default="{ row }">{{ Number(row.progressPct) || 0 }}%</template></el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }"><el-tag :type="statusMeta(row.status, TASK_STATUS)[1]" size="small">{{ statusMeta(row.status, TASK_STATUS)[0] }}</el-tag></template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, planOverview, taskDetail, alertImpact,
  TASK_STATUS, statusMeta
} from '../../../api/plan'
import SubwayMap from '../../../components/plan/SubwayMap.vue'
import SpecialtyProgress from '../../../components/plan/SpecialtyProgress.vue'
import ConstructionMatrix from '../../../components/plan/ConstructionMatrix.vue'

const route = useRoute()

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const data = ref(null)
const kpi = computed(() => data.value?.kpi || {})
const subway = computed(() => data.value?.subway || [])
const alertData = computed(() => data.value?.alert || {})

const distRows = computed(() => {
  const meta = { GREEN: ['正常', '#67c23a'], YELLOW: ['预警', '#e6a23c'], RED: ['滞后', '#c45656'] }
  const rows = (alertData.value.distribution || []).map(d => ({
    alertStatus: d.alertStatus, cnt: Number(d.cnt) || 0,
    name: meta[d.alertStatus]?.[0] || d.alertStatus, color: meta[d.alertStatus]?.[1] || '#909399'
  }))
  const max = Math.max(...rows.map(r => r.cnt), 1)
  rows.forEach(r => { r.pct = Math.round(r.cnt * 100 / max) })
  return rows
})

const delayRows = computed(() => {
  const obj = alertData.value.delayedByCategory || {}
  const rows = Object.entries(obj).map(([name, cnt]) => ({ name, cnt: Number(cnt) || 0 }))
  const max = Math.max(...rows.map(r => r.cnt), 1)
  rows.forEach(r => { r.pct = Math.round(r.cnt * 100 / max) })
  return rows
})

const nextMs = computed(() => kpi.value.nextMilestone || null)
const num = v => Number(v) || 0

// ===== 站点详情 =====
const dlgVisible = ref(false)
const dlg = ref({ station: null, task: null, impact: null })

const openStation = async s => {
  dlg.value = { station: s, task: null, impact: null }
  dlgVisible.value = true
  const [task, impact] = await Promise.all([
    taskDetail(s.id).catch(() => null),
    alertImpact(s.id).catch(() => null)
  ])
  dlg.value.task = task
  dlg.value.impact = impact
}

// ===== 加载 =====
const load = async () => {
  if (!projectId.value) { data.value = null; return }
  loading.value = true
  try {
    data.value = await planOverview(projectId.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  const qId = route.query.projectId
  if (qId) projectId.value = Number(qId)
  else if (projects.value.length) projectId.value = projects.value[0].id
  if (projectId.value) load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar-title { font-weight: 700; color: #0c447c; font-size: 14px; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { text-align: center; }
.k-label { font-size: 12.5px; color: #6b7280; }
.k-value { font-size: 26px; font-weight: 800; color: #1f2937; margin: 4px 0; font-variant-numeric: tabular-nums; }
.k-value i { font-style: normal; font-size: 14px; font-weight: 600; margin-left: 2px; }
.k-sub { font-size: 11.5px; color: #909399; }
.section-card { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 10px; }
.lg-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; margin-right: 4px; vertical-align: -1px; }
.lg-dot.done { background: #67c23a; }
.lg-dot.doing { background: #185fa5; }
.lg-dot.red { background: #f56c6c; }
.lg-dot.yellow { background: #e6a23c; }
.lg-dot.plan { background: #fff; border: 2px solid #c0c4cc; }
/* 分布条 */
.dist { padding: 6px 2px; }
.dist-row { display: grid; grid-template-columns: 44px 1fr 36px; gap: 10px; align-items: center; padding: 7px 0; }
.dist-name { font-size: 12.5px; font-weight: 600; }
.dist-track { height: 8px; background: #f0f2f5; border-radius: 4px; overflow: hidden; }
.dist-fill { height: 100%; border-radius: 4px; }
.dist-cnt { font-size: 13px; font-weight: 700; text-align: right; }
.num { font-variant-numeric: tabular-nums; }
.dim { color: #c0c4cc; }
/* 影响分析 */
.impact { margin-top: 12px; }
.impact-head { font-size: 13px; font-weight: 700; color: #0c447c; margin-bottom: 8px; }
</style>
