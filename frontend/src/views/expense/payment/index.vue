<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <el-tabs v-model="tab" class="pay-tabs" @tab-change="onTabChange">
        <el-tab-pane label="付款申请（待付）" name="DRAFT" />
        <el-tab-pane label="付款登记（已付）" name="PAID" />
        <el-tab-pane label="全部" name="ALL" />
      </el-tabs>
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.reimbId" placeholder="关联报销" clearable filterable style="width: 200px" @change="onSearch">
            <el-option v-for="r in reimbOptions" :key="r.id" :label="r.reimbNo" :value="r.id" />
          </el-select>
          <el-input v-model="query.kw" placeholder="付款单号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建付款申请</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="payNo" label="付款单号" width="150" show-overflow-tooltip />
        <el-table-column label="付款类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reimbId ? 'primary' : 'success'" size="small">{{ row.reimbId ? '费用' : '合同' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联报销单" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ reimbNo(row.reimbId) }}</template>
        </el-table-column>
        <el-table-column label="关联费用合同" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ contractLabel(row.contractId) }}</template>
        </el-table-column>
        <el-table-column label="付款金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="付款日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.paidDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="openPay(row)">实付登记</el-button>
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

    <!-- 新建付款申请 -->
    <el-dialog v-model="formVisible" title="新建付款申请" width="560px">
      <el-radio-group v-model="form.mode" class="mode-group" @change="onModeChange">
        <el-radio-button value="REIMB">费用付款（报销）</el-radio-button>
        <el-radio-button value="CONTRACT">合同付款</el-radio-button>
      </el-radio-group>

      <el-form v-if="form.mode === 'REIMB'" label-width="100px" class="form-body">
        <el-form-item label="关联报销" required>
          <el-select v-model="form.reimbId" filterable style="width: 100%" placeholder="选择审批通过的报销单" @change="onReimbChange">
            <el-option v-for="r in paidReimbs" :key="r.id" :label="r.reimbNo + '（' + money(r.totalAmount) + '）'" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款金额" required>
          <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
          <div class="hint">默认带出报销总额，可修改（如分次付款）。</div>
        </el-form-item>
        <el-form-item label="关联合同">
          <el-select v-model="form.contractId" clearable filterable style="width: 100%" placeholder="选择费用合同（可选，实付时回写合同已付）">
            <el-option v-for="c in contracts" :key="c.id" :label="contractLabel(c.id)" :value="c.id" />
          </el-select>
          <div class="hint">关联后实付登记将累计合同已付金额，超过合同未付余额将被阻断。</div>
        </el-form-item>
      </el-form>

      <el-form v-else label-width="100px" class="form-body">
        <el-form-item label="费用合同" required>
          <el-select v-model="form.contractId" filterable style="width: 100%" placeholder="选择生效中的费用合同" @change="onContractChange">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name + '（未付 ' + money(Number(c.amount) - Number(c.paidAmount || 0)) + '）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款金额" required>
          <el-input-number v-model="form.amount" :controls="false" :min="0" :max="contractRemain()" :precision="2" style="width: 100%" />
          <div class="hint">默认带出合同未付余额，可修改（分次付款）；超过未付余额将被阻断。</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">生成付款申请</el-button>
      </template>
    </el-dialog>

    <!-- 实付登记 -->
    <el-dialog v-model="payVisible" title="实付登记（付款登记）" width="460px">
      <el-form label-width="90px">
        <el-form-item label="付款单号">
          <span>{{ pay.row?.payNo }}</span>
        </el-form-item>
        <el-form-item label="付款类型">
          <el-tag :type="pay.row?.reimbId ? 'primary' : 'success'" size="small">{{ pay.row?.reimbId ? '费用' : '合同' }}</el-tag>
        </el-form-item>
        <el-form-item label="实付金额" required>
          <el-input-number v-model="pay.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="pay.paidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <div class="hint">实付后付款单置「已付款」进入付款登记；关联报销单回写「已付款」，关联合同累计已付金额。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="pay.saving" @click="onPay">确认实付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  paymentPage, paymentCreate, paymentPay,
  contractOptions, contractPaymentCreate,
  reimbursePage, PAY_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, PAY_STATUS)
