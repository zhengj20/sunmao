<template>
  <div class="page-container funnel">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
        <span class="hint">蓄客漏斗：来访 → 认购 → 签约 → 回款 全链路转化分析</span>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 环节 KPI -->
      <el-row :gutter="12" class="stat-row">
        <el-col v-for="s in stages" :key="s.stage" :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="label">{{ s.name }}</div>
            <div class="value" :style="{ color: stageColor(s.stage) }">{{ s.count ?? 0 }}</div>
            <div class="sub">{{ stageDesc(s.stage) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="12" class="section-row">
        <!-- 漏斗图 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">蓄客漏斗</span></template>
            <ChartBox v-if="stages.length" :option="funnelOption" height="340px" />
            <el-empty v-else description="暂无蓄客数据" :image-size="70" />
          </el-card>
        </el-col>

        <!-- 转化率 -->
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span class="card-title">环节转化率</span></template>
            <div v-if="conversions.length" class="conv-list">
              <div v-for="c in conversions" :key="c.from" class="conv-item">
                <div class="conv-name">{{ c.from }} → {{ c.to }}</div>
                <div class="conv-bar">
                  <el-progress :percentage="Math.min(Number(c.rate) || 0, 100)" :stroke-width="16" :color="rateColor(c.rate)" />
                </div>
                <div class="conv-rate">{{ c.rate ?? 0 }}%</div>
              </div>
            </div>
            <el-empty v-else description="暂无转化数据" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { fetchProjectOptions, dashboardFunnel } from '../../../api/sales'
import ChartBox from '../../../components/ChartBox.vue'

const STAGE_COLOR = { visit: '#5687ba', subscription: '#185fa5', contract: '#0c447c', paid: '#67c23a' }
const STAGE_DESC = {
  visit: '案场来访登记',
  subscription: '有效认购单',
  contract: '生效/网签合同',
  paid: '已回款合同'
}

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const data = ref({})

const stages = computed(() => data.value.stages || [])
const conversions = computed(() => data.value.conversions || [])
const stageColor = s => STAGE_COLOR[s] || '#185fa5'
const stageDesc = s => STAGE_DESC[s] || ''
const rateColor = rate => (Number(rate) >= 60 ? '#67c23a' : Number(rate) >= 30 ? '#185fa5' : '#e6a23c')

const load = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    data.value = (await dashboardFunnel(projectId.value)) || {}
  } catch (e) {
    data.value = {}
  } finally {
    loading.value = false
  }
}

const funnelOption = computed(() => ({
  color: ['#5687ba', '#185fa5', '#0c447c', '#67c23a'],
  tooltip: { trigger: 'item', formatter: '{b}: {c}' },
  series: [{
    type: 'funnel', left: '8%', right: '8%', top: 20, bottom: 20,
    min: 0, minSize: '30%', maxSize: '100%', sort: 'descending', gap: 4,
    label: { show: true, position: 'inside', formatter: '{b}\n{c}' },
    itemStyle: { borderColor: '#fff', borderWidth: 1 },
    data: stages.value.map(s => ({ name: s.name, value: Number(s.count) }))
  }]
}))

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
.stat-card .value { font-size: 26px; font-weight: 700; font-variant-numeric: tabular-nums; }
.stat-card .sub { font-size: 12px; color: #909399; margin-top: 4px; }
.section-row { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.conv-list { display: flex; flex-direction: column; gap: 18px; padding-top: 6px; }
.conv-item { display: grid; grid-template-columns: 150px 1fr 70px; align-items: center; gap: 12px; }
.conv-name { font-size: 13px; color: #4b5563; font-weight: 600; }
.conv-rate { font-size: 15px; font-weight: 700; color: #0c447c; text-align: right; font-variant-numeric: tabular-nums; }
</style>
