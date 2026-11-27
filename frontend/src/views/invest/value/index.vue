<template>
  <div class="page-container">
    <!-- 汇总指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">总可售面积（㎡）</div>
          <div class="value num">{{ money(totalArea) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">总货值（万元）</div>
          <div class="value num">{{ wan(totalValue) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">已售货值（万元）</div>
          <div class="value num" style="color:#67c23a">{{ wan(totalSold) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">综合均价（元/㎡）</div>
          <div class="value num">{{ totalArea > 0 ? money(totalValue / totalArea) : '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">业态货值构成</span></template>
          <ChartBox :option="pieOption" height="280px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">项目货值去化（已售 / 未售）</span></template>
          <ChartBox :option="barOption" height="280px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 项目供销存汇总 -->
    <el-card shadow="never" class="table-card">
      <template #header><span class="card-title">项目供销存汇总（万元）</span></template>
      <el-table :data="stockRows" size="small" show-summary :summary-method="stockSummary" border>
        <el-table-column label="项目" min-width="120">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="期初存货" align="right">
          <template #default="{ row }"><span class="num">{{ wan(row.beginStock) }}</span></template>
        </el-table-column>
        <el-table-column label="新供货" align="right">
          <template #default="{ row }"><span class="num">{{ wan(row.supply) }}</span></template>
        </el-table-column>
        <el-table-column label="已售（签约）" align="right">
          <template #default="{ row }"><span class="num">{{ wan(row.sold) }}</span></template>
        </el-table-column>
        <el-table-column label="退房回流" align="right">
          <template #default="{ row }"><span class="num">{{ wan(row.refund) }}</span></template>
        </el-table-column>
        <el-table-column label="期末存货" align="right">
          <template #default="{ row }"><span class="num">{{ wan(row.endStock) }}</span></template>
        </el-table-column>
        <el-table-column label="去化率" align="right" width="100">
          <template #default="{ row }">
            <span class="num">{{ row.endStock > 0 ? ((row.sold / (Number(row.sold) + Number(row.endStock))) * 100).toFixed(2) : '0.00' }}%</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 180px" @change="search">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
          <el-select v-model="query.productCategory" placeholder="业态" clearable style="width: 150px" @change="search">
            <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增货值台账</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe show-summary :summary-method="listSummary">
        <el-table-column label="项目" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="productCategory" label="业态" width="110" show-overflow-tooltip />
        <el-table-column label="可售面积(㎡)" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.area) }}</span></template>
        </el-table-column>
        <el-table-column label="价格版本" width="95">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dictLabel('price_version', row.priceVersion) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单价(元/㎡)" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.price) }}</span></template>
        </el-table-column>
        <el-table-column label="货值(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.totalValue) }}</span></template>
        </el-table-column>
        <el-table-column label="供货时间" width="110">
          <template #default="{ row }">{{ fmtDate(row.supplyDate) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="allRows.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="onPage"
        @current-change="onPage"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formDlg" :title="form.id ? '编辑货值台账' : '新增货值台账'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="业态" required>
          <el-select v-model="form.productCategory" placeholder="请选择业态" style="width: 100%" allow-create filterable>
            <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="可售面积" required>
              <el-input-number v-model="form.area" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格版本" required>
              <el-select v-model="form.priceVersion" style="width: 100%">
                <el-option label="预测价" value="FORECAST" />
                <el-option label="备案价" value="REGISTERED" />
                <el-option label="成交价" value="DEAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价(元)" required>
              <el-input-number v-model="form.price" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供货时间">
              <el-date-picker v-model="form.supplyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="货值预览">
          <span class="num preview-value">{{ money(previewValue) }} 元</span>
          <span class="form-tip">货值 = 面积 × 单价，保存后由系统自动计算</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { projectOptions, valueCreate, valueDelete, valuePage, valueStock, valueUpdate } from '../../../api/invest'
import { dictLabel, fmtDate, money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const BLUE = ['#185fa5', '#2a74bd', '#5687ba', '#8aadd2', '#c3d6e8', '#124a80']

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || id || '-'

const loading = ref(false)
const allRows = ref([])
const stockRows = ref([])
const query = reactive({ current: 1, size: 20, projectId: null, productCategory: '' })

const list = computed(() =>
  allRows.value.slice((query.current - 1) * query.size, query.current * query.size)
)
const categories = computed(() => [...new Set(allRows.value.map(r => r.productCategory).filter(Boolean))])

const totalArea = computed(() => allRows.value.reduce((s, r) => s + Number(r.area || 0), 0))
const totalValue = computed(() => allRows.value.reduce((s, r) => s + Number(r.totalValue || 0), 0))
const totalSold = computed(() => stockRows.value.reduce((s, r) => s + Number(r.sold || 0), 0))

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await valuePage({
      current: 1, size: 500,
      projectId: query.projectId || undefined,
      productCategory: query.productCategory || undefined
    })
    allRows.value = data.records || []
    loadStocks()
  } finally {
    loading.value = false
  }
}

const loadStocks = async () => {
  const ids = query.projectId
    ? [query.projectId]
    : [...new Set(allRows.value.map(r => r.projectId).filter(Boolean))]
  if (!ids.length) {
    stockRows.value = []
    return
  }
  stockRows.value = await Promise.all(ids.map(id => valueStock(id)))
}

// ============ 合计行 ============
const stockSummary = ({ columns, data }) => {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (col.label === '去化率') {
      const sold = data.reduce((s, r) => s + Number(r.sold || 0), 0)
      const stock = data.reduce((s, r) => s + Number(r.endStock || 0), 0)
      return stock + sold > 0 ? ((sold / (sold + stock)) * 100).toFixed(2) + '%' : '-'
    }
    const key = ['projectId', 'beginStock', 'supply', 'sold', 'refund', 'endStock'][i]
    const sum = data.reduce((s, r) => s + Number(r[key] || 0), 0)
    return key === 'projectId' ? '-' : wan(sum)
  })
}

const listSummary = ({ columns }) => {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    if (col.label === '可售面积(㎡)') return money(totalArea.value)
    if (col.label === '货值(元)') return money(totalValue.value)
    if (col.label === '单价(元/㎡)') return totalArea.value > 0 ? money(totalValue.value / totalArea.value) : '-'
    return ''
  })
}