const reimbOptions = ref([])
const paidReimbs = ref([])
const reimbNo = id => reimbOptions.value.find(r => r.id === id)?.reimbNo || (id ? '报销#' + id : '-')
const contracts = ref([])
const contractLabel = id => contracts.value.find(c => c.id === id)?.name || (id ? '合同#' + id : '-')
const contractRemain = () => {
  const c = contracts.value.find(x => x.id === form.contractId)
  return c ? Number(c.amount) - Number(c.paidAmount || 0) : undefined
}

// 页签：付款申请(DRAFT) / 付款登记(PAID) / 全部
const tab = ref('DRAFT')
const onTabChange = () => {
  query.status = tab.value === 'ALL' ? null : tab.value
  query.current = 1
  loadList()
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ reimbId: null, status: 'DRAFT', kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await paymentPage({
      current: query.current, size: query.size,
      reimbId: query.reimbId || undefined, status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.reimbId = null; query.kw = ''; onSearch() }

const loadReimbs = async () => {
  try {
    const all = await reimbursePage({ current: 1, size: 500 })
    reimbOptions.value = all?.records || []
    paidReimbs.value = reimbOptions.value.filter(r => r.status === 'APPROVED')
  } catch (e) { reimbOptions.value = []; paidReimbs.value = [] }
}
const loadContracts = async () => {
  contracts.value = (await contractOptions().catch(() => [])) || []
}

// 新建付款申请：REIMB=费用付款 / CONTRACT=合同付款
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ mode: 'REIMB', reimbId: null, contractId: null, amount: null })
const onReimbChange = id => { form.amount = reimbOptions.value.find(r => r.id === id)?.totalAmount || null }
const onContractChange = id => {
  const c = contracts.value.find(x => x.id === id)
  form.amount = c ? Number(c.amount) - Number(c.paidAmount || 0) : null
}
const onModeChange = () => { form.reimbId = null; form.contractId = null; form.amount = null }
const openForm = async () => {
  Object.assign(form, { mode: 'REIMB', reimbId: null, contractId: null, amount: null })
  formVisible.value = true
  if (!contracts.value.length) loadContracts()
}
const onSave = async () => {
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写付款金额')
  saving.value = true
  try {
    if (form.mode === 'REIMB') {
      if (!form.reimbId) { saving.value = false; return ElMessage.warning('请选择关联报销单') }
      await paymentCreate({ reimbId: form.reimbId, amount: form.amount, contractId: form.contractId || undefined })
    } else {
      if (!form.contractId) { saving.value = false; return ElMessage.warning('请选择费用合同') }
      await contractPaymentCreate(form.contractId, { amount: form.amount })
    }
    ElMessage.success('付款申请已生成（待付）')
    formVisible.value = false
    tab.value = 'DRAFT'
    query.status = 'DRAFT'
    loadList()
  } finally {
    saving.value = false
  }
}

const payVisible = ref(false)
const pay = reactive({ row: null, amount: null, paidDate: null, saving: false })
const openPay = row => {
  pay.row = row
  pay.amount = row.amount
  pay.paidDate = fmtDate(new Date())
  payVisible.value = true
}
const onPay = async () => {
  if (!pay.amount || Number(pay.amount) <= 0) return ElMessage.warning('请填写实付金额')
  if (!pay.paidDate) return ElMessage.warning('请选择付款日期')
  pay.saving = true
  try {
    await paymentPay(pay.row.id, { amount: pay.amount, paidDate: pay.paidDate })
    ElMessage.success('实付登记成功，已进入付款登记')
    payVisible.value = false
    loadList()
  } finally {
    pay.saving = false
  }
}

onMounted(async () => {
  await loadReimbs()
  loadContracts()
  loadList()
})
</script>

<style scoped>
.pay-tabs { --el-tabs-header-height: 42px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
.mode-group { margin-bottom: 4px; }
.form-body { margin-top: 16px; }
</style>
