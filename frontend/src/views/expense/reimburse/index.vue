<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.deptId" placeholder="部门" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="t in REIMB_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(REIMB_STATUS)" :key="v" :label="REIMB_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="报销单号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建报销</el-button>
      </div>
    </el-card>

    <el-alert type="warning" :closable="false" show-icon class="rule-alert">
      <template #title>超标（R3）须填写超标说明并走特批；关联发票须验真通过，重复发票将被阻断（R2）。</template>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="reimbNo" label="报销单号" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">{{ reimbTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="部门" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="报销人" width="110" align="center">
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <el-table-column label="总额（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.totalAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="超标额（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :style="Number(row.overAmount) > 0 ? 'color:#c45656;font-weight:700' : ''">{{ money(row.overAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="overReason" label="超标说明" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.overReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
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

    <!-- 新建报销 -->
    <el-dialog v-model="formVisible" title="新建报销" width="760px" top="6vh">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="类型" required>
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="t in REIMB_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="部门" required>
              <el-select v-model="form.deptId" filterable style="width: 100%">
                <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="报销人" required>
              <el-select v-model="form.userId" filterable style="width: 100%" @change="onUserChange">
                <el-option v-for="u in employees" :key="u.userId" :label="u.name" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="关联申请">
              <el-select v-model="form.applyId" clearable filterable style="width: 100%" placeholder="可选" @change="onApplyChange">
                <el-option v-for="a in applies" :key="a.id" :label="a.applyNo + '（' + money(a.amount) + '）'" :value="a.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="冲销借款">
              <el-select v-model="form.loanId" clearable filterable style="width: 100%" placeholder="可选">
                <el-option v-for="l in loans" :key="l.id" :label="l.loanNo + '（余 ' + money(l.unwriteoffBalance) + '）'" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联发票">
              <el-select v-model="form.invoiceId" clearable filterable style="width: 100%" placeholder="须验真通过">
                <el-option v-for="i in invoices" :key="i.id" :label="i.invNo + '（' + money(i.amount) + '）'" :value="i.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="items-head">
          <span class="items-title">报销明细（合计须等于报销总额）</span>
          <el-button type="primary" size="small" plain @click="addItem">+ 添加明细</el-button>
        </div>
        <div v-for="(it, idx) in form.items" :key="idx" class="item-row">
          <el-select v-model="it.accountId" filterable style="width: 240px" placeholder="科目" @change="onItemAccount(it)">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
          <el-input-number v-model="it.amount" :controls="false" :min="0" :precision="2" style="width: 160px" placeholder="金额" />
          <el-input v-model="it.remark" style="width: 200px" placeholder="备注" />
          <el-button link type="danger" @click="form.items.splice(idx, 1)">删除</el-button>
        </div>
        <div v-if="!form.items.length" class="hint">暂无明细，请添加至少一条（科目 + 金额）。</div>

        <el-row :gutter="12" class="total-row">
          <el-col :span="8">
            <el-form-item label="报销总额">
              <span class="num total-num">{{ money(itemsTotal) }} 元</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="超标额">
              <span class="num" :style="overAmount > 0 ? 'color:#c45656;font-weight:700' : ''">{{ money(overAmount) }} 元</span>
              <span v-if="overAmount > 0" class="over-hint">（超标，须说明）</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="overAmount > 0 ? '超标说明*' : '超标说明'">
              <el-input v-model="form.overReason" placeholder="超标原因（R3 必填）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchOrgOptions, fetchEmployeeOptions, fetchAccountOptions,
  applyPage, loanPage, invoicePage,
  reimbursePage, reimburseCreate, reimburseSubmit,
  REIMB_TYPES, reimbTypeLabel, REIMB_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, REIMB_STATUS)
const orgs = ref([])
const employees = ref([])
const accounts = ref([])
const applies = ref([])
const loans = ref([])
const invoices = ref([])
const deptName = id => orgs.value.find(o => o.id === id)?.name || (id ? '部门#' + id : '-')

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ deptId: null, type: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await reimbursePage({
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
const form = reactive({
  type: 'TRAVEL', deptId: null, userId: null, userName: null,
  applyId: null, loanId: null, invoiceId: null, overReason: '', items: []
})
const itemsTotal = computed(() => form.items.reduce((s, it) => s + (Number(it.amount) || 0), 0))
const overAmount = computed(() => {
  const standard = applies.value.find(a => a.id === form.applyId)?.amount
  return Math.max((Number(standard) || 0) ? itemsTotal.value - Number(standard) : 0, 0)
})
const onUserChange = id => { form.userName = employees.value.find(u => u.userId === id)?.name || null }
const onApplyChange = id => { form.applyId = id }
const onItemAccount = it => { it.accountName = accounts.value.find(a => a.id === it.accountId)?.name || null }
const addItem = () => form.items.push({ accountId: null, accountName: null, amount: null, remark: '' })

const openForm = async () => {
  Object.assign(form, {
    type: 'TRAVEL', deptId: query.deptId, userId: null, userName: null,
    applyId: null, loanId: null, invoiceId: null, overReason: '', items: []
  })
  addItem()
  formVisible.value = true
  await Promise.all([
    !employees.value.length ? fetchEmployeeOptions().then(v => employees.value = v) : Promise.resolve(),
    !applies.value.length ? applyPage({ current: 1, size: 200 }).then(p => applies.value = p?.records || []).catch(() => {}) : Promise.resolve(),
    !loans.value.length ? loanPage({ current: 1, size: 200 }).then(p => loans.value = (p?.records || []).filter(l => Number(l.unwriteoffBalance) > 0)).catch(() => {}) : Promise.resolve(),
    !invoices.value.length ? invoicePage({ current: 1, size: 200, verifyStatus: 'PASS' }).then(p => invoices.value = p?.records || []).catch(() => {}) : Promise.resolve()
  ])
}
const onSave = async () => {
  if (!form.deptId) return ElMessage.warning('请选择部门')
  if (!form.userId) return ElMessage.warning('请选择报销人')
  if (!form.items.length) return ElMessage.warning('请添加至少一条明细')
  if (form.items.some(it => !it.accountId)) return ElMessage.warning('请为每条明细选择科目')
  if (form.items.some(it => !it.amount || Number(it.amount) <= 0)) return ElMessage.warning('请填写每条明细的金额')
  if (overAmount.value > 0 && !form.overReason) return ElMessage.warning('报销超标，须填写超标说明（R3）')
  saving.value = true
  try {
    await reimburseCreate({
      type: form.type, deptId: form.deptId, userId: form.userId, userName: form.userName,
      totalAmount: itemsTotal.value, overAmount: overAmount.value || 0, overReason: form.overReason || undefined,
      applyId: form.applyId || undefined, loanId: form.loanId || undefined, invoiceId: form.invoiceId || undefined,
      items: form.items.map(it => ({ accountId: it.accountId, accountName: it.accountName, amount: it.amount, remark: it.remark || undefined }))
    })
    ElMessage.success('报销已创建（草稿），可提交审批')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 提交：超标走特批 / 未超标直接核销；发票未验真、重复发票等由后端 R2/R3 报错，request.js 已提示
const onSubmit = async row => {
  const tip = row.overAmount > 0
    ? `该报销超标 ${money(row.overAmount)} 元，将发起超标特批流（R3）。`
    : '未超标，提交后将直接生效并核销预算/冲销借款。'
  await ElMessageBox.confirm(`确认提交报销 ${row.reimbNo}？${tip}`, '提交审批', { type: 'warning' })
  try {
    await reimburseSubmit(row.id)
    ElMessage.success('已提交')
    loadList()
  } catch (e) { /* 后端 R2/R3 阻断提示已由 request.js 展示 */ }
}

onMounted(async () => {
  orgs.value = await fetchOrgOptions()
  accounts.value = await fetchAccountOptions()
  if (orgs.value.length) query.deptId = orgs.value[0].id
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.rule-alert { margin-bottom: 12px; }
.items-head { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; }
.items-title { font-size: 13px; font-weight: 600; color: #0c447c; }
.item-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.total-row { margin-top: 8px; }
.total-num { font-size: 16px; font-weight: 700; color: #185fa5; }
.over-hint { font-size: 12px; color: #c45656; margin-left: 4px; }
.hint { font-size: 12px; color: #909399; }
</style>
