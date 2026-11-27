<template>
  <div class="page-container">
    <!-- 适配器统计卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">总调用量</div>
          <div class="value num">{{ totalCalls }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">成功调用</div>
          <div class="value num" style="color:#67c23a">{{ totalSuccess }}</div>
          <div class="sub">成功率 {{ successRate }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">失败调用</div>
          <div class="value num" style="color:#f56c6c">{{ totalFail }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">平均耗时</div>
          <div class="value num">{{ avgCost }} ms</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 适配器维度统计图 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">各适配器成功 / 失败调用数</span></template>
          <ChartBox :option="barOption" height="250px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">各适配器平均耗时（ms）</span></template>
          <ChartBox :option="costOption" height="250px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="adapterCode" placeholder="适配器" clearable style="width: 200px" @change="search">
            <el-option v-for="s in stats" :key="s.adapterCode" :value="s.adapterCode" :label="s.adapterCode" />
          </el-select>
          <el-select v-model="success" placeholder="调用结果" clearable style="width: 130px" @change="search">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="createdAt" label="调用时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="adapterCode" label="适配器" width="150" show-overflow-tooltip />
        <el-table-column prop="apiCode" label="API编码" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.apiCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'IN' ? 'success' : 'warning'" effect="plain">
              {{ row.direction === 'IN' ? '入站' : '出站' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestDigest" label="请求摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestDigest || '-' }}</template>
        </el-table-column>
        <el-table-column prop="responseDigest" label="响应摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.responseDigest || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ slow: row.costMs > 1000 }">{{ row.costMs }} ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="失败原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { logPage, logStats } from '../../../api/int'
import { fmtTime } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref([])
const adapterCode = ref('')
const success = ref(null)
const query = reactive({ current: 1, size: 20 })

const totalSuccess = computed(() => stats.value.reduce((s, r) => s + Number(r.successCount || 0), 0))
const totalFail = computed(() => stats.value.reduce((s, r) => s + Number(r.failCount || 0), 0))
const totalCalls = computed(() => totalSuccess.value + totalFail.value)
const successRate = computed(() => {
  if (!totalCalls.value) return '0.00'
  return ((totalSuccess.value / totalCalls.value) * 100).toFixed(2)
})
const avgCost = computed(() => {
  if (!stats.value.length) return 0
  const items = stats.value.filter(r => r.avgCost != null)
  if (!items.length) return 0
  return Math.round(items.reduce((s, r) => s + Number(r.avgCost || 0), 0) / items.length)
})

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await logPage({
      ...query,
      adapterCode: adapterCode.value || undefined,
      success: success.value ?? undefined
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  stats.value = await logStats()
}

const loadAll = () => {
  loadStats()
  load()
}

const barOption = computed(() => {
  const names = stats.value.map(s => s.adapterCode)
  return {
    color: ['#67c23a', '#f56c6c'],
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 50, right: 20, top: 36, bottom: 46 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'], axisLabel: { fontSize: 11, interval: 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '成功', type: 'bar', stack: 'total', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: stats.value.map(s => Number(s.successCount || 0)) },
      { name: '失败', type: 'bar', stack: 'total', barWidth: 26, data: stats.value.map(s => Number(s.failCount || 0)) }
    ]
  }
})

const costOption = computed(() => {
  const names = stats.value.map(s => s.adapterCode)
  return {
    color: ['#185fa5'],
    tooltip: { trigger: 'axis', valueFormatter: v => v + ' ms' },
    grid: { left: 60, right: 40, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'], axisLabel: { fontSize: 11, interval: 0 } },
    yAxis: { type: 'value' },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        barWidth: 26,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#185fa5' },
        data: stats.value.map(s => Number(s.avgCost || 0)),
        label: { show: true, position: 'top', formatter: p => p.value + 'ms' }
      }
    ]
  }
})

onMounted(loadAll)
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; }
.stat-card .sub { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.slow { color: #f56c6c; font-weight: 600; }
</style>
