<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.deptId" placeholder="部门" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="t in APPLY_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(APPLY_STATUS)" :key="v" :label="APPLY_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="单号/申请人" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建申请</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="applyNo" label="申请单号" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ applyTypeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="部门" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="申请人" width="110" align="center">
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="占用预算（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.occupyAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 新建申请 -->
    <el-dialog v-model="formVisible" title="新建费用申请" width="560px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="t in APPLY_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" required>
              <el-select v-model="form.deptId" filterable style="width: 100%">
                <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="申请人" required>
              <el-select v-model="form.userId" filterable style="width: 100%" @change="onUserChange">
                <el-option v-for="u in employees" :key="u.userId" :label="u.name" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请金额" required>
              <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="占用预算">
          <el-select v-model="form.budgetId" clearable filterable style="width: 100%" placeholder="选择要占用的预算（R1 校验余额）">
            <el-option v-for="b in budgets" :key="b.id" :label="budgetLabel(b)" :value="b.id" />
          </el-select>
          <div class="hint">提交审批时按申请金额占用该预算，余额不足将被阻断（R1）。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" placeholder="出差事由/采购用途等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchOrgOptions, fetchEmployeeOptions, budgetPage,
  applyPage, applyCreate, applyDelete, applySubmit,
  APPLY_TYPES, applyTypeLabel, APPLY_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, APPLY_STATUS)
const orgs = ref([])
const employees = ref([])
const budgets = ref([])
const deptName = id => orgs.value.find(o => o.id === id)?.name || (id ? '部门#' + id : '-')
const budgetLabel = b => `${deptName(b.deptId)} / ${b.accountName || '未分科目'} / ${b.budgetYear}年${b.month ? b.month + '月' : ''}（余额 ${money(b.balance)}）`

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ deptId: null, type: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await applyPage({
      current: query.current, size: query.size,
      deptId: query.deptId || undefined, type: query.type || undefined,
      status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.deptId = null; query.type = null; query.status = null; query.kw = ''; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ type: 'TRAVEL', deptId: null, userId: null, userName: null, amount: null, budgetId: null, remark: '' })
const onUserChange = id => { form.userName = employees.value.find(u => u.userId === id)?.name || null }
const loadBudgets = async () => {
  const page = await budgetPage({ current: 1, size: 200, status: 'ACTIVE' }).catch(() => null)
  budgets.value = page?.records || []
}
const openForm = async () => {
  Object.assign(form, { type: 'TRAVEL', deptId: query.deptId, userId: null, userName: null, amount: null, budgetId: null, remark: '' })
  formVisible.value = true
  if (!employees.value.length) employees.value = await fetchEmployeeOptions()
  if (!budgets.value.length) loadBudgets()
}
const onSave = async () => {
  if (!form.type) return ElMessage.warning('请选择类型')
  if (!form.deptId) return ElMessage.warning('请选择部门')
  if (!form.userId) return ElMessage.warning('请选择申请人')
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写申请金额')
  saving.value = true
  try {
    await applyCreate({
      type: form.type, deptId: form.deptId, userId: form.userId, userName: form.userName,
      amount: form.amount, budgetId: form.budgetId || undefined, remark: form.remark || undefined
    })
    ElMessage.success('申请已创建（草稿），可提交审批')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 提交审批：R1 超预算后端报错，request.js 已 ElMessage.error 展示，此处不吞错
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交申请 ${row.applyNo} 进入审批？提交后将占用预算（R1 余额不足将被阻断）。`, '提交审批', { type: 'warning' })
  try {
    await applySubmit(row.id)
    ElMessage.success('已提交审批，预算已占用')
    loadList()
  } catch (e) { /* 后端 R1 阻断提示已由 request.js 展示 */ }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除申请 ${row.applyNo}？`, '提示', { type: 'warning' })
  await applyDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(async () => {
  orgs.value = await fetchOrgOptions()
  if (orgs.value.length) query.deptId = orgs.value[0].id
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