// ============ 图表 ============
const pieOption = computed(() => {
  const byCat = {}
  allRows.value.forEach(r => {
    const c = r.productCategory || '其他'
    byCat[c] = (byCat[c] || 0) + Number(r.totalValue || 0)
  })
  const data = Object.entries(byCat).map(([name, value]) => ({ name, value }))
  return {
    color: BLUE,
    tooltip: { trigger: 'item', formatter: p => `${p.name}<br/>货值：${wan(p.value)}万元（${p.percent}%）` },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%', fontSize: 12 },
      data
    }]
  }
})

const barOption = computed(() => {
  const names = stockRows.value.map(r => projectName(r.projectId))
  const sold = stockRows.value.map(r => Number(r.sold || 0))
  const unsold = stockRows.value.map(r => Number(r.endStock || 0))
  return {
    color: ['#67c23a', BLUE[0]],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 90, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 12 } },
    series: [
      { name: '已售货值', type: 'bar', stack: 'total', barWidth: 22, data: sold, itemStyle: { borderRadius: [4, 0, 0, 4] } },
      { name: '期末存货', type: 'bar', stack: 'total', barWidth: 22, data: unsold, itemStyle: { borderRadius: [0, 4, 4, 0] } }
    ]
  }
})

// ============ 新增/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, projectId: null, productCategory: '', area: null,
  priceVersion: 'FORECAST', price: null, supplyDate: null, remark: ''
})
const form = reactive(emptyForm())

const previewValue = computed(() =>
  (form.area != null && form.price != null) ? form.area * form.price : null
)

const openCreate = () => {
  Object.assign(form, emptyForm())
  formDlg.value = true
}
const openEdit = row => {
  Object.assign(form, emptyForm(), row)
  formDlg.value = true
}
const save = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (!form.productCategory) return ElMessage.warning('请选择业态')
  if (form.area == null || form.price == null) return ElMessage.warning('请填写面积与单价')
  saving.value = true
  try {
    if (form.id) {
      await valueUpdate(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await valueCreate({ ...form })
      ElMessage.success('已新增')
    }
    formDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  try {
    await ElMessageBox.confirm(
      `确定删除「${projectName(row.projectId)} - ${row.productCategory}」货值记录吗？`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await valueDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  projects.value = await projectOptions()
  load()
})
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.preview-value { font-size: 16px; font-weight: 700; color: #185fa5; margin-right: 10px; }
.form-tip { font-size: 12px; color: #9ca3af; }
</style>
