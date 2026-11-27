<template>
  <div class="page-container proc-cockpit">
    <!-- 顶部 KPI -->
    <div class="hero" v-loading="loading">
      <div class="hero-head">
        <div class="hero-title">招采驾驶舱</div>
        <div class="hero-sub">{{ today }} · 招采全链路指标 · 点击预警可定位</div>
      </div>
      <div class="hero-cards">
        <div class="hero-card">
          <div class="hc-label">立项总数</div>
          <div class="hc-value num">{{ cards.projectCount ?? 0 }}</div>
          <div class="hc-foot">在招 {{ cards.inProgressCount ?? 0 }} · 待审 {{ cards.pendingCount ?? 0 }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">已定标</div>
          <div class="hc-value num">{{ cards.awardedCount ?? 0 }}</div>
          <div class="hc-foot">定标额 {{ money(cards.awardedTotal) }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">预算总额</div>
          <div class="hc-value num">{{ money(cards.budgetTotal) }}</div>
          <div class="hc-foot">累计立项预算</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">平均采购周期</div>
          <div class="hc-value num">{{ cards.avgCycleDays ?? 0 }}<span class="unit"> 天</span></div>
          <div class="hc-foot">发标→定标</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">平均偏差率</div>
          <div class="hc-value num" :class="devColor(cards.avgDeviationPct)">{{ sign(cards.avgDeviationPct) }}%</div>
          <div class="hc-foot">定标价 vs 预算</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">公开招标占比</div>
          <div class="hc-value num">{{ cards.publicBidPct ?? 0 }}<span class="unit">%</span></div>
          <div class="hc-foot">阳光采购度</div>
        </div>
        <div class="hero-card warn">
          <div class="hc-label">超期立项</div>
          <div class="hc-value num">{{ cards.overdueCount ?? 0 }}</div>
          <div class="hc-foot">停滞超 30 天</div>
        </div>
        <div class="hero-card warn">
          <div class="hc-label">围串标预警</div>
          <div class="hc-value num">{{ cards.collusionCount ?? 0 }}</div>
          <div class="hc-foot">同IP / 梯度 / 关联</div>
        </div>
      </div>
    </div>

    <!-- 图表行 1：方式 / 阶段 / 周期 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">采购方式分布</span></template>
          <ChartBox v-if="methodDist.length" :option="methodOption" height="260px" />
          <el-empty v-else description="暂无数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">阶段分布（立项状态）</span></template>
          <ChartBox v-if="stageDist.length" :option="stageOption" height="260px" />
          <el-empty v-else description="暂无数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">采购周期趋势（发标→定标）</span></template>
          <ChartBox v-if="cycleTrend.length" :option="cycleOption" height="260px" />
          <el-empty v-else description="暂无数据" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 2：偏差 / 供应商 -->
    <el-row :gutter="12" class="section-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">预算 vs 定标（偏差分析）</span></template>
          <ChartBox v-if="deviation.length" :option="deviationOption" height="300px" />
          <el-empty v-else description="暂无定标数据" :image-size="70" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">中标供应商 Top</span></template>
          <el-table :data="supplierTop" size="small" stripe empty-text="暂无数据">
            <el-table-column label="#" width="50" align="center">
              <template #default="{ $index }"><span class="rank" :class="$index < 3 ? 'top' : ''">{{ $index + 1 }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="供应商" min-width="160" show-overflow-tooltip />
            <el-table-column prop="count" label="中标次数" width="90" align="center" />
            <el-table-column label="中标金额" width="120" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警聚合 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">招采预警聚合（{{ alerts.length }}）</span>
          <div class="legend">
            <el-tag size="small" type="danger" effect="plain">高</el-tag>
            <el-tag size="small" type="warning" effect="plain">中</el-tag>
          </div>
        </div>
      </template>
      <div v-if="alerts.length" class="risk-list">
        <div v-for="(a, i) in alerts" :key="i" class="risk-item" @click="goLink(a.link)">
          <el-tag size="small" :type="a.level === 'HIGH' ? 'danger' : 'warning'" effect="light">{{ a.level === 'HIGH' ? '高' : '中' }}</el-tag>
          <span class="risk-src">{{ typeLabel(a.type) }}</span>
          <span class="risk-title">{{ a.title }}</span>
          <span class="risk-desc">{{ a.desc }}</span>
          <el-icon class="risk-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <el-empty v-else description="暂无预警，招采状态良好" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowRight } from '@element-plus/icons-vue'
import { procCockpit, methodLabel } from '../../../api/proc'
import ChartBox from '../../../components/ChartBox.vue'

const router = useRouter()
const today = dayjs().format('YYYY年MM月DD日')

const loading = ref(false)
const cards = ref({})
const methodDist = ref([])
const stageDist = ref([])
const cycleTrend = ref([])
const deviation = ref([])
const supplierTop = ref([])
const alerts = ref([])

const STAGE = { DRAFT: '草稿', APPROVING: '立项审批', APPROVED: '已立项', ENTRY: '入围', TENDERING: '发标', EVALUATING: '评标', AWARDED: '已定标', CLOSED: '已归档' }
const STAGE_ORDER = ['DRAFT', 'APPROVING', 'APPROVED', 'ENTRY', 'TENDERING', 'EVALUATING', 'AWARDED', 'CLOSED']
const typeLabel = t => ({ COLLUSION: '围串标', BUDGET: '超预算', OVERDUE: '超期' }[t] || t)

const money = v => {
  v = Number(v || 0)
  if (Math.abs(v) >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (Math.abs(v) >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}
const sign = v => { v = Number(v || 0); return (v > 0 ? '+' : '') + v.toFixed(1) }
const devColor = v => (v == null || v === '' ? '' : Number(v) > 0 ? 'dev-up' : Number(v) < 0 ? 'dev-down' : '')

const load = async () => {
  loading.value = true
  try {
    const data = await procCockpit()
    cards.value = data.cards || {}
    methodDist.value = data.methodDist || []
    stageDist.value = data.stageDist || []
    cycleTrend.value = data.cycleTrend || []
    deviation.value = data.deviation || []
    supplierTop.value = data.supplierTop || []
    alerts.value = data.alerts || []
  } finally {
    loading.value = false
  }
}

const goLink = link => { if (link) router.push(link) }

// 采购方式分布（环形饼）
const methodOption = computed(() => ({
  color: ['#185fa5', '#5687ba', '#8aadd2', '#67c23a', '#e6a23c', '#f56c6c'],
  tooltip: { trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 11 } },
  series: [{ type: 'pie', radius: ['44%', '70%'], center: ['50%', '44%'], label: { show: false },
    data: methodDist.value.map(r => ({ name: methodLabel(r.method) || r.method, value: Number(r.count || 0) })) }]
}))

// 阶段分布（按业务流排序的柱状）
const stageOption = computed(() => {
  const map = Object.fromEntries(stageDist.value.map(r => [r.status, Number(r.count || 0)]))
  const names = STAGE_ORDER.filter(s => map[s] != null)
  return {
    color: ['#185fa5'],
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 16, top: 24, bottom: 30 },
    xAxis: { type: 'category', data: names.map(s => STAGE[s]), axisLabel: { fontSize: 11, interval: 0, rotate: 18 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', barWidth: 22, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: names.map(s => map[s]) }]
  }
})

// 采购周期趋势（折线 + 定标数柱）
const cycleOption = computed(() => ({
  color: ['#185fa5', '#67c23a'],
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
  grid: { left: 40, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: cycleTrend.value.map(r => r.month) },
  yAxis: [
    { type: 'value', name: '周期(天)' },
    { type: 'value', name: '定标(单)', splitLine: { show: false } }
  ],
  series: [
    { name: '平均周期', type: 'line', smooth: true, symbolSize: 7, data: cycleTrend.value.map(r => Number(r.avgCycleDays || 0)) },
    { name: '定标数', type: 'bar', yAxisIndex: 1, barWidth: 20, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: cycleTrend.value.map(r => Number(r.awardCount || 0)) }
  ]
}))

// 预算 vs 定标偏差
const deviationOption = computed(() => ({
  color: ['#8aadd2', '#185fa5', '#f56c6c'],
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
  grid: { left: 64, right: 24, top: 24, bottom: 40 },
  xAxis: { type: 'category', data: deviation.value.map(r => (r.name || '').slice(0, 8)), axisLabel: { fontSize: 11, interval: 0, rotate: 15 } },
  yAxis: [
    { type: 'value', name: '万元', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    { type: 'value', name: '偏差%', axisLabel: { formatter: '{value}%' }, splitLine: { show: false } }
  ],
  series: [
    { name: '预算', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: deviation.value.map(r => Number(r.budget || 0)) },
    { name: '定标', type: 'bar', barWidth: 16, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: deviation.value.map(r => Number(r.award || 0)) },
    { name: '偏差率', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 7, itemStyle: { color: '#f56c6c' }, data: deviation.value.map(r => Number(r.deviationPct || 0)) }
  ]
}))

onMounted(load)
</script>

<style scoped>
.proc-cockpit { max-width: 1560px; }
.hero { background: linear-gradient(120deg, #0c2d4e 0%, #185fa5 78%, #2a74bd 100%); border-radius: 10px; padding: 18px 20px 20px; margin-bottom: 12px; color: #fff; }
.hero-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 14px; }
.hero-title { font-size: 19px; font-weight: 700; letter-spacing: 2px; }
.hero-sub { font-size: 12px; opacity: .75; }
.hero-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.hero-card { background: rgba(255,255,255,.10); border: 1px solid rgba(255,255,255,.16); border-radius: 8px; padding: 14px 16px; transition: background .15s; }
.hero-card:hover { background: rgba(255,255,255,.18); }
.hero-card.warn { background: rgba(245,108,108,.16); border-color: rgba(255,180,180,.35); }
.hc-label { font-size: 12px; opacity: .82; margin-bottom: 8px; }
.hc-value { font-size: 22px; font-weight: 700; color: #fff; }
.hc-value .unit { font-size: 12px; font-weight: 400; opacity: .8; }
.hc-value.dev-up { color: #ff9d9d; }
.hc-value.dev-down { color: #7ce38b; }
.hc-foot { font-size: 11.5px; opacity: .7; margin-top: 6px; }
.section-row { margin-bottom: 12px; }
.section-card { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: #eef2f7; color: #6b7280; font-weight: 700; font-size: 12px; }
.rank.top { background: #185fa5; color: #fff; }
.risk-list { display: flex; flex-direction: column; gap: 8px; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #eef0f3; border-radius: 8px; cursor: pointer; transition: background .15s; }
.risk-item:hover { background: #f7fafc; }
.risk-src { font-size: 12px; color: #185fa5; font-weight: 600; min-width: 48px; }
.risk-title { font-size: 13px; color: #1f2937; font-weight: 600; white-space: nowrap; }
.risk-desc { flex: 1; font-size: 12.5px; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.risk-arrow { color: #909399; }
.num { font-variant-numeric: tabular-nums; }
</style>
