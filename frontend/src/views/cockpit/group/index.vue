<template>
  <div class="page-container cockpit-group">
    <!-- 顶部六卡 -->
    <div class="hero" v-loading="loading">
      <div class="hero-head">
        <div class="hero-title">集团驾驶舱</div>
        <div class="hero-sub">{{ today }} · 点击卡片数字可下钻明细</div>
      </div>
      <div class="hero-cards">
        <div class="hero-card" @click="openDrill('SIGN_AMOUNT')">
          <div class="hc-label">签约额（万元）</div>
          <div class="hc-value num">{{ wan(cards.signedAmount) }}</div>
          <div class="hc-foot">Σ 生效/网签合同</div>
        </div>
        <div class="hero-card" @click="openDrill('RECEIPT_AMOUNT')">
          <div class="hc-label">回款额（万元）</div>
          <div class="hc-value num">{{ wan(cards.receiptAmount) }}</div>
          <div class="hc-foot">回款率 {{ pct(cards.receiptRate) }}</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">供货货值（万元）</div>
          <div class="hc-value num">{{ wan(cards.supplyValue) }}</div>
          <div class="hc-foot">Σ inv_value</div>
        </div>
        <div class="hero-card">
          <div class="hc-label">总货值（万元）</div>
          <div class="hc-value num">{{ wan(cards.totalValue) }}</div>
          <div class="hc-foot">拿地锁定基线</div>
        </div>
        <div class="hero-card" @click="openDrill('DYNAMIC_COST')">
          <div class="hc-label">动态成本（万元）</div>
          <div class="hc-value num">{{ wan(cards.dynamicCost) }}</div>
          <div class="hc-foot">最新月度快照</div>
        </div>
        <div class="hero-card" @click="openDrill('DYNAMIC_PROFIT')">
          <div class="hc-label">动态利润（万元）</div>
          <div class="hc-value num profit">{{ wan(cards.dynamicProfit) }}</div>
          <div class="hc-foot">货值-成本-三费-税金</div>
        </div>
      </div>
    </div>

    <!-- 全国项目地图（卡片矩阵，红黄绿） -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">全国项目地图（综合红黄绿，点击下钻项目驾驶舱）</span>
          <div class="legend">
            <span class="dot green" /> 绿正常 <span class="dot yellow" /> 黄关注 <span class="dot red" /> 红预警
          </div>
        </div>
      </template>
      <div v-if="projectMap.length" class="pm-grid">
        <div
          v-for="p in projectMap"
          :key="p.projectId"
          class="pm-card"
          :class="lightClass(p.light)"
          @click="goProject(p.projectId)"
        >
          <div class="pm-top">
            <span class="pm-name">{{ p.projectName }}</span>
            <span class="pm-dot" :class="lightClass(p.light)" />
          </div>
          <div class="pm-org">{{ p.orgName }} · {{ p.city || '-' }}</div>
          <div class="pm-metrics">
            <div><span>去化率</span><b>{{ pct(p.turnoverRate) }}</b></div>
            <div><span>成本偏差</span><b :class="devClass(p.costDeviation)">{{ signed(pct(p.costDeviationRate)) }}</b></div>
            <div><span>动态利润</span><b>{{ wan(p.dynamicProfit) }}万</b></div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无项目数据" :image-size="80" />
    </el-card>

    <el-row :gutter="12" class="section-row">
      <!-- 区域排名 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">区域排名矩阵（按动态利润）</span></template>
          <el-table v-loading="loading" :data="regionRanking" stripe size="small" empty-text="暂无数据">
            <el-table-column label="排名" width="60" align="center">
              <template #default="{ row }">
                <span class="rank" :class="row.rank <= 3 ? 'top' : ''">{{ row.rank }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="orgName" label="区域" min-width="120" show-overflow-tooltip />
            <el-table-column prop="projectCount" label="项目数" width="70" align="center" />
            <el-table-column label="签约额(万)" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ wan(row.signedAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="回款(万)" width="100" align="right">
              <template #default="{ row }"><span class="num">{{ wan(row.receiptAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="动态利润(万)" width="110" align="right">
              <template #default="{ row }"><span class="num" style="color:#67c23a;font-weight:600">{{ wan(row.dynamicProfit) }}</span></template>
            </el-table-column>
            <el-table-column label="成本偏差(万)" width="110" align="right">
              <template #default="{ row }"><span class="num" :class="devClass(row.costDeviation)">{{ wan(row.costDeviation) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <!-- 投资兑现度 -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">投资兑现度（动态利润 vs 测算基线）</span></template>
          <ChartBox v-if="investmentRealization.length" :option="investOption" height="320px" />
          <el-empty v-else description="暂无测算基线数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 风险预警聚合 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">风险预警聚合（{{ riskAlerts.length }}）</span>
          <div class="legend">
            <el-tag size="small" type="danger" effect="plain">高</el-tag>
            <el-tag size="small" type="warning" effect="plain">中</el-tag>
          </div>
        </div>
      </template>
      <div v-if="riskAlerts.length" class="risk-list">
        <div v-for="(a, i) in riskAlerts" :key="i" class="risk-item" @click="goLink(a.link)">
          <el-tag size="small" :type="a.level === 'HIGH' ? 'danger' : 'warning'" effect="light">{{ a.level === 'HIGH' ? '高' : '中' }}</el-tag>
          <span class="risk-src">{{ sourceLabel(a.source) }}</span>
          <span class="risk-title">{{ a.title }}</span>
          <span class="risk-desc">{{ a.desc }}</span>
          <el-icon class="risk-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
      <el-empty v-else description="暂无风险预警" :image-size="80" />
    </el-card>

    <!-- 下钻弹窗 -->
    <DrillDialog
      v-model="drill.visible"
      :metric-code="drill.metricCode"
      :title="drill.title"
      :params="drill.params"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowRight } from '@element-plus/icons-vue'
import { cockpitGroup } from '../../../api/cockpit'
import { wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'
import DrillDialog from '../components/DrillDialog.vue'

const router = useRouter()
const today = dayjs().format('YYYY年MM月DD日')

const loading = ref(false)
const cards = ref({})
const projectMap = ref([])
const regionRanking = ref([])
const investmentRealization = ref([])
const riskAlerts = ref([])

const drill = ref({ visible: false, metricCode: '', title: '', params: {} })

const pct = v => (v == null || v === '' ? '-' : Number(v).toFixed(1) + '%')
const signed = v => (v === '-' ? '-' : (Number(v) > 0 ? '+' : '') + v)
const devClass = v => (v == null ? '' : Number(v) > 0 ? 'dev-up' : Number(v) < 0 ? 'dev-down' : '')

const lightClass = l => (l ? 'light-' + l.toLowerCase() : 'light-na')

const sourceLabel = s => ({ PLAN: '计划', COST: '成本', PROC: '招采', EXPENSE: '费用' }[s] || s || '-')

const load = async () => {
  loading.value = true
  try {
    const data = await cockpitGroup()
    cards.value = data.cards || {}
    projectMap.value = data.projectMap || []
    regionRanking.value = data.regionRanking || []
    investmentRealization.value = data.investmentRealization || []
    riskAlerts.value = data.riskAlerts || []
  } finally {
    loading.value = false
  }
}

const openDrill = code => {
  const titles = { SIGN_AMOUNT: '签约额', RECEIPT_AMOUNT: '回款额', DYNAMIC_COST: '动态成本', DYNAMIC_PROFIT: '动态利润' }
  drill.value = { visible: true, metricCode: code, title: titles[code] || '明细', params: {} }
}

const goProject = projectId => router.push({ path: '/cockpit/project', query: { projectId } })
const goLink = link => { if (link) router.push(link) }

// 投资兑现度：动态利润 vs 测算基线
const investOption = computed(() => {
  const rows = investmentRealization.value
  const names = rows.map(r => r.projectName)
  return {
    color: ['#67c23a', '#8aadd2'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 70, right: 20, top: 30, bottom: 44 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'], axisLabel: { fontSize: 11, interval: 0, rotate: names.length > 5 ? 20 : 0 } },
    yAxis: [
      { type: 'value', name: '万元', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
      { type: 'value', name: '兑现%', max: 200, axisLabel: { formatter: '{value}%' }, splitLine: { show: false } }
    ],
    series: [
      { name: '动态利润', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.dynamicProfit || 0)) },
      { name: '测算基线', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: rows.map(r => Number(r.baselineProfit || 0)) },
      { name: '兑现度', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 7, data: rows.map(r => Number(r.realizationPct || 0)) }
    ]
  }
})

onMounted(load)
</script>

<style scoped>
.cockpit-group { max-width: 1560px; }
.hero { background: linear-gradient(120deg, #0c2d4e 0%, #185fa5 78%, #2a74bd 100%); border-radius: 10px; padding: 18px 20px 20px; margin-bottom: 12px; color: #fff; }
.hero-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 14px; }
.hero-title { font-size: 19px; font-weight: 700; letter-spacing: 2px; }
.hero-sub { font-size: 12px; opacity: .75; }
.hero-cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.hero-card { background: rgba(255,255,255,.10); border: 1px solid rgba(255,255,255,.16); border-radius: 8px; padding: 14px 16px; cursor: pointer; transition: background .15s; }
.hero-card:hover { background: rgba(255,255,255,.18); }
.hc-label { font-size: 12px; opacity: .8; margin-bottom: 8px; white-space: nowrap; }
.hc-value { font-size: 22px; font-weight: 700; color: #fff; }
.hc-value.profit { color: #7ce38b; }
.hc-foot { font-size: 11.5px; opacity: .7; margin-top: 6px; }
.section-card { margin-bottom: 12px; }
.section-row { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 6px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.legend { font-size: 12px; color: #6b7280; display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot.green { background: #67c23a; }
.dot.yellow { background: #e6a23c; }
.dot.red { background: #f56c6c; }
/* 项目卡片矩阵 */
.pm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; }
.pm-card { border: 1px solid #e5e7eb; border-left: 4px solid #c0c4cc; border-radius: 8px; padding: 12px; cursor: pointer; transition: box-shadow .15s, transform .15s; }
.pm-card:hover { box-shadow: 0 4px 14px rgba(12,45,78,.12); transform: translateY(-1px); }
.pm-card.light-green { border-left-color: #67c23a; }
.pm-card.light-yellow { border-left-color: #e6a23c; }
.pm-card.light-red { border-left-color: #f56c6c; }
.pm-card.light-na { border-left-color: #c0c4cc; }
.pm-top { display: flex; justify-content: space-between; align-items: center; }
.pm-name { font-weight: 700; color: #1f2937; font-size: 14px; }
.pm-dot { width: 12px; height: 12px; border-radius: 50%; }
.pm-dot.light-green { background: #67c23a; box-shadow: 0 0 6px rgba(103,194,58,.6); }
.pm-dot.light-yellow { background: #e6a23c; box-shadow: 0 0 6px rgba(230,162,60,.6); }
.pm-dot.light-red { background: #f56c6c; box-shadow: 0 0 6px rgba(245,108,108,.7); }
.pm-dot.light-na { background: #c0c4cc; }
.pm-org { font-size: 12px; color: #6b7280; margin: 4px 0 10px; }
.pm-metrics { display: flex; flex-direction: column; gap: 6px; }
.pm-metrics div { display: flex; justify-content: space-between; font-size: 12.5px; color: #4b5563; }
.pm-metrics b { color: #1f2937; font-weight: 600; }
/* 区域排名 */
.rank { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: #eef2f7; color: #6b7280; font-weight: 700; font-size: 12px; }
.rank.top { background: #185fa5; color: #fff; }
/* 风险预警 */
.risk-list { display: flex; flex-direction: column; gap: 8px; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #eef0f3; border-radius: 8px; cursor: pointer; transition: background .15s; }
.risk-item:hover { background: #f7fafc; }
.risk-src { font-size: 12px; color: #185fa5; font-weight: 600; min-width: 40px; }
.risk-title { font-size: 13px; color: #1f2937; font-weight: 600; white-space: nowrap; }
.risk-desc { flex: 1; font-size: 12.5px; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.risk-arrow { color: #909399; }
.num { font-variant-numeric: tabular-nums; }
.dev-up { color: #f56c6c; font-weight: 600; }
.dev-down { color: #67c23a; font-weight: 600; }
</style>
