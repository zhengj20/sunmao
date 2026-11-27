<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.deptId" placeholder="部门" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-input v-model="query.year" placeholder="年度" type="number" style="width: 100px" @keyup.enter="onSearch" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(BUDGET_STATUS)" :key="v" :label="BUDGET_STATUS[v][0]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">预算编制</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">预算总额（元）</div><div class="value">{{ money(stat.amount) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已占用（元）</div><div class="value">{{ money(stat.occupyAmount) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已核销（元）</div><div class="value">{{ money(stat.writeoffAmount) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">余额（元）</div><div class="value">{{ money(stat.balance) }}</div></el-card></el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || ('部门#' + row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="费用科目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.accountName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="budgetYear" label="年度" width="80" align="center" />
        <el-table-column label="月份" width="80" align="center">
          <template #default="{ row }">{{ row.month ? row.month + '月' : '全年' }}</template>
        </el-table-column>
        <el-table-column label="预算（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="占用（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.occupyAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="核销（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.writeoffAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :style="Number(row.balance) < 0 ? 'color:#c45656;font-weight:600' : ''">{{ money(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">调整</el-button>
            <el-button v-if="row.status === 'ACTIVE' && !row.occupyAmount && !row.writeoffAmount" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 预算编制 / 调整 -->
    <el-dialog v-model="formVisible" :title="form.id ? '调整预算' : '预算编制（部门×科目×月）'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="部门" required>
          <el-select v-model="form.deptId" filterable style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用科目" required>
          <el-select v-model="form.accountId" filterable style="width: 100%" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="预算年度" required>
              <el-input-number v-model="form.budgetYear" :controls="false" :min="2020" :max="2099" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="月份">
              <el-select v-model="form.month" placeholder="全年" clearable style="width: 100%">
                <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="预算金额" required>
          <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.id" label="说明">
          <div class="hint">调整预算后，余额 = 新预算 − 已占用 − 已核销（占用/核销保持不变）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchOrgOptions, fetchAccountOptions,
  budgetPage, budgetLedger, budgetCreate, budgetUpdate, budgetDelete,
  BUDGET_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, BUDGET_STATUS)
const orgs = ref([])
const accounts = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ deptId: null, year: 2026, status: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await budgetPage({
      current: query.current, size: query.size,
      deptId: query.deptId || undefined, year: query.year || undefined, status: query.status || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.deptId = null; query.status = null; onSearch() }

const loadStat = async () => {
  const list = (await budgetLedger({ deptId: query.deptId || undefined, year: query.year || undefined }).catch(() => [])) || []
  const sum = k => list.reduce((s, r) => s + (Number(r[k]) || 0), 0)
  stat.value = { amount: sum('amount'), occupyAmount: sum('occupyAmount'), writeoffAmount: sum('writeoffAmount'), balance: sum('balance') }
}
const stat = ref({ amount: 0, occupyAmount: 0, writeoffAmount: 0, balance: 0 })

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, deptId: null, accountId: null, accountName: null, budgetYear: new Date().getFullYear(), month: null, amount: null })
const onAccountChange = id => { form.accountName = accounts.value.find(a => a.id === id)?.name || null }

const openForm = row => {
  if (row) {
    Object.assign(form, { id: row.id, deptId: row.deptId, accountId: row.accountId, accountName: row.accountName, budgetYear: row.budgetYear, month: row.month, amount: row.amount })
  } else {
    Object.assign(form, { id: null, deptId: query.deptId, accountId: null, accountName: null, budgetYear: query.year || new Date().getFullYear(), month: null, amount: null })
  }
  formVisible.value = true
}
const onSave = async () => {
  if (!form.deptId) return ElMessage.warning('请选择部门')
  if (!form.accountId) return ElMessage.warning('请选择费用科目')
  if (!form.budgetYear) return ElMessage.warning('请填写预算年度')
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写预算金额')
  saving.value = true
  try {
    if (form.id) {
      await budgetUpdate({ id: form.id, amount: form.amount })
      ElMessage.success('预算已调整，余额已重算')
    } else {
      await budgetCreate({
        deptId: form.deptId, accountId: form.accountId, accountName: form.accountName,
        budgetYear: form.budgetYear, month: form.month || undefined, amount: form.amount
      })
      ElMessage.success('预算已编制')
    }
    formVisible.value = false
    loadList(); loadStat()
  } finally {
    saving.value = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除该预算（部门#${row.deptId} / ${row.accountName || '-'} / ${row.budgetYear}年）？`, '提示', { type: 'warning' })
  await budgetDelete(row.id)
  ElMessage.success('已删除')
  loadList(); loadStat()
}

onMounted(async () => {
  orgs.value = await fetchOrgOptions()
  accounts.value = await fetchAccountOptions()
  if (orgs.value.length) query.deptId = null
  loadList()
  loadStat()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; }
</style>
