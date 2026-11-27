<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.year" placeholder="年度" type="number" style="width: 100px" @keyup.enter="onSearch" />
          <el-select v-model="query.type" placeholder="预算线" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="t in BUD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(BUD_STATUS)" :key="v" :label="BUD_STATUS[v][0]" :value="v" />
          </el-select>
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 170px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="query.kw" placeholder="科目" clearable style="width: 140px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">预算编制</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">年度预算（元）</div><div class="value">{{ money(stat.annual) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已分解月度（元）</div><div class="value">{{ money(stat.allocated) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已执行（元）</div><div class="value">{{ money(stat.actual) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">未分解余额（元）</div><div class="value">{{ money(stat.unallocated) }}</div></el-card></el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="budgetCode" label="编号" width="140" show-overflow-tooltip />
        <el-table-column label="预算线" width="80" align="center">
          <template #default="{ row }"><el-tag :type="row.budgetType === 'COST' ? 'primary' : 'success'" size="small">{{ budTypeLabel(row.budgetType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="范围" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || row.deptName || (row.projectId ? '项目#' + row.projectId : row.deptId ? '部门#' + row.deptId : '-') }}</template>
        </el-table-column>
        <el-table-column prop="accountName" label="科目" min-width="140" show-overflow-tooltip />
        <el-table-column label="控制" width="70" align="center">
          <template #default="{ row }"><el-tag :type="controlTag(row.controlMode)" size="small">{{ row.controlMode || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="年度预算（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.annualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已分解（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.allocatedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已执行（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.actualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="执行率" width="90" align="center">
          <template #default="{ row }">
            <span :style="Number(row.execRate) >= 100 ? 'color:#c45656;font-weight:600' : ''">{{ row.execRate != null ? row.execRate + '%' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onActivate(row)">生效</el-button>
            <el-button v-if="row.status === 'APPROVED'" link type="warning" size="small" @click="onArchive(row)">归档</el-button>
            <el-button link type="primary" size="small" @click="openAdjust(row)">调整</el-button>
            <el-button v-if="row.status !== 'ARCHIVED'" link type="primary" size="small" @click="openMonths(row)">月度分解</el-button>
            <el-button v-if="row.status === 'DRAFT' && !row.allocatedAmount" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 预算编制 -->
    <el-dialog v-model="formVisible" title="全面预算编制（年度）" width="560px">
      <el-form label-width="100px">
        <el-form-item label="预算年度" required>
          <el-input-number v-model="form.budgetYear" :controls="false" :min="2020" :max="2099" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预算线" required>
          <el-radio-group v-model="form.budgetType" @change="onBudgetTypeChange">
            <el-radio value="COST">成本线（项目×成本科目）</el-radio>
            <el-radio value="EXPENSE">费用线（部门×费用科目）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.budgetType === 'COST'" label="项目" required>
          <el-select v-model="form.projectId" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="部门" required>
          <el-select v-model="form.deptId" filterable style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预算科目" required>
          <el-select v-model="form.accountId" filterable style="width: 100%" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年度预算额" required>
          <el-input-number v-model="form.annualAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="控制方式">
          <el-select v-model="form.controlMode" style="width: 100%">
            <el-option v-for="m in CONTROL_MODES" :key="m" :label="m" :value="m" />
          </el-select>
          <div class="hint">默认继承科目控制方式：刚性=超预算阻断 / 预警=放行留痕 / 不限=不校验</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 年度调整（B2） -->
    <el-dialog v-model="adjustVisible" title="年度预算调整" width="460px">
      <el-alert type="info" :closable="false" class="mb"
        title="调增直接生效；调减后年度额须 ≥ 已分解月度预算 且 ≥ 已执行额（B2）" />
      <el-form label-width="90px">
        <el-form-item label="调整方向" required>
          <el-radio-group v-model="adjustForm.adjustType">
            <el-radio value="INCREASE">调增</el-radio>
            <el-radio value="DECREASE">调减</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整金额" required>
          <el-input-number v-model="adjustForm.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdjust">提交调整</el-button>
      </template>
    </el-dialog>

    <!-- 月度分解（一键分解 + 结果预览） -->
    <el-dialog v-model="monthsVisible" :title="`月度分解 - ${monthsBudget?.accountName || ''}（${monthsBudget?.budgetYear || ''}年）`" width="560px">
      <el-alert type="info" :closable="false" class="mb"
        title="规则 B1：Σ月度预算 ≤ 年度预算额；已执行月份不可低于已执行额" />
      <div class="gen-row">
        <span class="hint">一键分解 = 平均 12 个月（整体重建，将覆盖已有月度分解，仍受 B1 约束）</span>
        <el-button type="primary" plain :loading="saving" @click="onGenerate">生成月度预算</el-button>
      </div>
      <el-table v-if="months.length" :data="months" border size="small" class="mt">
        <el-table-column prop="month" label="月份" width="70" align="center">
          <template #default="{ row }">{{ row.month }}月</template>
        </el-table-column>
        <el-table-column label="月度预算（元）" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.monthAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已执行（元）" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.actualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.overBudget ? 'danger' : 'success'" size="small">{{ row.overBudget ? '超执行' : '正常' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="hint mt">详细月度调整/跨月调剂请在「月度预算」菜单操作。</div>
      <template #footer>
        <el-button @click="monthsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  BUD_STATUS, BUD_TYPES, CONTROL_MODES, budTypeLabel, controlTag, statusMeta,
  fetchProjectOptions, fetchOrgOptions, fetchAccountOptions, fetchExpenseAccountOptions,
  annualPage, annualCreate, annualActivate, annualArchive, annualDelete, annualAdjust,
  monthlyList, monthGenerate
} from '../../../api/budget'

const meta = s => statusMeta(s, BUD_STATUS)
const projects = ref([])
const orgs = ref([])
const accounts = ref([])
const costAccounts = ref([])
const expenseAccounts = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ year: new Date().getFullYear(), type: null, status: null, projectId: null, kw: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await annualPage({
      current: query.current, size: query.size,
      year: query.year || undefined, type: query.type || undefined,
      status: query.status || undefined, projectId: query.projectId || undefined,
      kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    loadStat()
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { Object.assign(query, { type: null, status: null, projectId: null, kw: null }); onSearch() }

const stat = ref({ annual: 0, allocated: 0, actual: 0, unallocated: 0 })
const loadStat = () => {
  const sum = k => rows.value.reduce((s, r) => s + (Number(r[k]) || 0), 0)
  stat.value = {
    annual: sum('annualAmount'), allocated: sum('allocatedAmount'),
    actual: sum('actualAmount'), unallocated: sum('annualAmount') - sum('allocatedAmount')
  }
}

// ===== 编制 =====
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ budgetYear: new Date().getFullYear(), budgetType: 'COST', projectId: null, deptId: null, accountId: null, accountName: null, annualAmount: null, controlMode: null, remark: null })
/** 按预算线切换科目下拉源：成本线→成本科目 / 费用线→费用科目 */
const loadAccounts = type => { accounts.value = type === 'EXPENSE' ? expenseAccounts.value : costAccounts.value }
const onBudgetTypeChange = () => {
  form.accountId = null
  form.accountName = null
  form.controlMode = null
  loadAccounts(form.budgetType)
}
const onAccountChange = id => {
  const a = accounts.value.find(x => x.id === id)
  form.accountName = a?.name || null
  form.controlMode = a?.controlMode || null
}
const openForm = () => {
  Object.assign(form, { budgetYear: query.year || new Date().getFullYear(), budgetType: query.type || 'COST', projectId: query.projectId, deptId: null, accountId: null, accountName: null, annualAmount: null, controlMode: null, remark: null })
  loadAccounts(form.budgetType)
  formVisible.value = true
}
const onSave = async () => {
  if (form.budgetType === 'COST' && !form.projectId) return ElMessage.warning('请选择项目')
  if (form.budgetType === 'EXPENSE' && !form.deptId) return ElMessage.warning('请选择部门')
  if (!form.accountId) return ElMessage.warning('请选择预算科目')
  if (!form.annualAmount || Number(form.annualAmount) <= 0) return ElMessage.warning('请填写年度预算额')
  saving.value = true
  try {
    await annualCreate({
      budgetYear: form.budgetYear, budgetType: form.budgetType,
      projectId: form.budgetType === 'COST' ? form.projectId : undefined,
      deptId: form.budgetType === 'EXPENSE' ? form.deptId : undefined,
      accountId: form.accountId, accountName: form.accountName,
      annualAmount: form.annualAmount, controlMode: form.controlMode || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success('预算已编制（草稿），生效后方可参与管控')
    formVisible.value = false
    loadList()
  } finally { saving.value = false }
}

// ===== 状态操作 =====
const onActivate = async row => {
  await ElMessageBox.confirm('生效后该预算参与管控（B1/B3/B4/B5），确认生效？', '提示', { type: 'success' })
  await annualActivate(row.id)
  ElMessage.success('已生效')
  loadList()
}
const onArchive = async row => {
  await ElMessageBox.confirm('归档后该预算退出管控，确认归档？', '提示', { type: 'warning' })
  await annualArchive(row.id)
  ElMessage.success('已归档')
  loadList()
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除预算 ${row.budgetCode}？`, '提示', { type: 'warning' })
  await annualDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

// ===== 年度调整（B2） =====
const adjustVisible = ref(false)
const adjustForm = reactive({ adjustType: 'INCREASE', amount: null, reason: '' })
let adjustTarget = null
const openAdjust = row => {
  adjustTarget = row
  Object.assign(adjustForm, { adjustType: 'INCREASE', amount: null, reason: '' })
  adjustVisible.value = true
}
const onAdjust = async () => {
  if (!adjustForm.amount || Number(adjustForm.amount) <= 0) return ElMessage.warning('请填写调整金额')
  if (!adjustForm.reason) return ElMessage.warning('请填写调整原因')
  saving.value = true
  try {
    await annualAdjust(adjustTarget.id, { adjustType: adjustForm.adjustType, amount: adjustForm.amount, reason: adjustForm.reason })
    ElMessage.success('年度预算已调整，留痕已记录')
    adjustVisible.value = false
    loadList()
  } finally { saving.value = false }
}

// ===== 月度分解 =====
const monthsVisible = ref(false)
const monthsBudget = ref(null)
const months = ref([])
const openMonths = async row => {
  monthsBudget.value = row
  months.value = (await monthlyList(row.id).catch(() => [])) || []
  monthsVisible.value = true
}
const onGenerate = async () => {
  saving.value = true
  try {
    const n = await monthGenerate(monthsBudget.value.id, { mode: 'EVEN' })
    ElMessage.success(`已按平均分解生成 ${n} 个月度预算`)
    months.value = (await monthlyList(monthsBudget.value.id).catch(() => [])) || []
  } finally { saving.value = false }
}

onMounted(async () => {
  projects.value = (await fetchProjectOptions().catch(() => [])) || []
  orgs.value = await fetchOrgOptions()
  costAccounts.value = await fetchAccountOptions()
  expenseAccounts.value = await fetchExpenseAccountOptions()
  loadAccounts(form.budgetType)
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; }
.mt { margin-top: 12px; }
.mb { margin-bottom: 12px; }
.gen-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>
