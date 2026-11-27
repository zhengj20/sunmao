<template>
  <div class="page-container advisor">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        <span class="hint">置业顾问业绩：认购 · 签约 · 回款 三维排名（顾问=认购顾问，无认购时取合同中介）</span>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- KPI 汇总 -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">顾问人数</div><div class="value">{{ total.advisorCount ?? 0 }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">认购定金</div><div class="value">{{ wan(total.totalSubAmount) }}<span class="unit">万</span></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">签约金额</div><div class="value" style="color:#185fa5">{{ wan(total.totalConAmount) }}<span class="unit">万</span></div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">回款金额</div><div class="value" style="color:#67c23a">{{ wan(total.totalRecAmount) }}<span class="unit">万</span></div></el-card></el-col>
      </el-row>

      <el-row :gutter="12" class="section-row">
        <!-- 排名表 -->
        <el-col :span="14">
          <el-card shadow="never">
            <template #header><span class="card-title">业绩排名</span></template>
            <el-table v-if="list.length" :data="list" border stripe size="small" :default-sort="{ prop: 'rank', order: 'ascending' }">
              <el-table-column prop="rank" label="排名" width="70" align="center">
                <template #default="{ row }">
                  <span :class="['rank', 'rank-' + row.rank]">{{ row.rank }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="agentName" label="顾问" min-width="100" show-overflow-tooltip />
              <el-table-column prop="subCount" label="认购单" width="80" align="right"><template #default="{ row }">{{ row.subCount ?? 0 }}</template></el-table-column>
              <el-table-column label="认购定金(万)" width="110" align="right"><template #default="{ row }"><span class="num">{{ wan(row.subAmount) }}</span></template></el-table-column>
              <el-table-column prop="conCount" label="签约套" width="80" align="right"><template #default="{ row }">{{ row.conCount ?? 0 }}</template></el-table-column>
              <el-table-column label="签约金额(万)" width="110" align="right"><template #default="{ row }"><span class="num c-blue">{{ wan(row.conAmount) }}</span></template></el-table-column>
              <el-table-column label="回款(万)" width="100" align="right"><template #default="{ row }"><span class="num c-green">{{ wan(row.recAmount) }}</span></template></el-table-column>
            </el-table>
            <el-empty v-else description="暂无顾问业绩数据" :image-size="80" />
          </el-card>
        </el-col>
        <!-- 业绩对比图 -->
        <el-col :span="10">
          <el-card shadow="never">
            <template #header><span class="card-title">顾问签约 / 回款对比（万元）</span></template>
            <ChartBox v-if="list.length" :option="barOption" height="320px" />
            <el-empty v-else description="暂无数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { wan } from '../../../utils/format'
import { fetchProjectOptions, dashboardAdvisor } from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const data = ref({})

const total = computed(() => data.value.total || {})
const list = computed(() => data.value.list || [])

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    data.value = (await dashboardAdvisor(projectId.value)) || {}
  } catch (e) {
    data.value = {}
  } finally {
    loading.value = false
  }
}

const barOption = computed(() => {
  const names = list.value.map(a => a.agentName || '顾问')
  return {
    color: ['#185fa5', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => Number(v).toFixed(1) + ' 万元' },
    legend: { data: ['签约金额', '回款金额'], bottom: 0 },
    grid: { left: 60, right: 24, top: 24, bottom: 40 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: '万元' },
    series: [
      { name: '签约金额', type: 'bar', barWidth: 20, data: list.value.map(a => Number(a.conAmount) / 10000) },
      { name: '回款金额', type: 'bar', barWidth: 20, data: list.value.map(a => Number(a.recAmount) / 10000) }
    ]
  }
})

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.hint { font-size: 12px; color: #909399; }
.stat-row { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; font-weight: 700; color: #1f2937; font-variant-numeric: tabular-nums; }
.stat-card .unit { font-size: 12px; color: #909399; margin-left: 2px; font-weight: 400; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.num { font-variant-numeric: tabular-nums; }
.c-blue { color: #185fa5; }
.c-green { color: #67c23a; }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; border-radius: 50%; background: #f0f2f5; font-size: 12px; font-weight: 700; color: #6b7280; }
.rank-1 { background: #f56c6c; color: #fff; }
.rank-2 { background: #e6a23c; color: #fff; }
.rank-3 { background: #185fa5; color: #fff; }
</style>
