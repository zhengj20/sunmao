<template>
  <div class="page-container">
    <!-- 汇总指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">融资总额（万元）</div>
          <div class="value num">{{ wan(totalAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">已用额度（万元）</div>
          <div class="value num" style="color:#e6a23c">{{ wan(totalUsed) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">剩余额度（万元）</div>
          <div class="value num" style="color:#67c23a">{{ wan(totalAmount - totalUsed) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">融资笔数 / 加权利率</div>
          <div class="value num">{{ allRows.length }} 笔 / {{ weightedRate }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 170px" @change="search">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
          <el-select v-model="query.financeType" placeholder="融资类型" clearable style="width: 130px" @change="search">
            <el-option label="开发贷" value="开发贷" />
            <el-option label="信托" value="信托" />
            <el-option label="债券" value="债券" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option label="正常" value="NORMAL" />
            <el-option label="已还清" value="REPAID" />
            <el-option label="逾期" value="OVERDUE" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <div>
          <el-button type="warning" :icon="Bell" @click="openExpiring">到期提醒</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增融资</el-button>
        </div>
      </div>
    </el-card>

    <!-- 额度使用图 -->
    <el-card shadow="never" class="stat-row">
      <template #header><span class="card-title">融资机构额度使用情况（万元）</span></template>
      <ChartBox :option="chartOption" height="250px" />
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe show-summary :summary-method="listSummary">
        <el-table-column label="项目" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="financeType" label="融资类型" width="90" />
        <el-table-column prop="bankName" label="融资机构" min-width="150" show-overflow-tooltip />
        <el-table-column label="额度(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="已用额度(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.usedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="使用率" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.amount > 0 ? Math.min((Number(row.usedAmount || 0) / Number(row.amount)) * 100, 100) : 0"
              :stroke-width="12"
              :color="progColor"
            />
          </template>
        </el-table-column>
        <el-table-column label="利率" width="80" align="right">
          <template #default="{ row }">{{ row.rate != null ? row.rate + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="放款日" width="105">
          <template #default="{ row }">{{ fmtDate(row.startDate) }}</template>
        </el-table-column>
        <el-table-column label="到期日" width="105">
          <template #default="{ row }">
            <span :class="{ 'due-soon': isDueSoon(row.dueDate) }">{{ fmtDate(row.dueDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusColor(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="110" show-overflow-tooltip>
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
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formDlg" :title="form.id ? '编辑融资台账' : '新增融资台账'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="融资类型" required>
              <el-select v-model="form.financeType" style="width: 100%">
                <el-option label="开发贷" value="开发贷" />
                <el-option label="信托" value="信托" />
                <el-option label="债券" value="债券" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="融资机构" required>
              <el-input v-model="form.bankName" placeholder="如：工商银行杭州分行" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="额度(元)" required>
              <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="已用额度">
              <el-input-number v-model="form.usedAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="利率(%)">
              <el-input-number v-model="form.rate" :controls="false" :precision="3" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="正常" value="NORMAL" />
                <el-option label="已还清" value="REPAID" />
                <el-option label="逾期" value="OVERDUE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="放款日">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日">
              <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 到期提醒 -->
    <el-dialog v-model="expiringDlg" title="融资到期提醒（90天内）" width="720px">
      <el-table :data="expiringList" size="small" border>
        <el-table-column label="项目" min-width="100">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="bankName" label="融资机构" min-width="150" show-overflow-tooltip />
        <el-table-column label="额度(元)" align="right" width="140">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="到期日" width="105">
          <template #default="{ row }">
            <span class="due-soon">{{ fmtDate(row.dueDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="剩余天数" width="90" align="center">
          <template #default="{ row }">{{ daysLeft(row.dueDate) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="!expiringList.length" style="padding:10px;color:#9ca3af">暂无90天内到期融资</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Plus, Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  expiringFinances, financeCreate, financeDelete, financePage, financeUpdate, projectOptions
} from '../../../api/fund'
import { fmtDate, money, wan } from '../../../utils/format'
import ChartBox from '../../../components/ChartBox.vue'

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || (id ? `项目${id}` : '-')

const statusLabel = s => ({ NORMAL: '正常', REPAID: '已还清', OVERDUE: '逾期' }[s] || s)
const statusColor = s => ({ NORMAL: 'success', REPAID: 'info', OVERDUE: 'danger' }[s] || 'info')

const loading = ref(false)
const allRows = ref([])
const query = reactive({ current: 1, size: 20, projectId: null, financeType: '', status: '' })

const list = computed(() => allRows.value.slice((query.current - 1) * query.size, query.current * query.size))

const totalAmount = computed(() => allRows.value.reduce((s, r) => s + Number(r.amount || 0), 0))
const totalUsed = computed(() => allRows.value.reduce((s, r) => s + Number(r.usedAmount || 0), 0))
const weightedRate = computed(() => {
  const amt = totalAmount.value
  if (!amt) return '0.00'
  const w = allRows.value.reduce((s, r) => s + Number(r.rate || 0) * Number(r.amount || 0), 0)
  return (w / amt).toFixed(2)
})

const progColor = [
  { color: '#67c23a', percentage: 50 },
  { color: '#e6a23c', percentage: 80 },
  { color: '#f56c6c', percentage: 100 }
]

const daysLeft = d => (d ? dayjs(d).diff(dayjs(), 'day') : '-')
const isDueSoon = d => {
  const left = daysLeft(d)
  return typeof left === 'number' && left >= 0 && left <= 90
}

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await financePage({
      current: 1, size: 500,
      projectId: query.projectId || undefined,
      financeType: query.financeType || undefined,
      status: query.status || undefined
    })
    allRows.value = data.records || []
  } finally {
    loading.value = false
  }
}

const listSummary = ({ columns }) => {
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    const key = { 额度: 'amount', 已用额度: 'usedAmount' }[col.label?.replace('(元)', '')]
    if (!key) return ''
    return money(allRows.value.reduce((s, r) => s + Number(r[key] || 0), 0))
  })
}

const chartOption = computed(() => {
  const names = allRows.value.map(r => r.bankName || '-')
  return {
    color: ['#185fa5', '#8aadd2'],
    tooltip: { trigger: 'axis', valueFormatter: v => wan(v) + ' 万元' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    grid: { left: 80, right: 24, top: 36, bottom: 46 },
    xAxis: { type: 'category', data: names.length ? names : ['暂无数据'], axisLabel: { interval: 0, fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { formatter: v => (v / 10000).toFixed(0) + '万' } },
    series: [
      { name: '融资额度', type: 'bar', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: allRows.value.map(r => Number(r.amount || 0)) },
      { name: '已用额度', type: 'bar', barWidth: 26, itemStyle: { borderRadius: [4, 4, 0, 0] }, data: allRows.value.map(r => Number(r.usedAmount || 0)) }
    ]
  }
})

// ============ 新增/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, projectId: null, financeType: '开发贷', bankName: '', amount: null,
  usedAmount: 0, rate: null, startDate: null, dueDate: null, status: 'NORMAL', remark: ''
})
const form = reactive(emptyForm())

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
  if (!form.bankName) return ElMessage.warning('请填写融资机构')
  if (form.amount == null) return ElMessage.warning('请填写融资额度')
  saving.value = true
  try {
    if (form.id) {
      await financeUpdate(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await financeCreate({ ...form })
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
    await ElMessageBox.confirm(`确定删除「${row.bankName}」融资记录吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await financeDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 到期提醒 ============
const expiringDlg = ref(false)
const expiringList = ref([])
const openExpiring = async () => {
  expiringList.value = await expiringFinances(90)
  expiringDlg.value = true
}

onMounted(async () => {
  projects.value = await projectOptions()
  load()
})
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.due-soon { color: #e6a23c; font-weight: 600; }
</style>
