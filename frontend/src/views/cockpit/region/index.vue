<template>
  <div class="page-container cockpit-region">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-select v-model="orgId" placeholder="选择区域组织" filterable clearable style="width: 240px" @change="load">
          <el-option v-for="o in orgOptions" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <span v-if="loaded" class="sub">{{ data.orgId ? '区域ID ' + data.orgId + ' · ' : '' }}项目 {{ projectMatrix.length }} 个</span>
      </div>
    </el-card>

    <el-empty v-if="!loaded || !orgId" description="请选择区域组织" :image-size="80" />

    <template v-else>
      <!-- 区域项目矩阵 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">区域项目矩阵（供货 / 去化 / 回款 / 成本偏差）</span></template>
        <el-table v-loading="loading" :data="projectMatrix" stripe size="small" empty-text="该区域暂无项目">
          <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="goProject(row.projectId)">{{ row.projectName }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="city" label="城市" width="90" align="center">
            <template #default="{ row }">{{ row.city || '-' }}</template>
          </el-table-column>
          <el-table-column label="供货货值(万)" width="120" align="right">
            <template #default="{ row }"><span class="num">{{ wan(row.supplyValue) }}</span></template>
          </el-table-column>
          <el-table-column label="去化率" width="100" align="center">
            <template #default="{ row }"><el-tag size="small" :type="turnoverTag(row.turnoverRate)" effect="light">{{ pct(row.turnoverRate) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="回款(万)" width="110" align="right">
            <template #default="{ row }"><span class="num">{{ wan(row.receiptAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="成本偏差(万)" width="120" align="right">
            <template #default="{ row }"><span class="num" :class="devClass(row.costDeviation)">{{ wan(row.costDeviation) }}</span></template>
          </el-table-column>
          <el-table-column label="偏差率" width="100" align="center">
            <template #default="{ row }"><span :class="devClass(row.costDeviation)">{{ pct(row.costDeviationRate) }}</span></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-row :gutter="12" class="section-row">
        <!-- 三算对比 -->
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">三算对比（目标 / 动态 / 实际）</span></template>
            <ChartBox :option="threeCalcOption" height="300px" />
            <div class="calc-foot">
              <div>动态vs目标 <b :class="devClass(threeCalc.dynamicVsTarget)">{{ wan(threeCalc.dynamicVsTarget) }}万</b></div>
              <div>实际vs动态 <b :class="devClass(threeCalc.actualVsDynamic)">{{ wan(threeCalc.actualVsDynamic) }}万</b></div>
            </div>
          </el-card>
        </el-col>
        <!-- 区域费效 -->
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">区域费效（三费构成 / 费效比）</span></template>
            <ChartBox :option="feeOption" height="230px" />
            <div class="calc-foot two">
              <div>三费总额 <b>{{ wan(fee.total) }}万</b></div>
              <div>营销费效 <b>{{ pct(fee.salesEfficiency) }}</b></div>
              <div>管理费效 <b>{{ pct(fee.adminEfficiency) }}</b></div>
            </div>
          </el-card>
        </el-col>
        <!-- 招采阳光度 -->
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="card-title">招采阳光度</span></template>
            <div class="sun-grid">
              <div class="sun-item"><div class="s-label">招采项目数</div><div class="s-value">{{ procurement.totalProjects ?? 0 }}</div></div>
              <div class="sun-item"><div class="s-label">公开招标数</div><div class="s-value">{{ procurement.publicBidCount ?? 0 }}</div></div>
              <div class="sun-item"><div class="s-label">阳光度(招标占比)</div><div class="s-value sun">{{ pct(procurement.sunshineRatio) }}</div></div>
              <div class="sun-item"><div class="s-label">定标/立项比</div><div class="s-value sun">{{ pct(procurement.awardBudgetRatio) }}</div></div>
              <div class="sun-item"><div class="s-label">立项预算(万)</div><div class="s-value">{{ wan(procurement.budgetAmount) }}</div></div>
              <div class="sun-item"><div class="s-label">定标金额(万)</div><div class="s-value">{{ wan(procurement.awardAmount) }}</div></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { cockpitRegion } from '../../../api/cockpit'
import { orgTree } from '../../../api/mdm'
import { wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const router = useRouter()

const loading = ref(false)
const loaded = ref(false)
const orgId = ref(null)
const orgOptions = ref([])
const data = ref({})
const projectMatrix = ref([])
const threeCalc = ref({})
const fee = ref({})
const procurement = ref({})

const pct = v => (v == null || v === '' ? '-' : Number(v).toFixed(1) + '%')
const devClass = v => (v == null ? '' : Number(v) > 0 ? 'dev-up' : Number(v) < 0 ? 'dev-down' : '')
const turnoverTag = v => {
  const n = Number(v)
  if (isNaN(n)) return 'info'
  return n >= 50 ? 'success' : n >= 25 ? 'warning' : 'danger'
}

const load = async () => {
  if (!orgId.value) return
  loading.value = true
  try {
    const d = await cockpitRegion(orgId.value)
    data.value = d || {}
    projectMatrix.value = d.projectMatrix || []
    threeCalc.value = d.threeCalc || {}
    fee.value = d.fee || {}
    procurement.value = d.procurement || {}
    loaded.value = true
  } finally {
    loading.value = false
  }
}

const goProject = id => router.push({ path: '/cockpit/project', query: { projectId: id } })

// 三算对比柱状
const threeCalcOption = computed(() => {
  const t = threeCalc.value || {}
  return {
    color: ['#8aadd2', '#185fa5', '#67c23a'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 70, right: 20, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: ['目标成本', '动态成本', '实际成本'], axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [{
      name: '金额', type: 'bar', barWidth: 46,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: [Number(t.targetCost || 0), Number(t.dynamicCost || 0), Number(t.actualCost || 0)],
      label: { show: true, position: 'top', formatter: p => wan(p.value) }
    }]
  }
})

// 费效构成环形
const feeOption = computed(() => {
  const f = fee.value || {}
  return {
    color: ['#185fa5', '#5687ba', '#e6a23c'],
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>${wan(p.value)} 万元（${p.percent}%）` },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 8 },
    series: [{
      name: '三费', type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { name: '销售费用', value: Number(f.salesFee || 0) },
        { name: '管理费用', value: Number(f.adminFee || 0) },
        { name: '财务费用', value: Number(f.finFee || 0) }
      ]
    }]
  }
})

onMounted(async () => {
  const tree = await orgTree().catch(() => [])
  const flat = []
  const walk = list => (list || []).forEach(o => { flat.push({ id: o.id, name: o.name }); walk(o.children) })
  walk(tree)
  orgOptions.value = flat
})
</script>

<style scoped>
.cockpit-region { max-width: 1560px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .sub { font-size: 13px; color: #4b5563; }
.section-card { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.calc-foot { display: flex; flex-direction: column; gap: 6px; padding: 10px 4px 0; border-top: 1px dashed #eef0f3; margin-top: 8px; }
.calc-foot div { display: flex; justify-content: space-between; font-size: 12.5px; color: #4b5563; }
.calc-foot b { color: #1f2937; }
.calc-foot.two { flex-direction: row; justify-content: space-around; }
.calc-foot.two div { display: block; text-align: center; }
.sun-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; padding-top: 6px; }
.sun-item { border: 1px solid #eef0f3; border-radius: 8px; padding: 12px; }
.s-label { font-size: 12px; color: #6b7280; margin-bottom: 6px; }
.s-value { font-size: 20px; font-weight: 700; color: #1f2937; font-variant-numeric: tabular-nums; }
.s-value.sun { color: #185fa5; }
.num { font-variant-numeric: tabular-nums; }
.dev-up { color: #f56c6c; font-weight: 600; }
.dev-down { color: #67c23a; font-weight: 600; }
</style>
