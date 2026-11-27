<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.contractId" placeholder="关联合同" clearable filterable style="width: 240px" @change="onSearch">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="query.paymentType" placeholder="付款类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in DICTS.payment_type" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="付款编号" clearable style="width: 170px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增付款申请</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">申请总额（元）</div>
          <div class="value">{{ money(summary.applyAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">审批通过金额（元）</div>
          <div class="value">{{ money(summary.approvedAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">实付总额（元）</div>
          <div class="value">{{ money(summary.paidAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">待支付金额（元）</div>
          <div class="value">{{ money(summary.unpaidAmount) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="paymentCode" label="付款编号" width="170" show-overflow-tooltip />
        <el-table-column prop="contractName" label="关联合同" min-width="190" show-overflow-tooltip />
        <el-table-column label="付款类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dictLabel('payment_type', row.paymentType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请金额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.applyAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="审批金额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.approvedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="实付金额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="累计已付（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.appliedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="关联发票（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.invoiceAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="形象进度" width="90" align="right">
          <template #default="{ row }">{{ row.imageProgressPct != null ? row.imageProgressPct + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="付款日期" width="100" align="center">
          <template #default="{ row }">{{ fmtDate(row.paidDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="warning" size="small" @click="onSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
            <el-button v-if="row.status === 'APPROVED'" link type="success" size="small" @click="openPay(row)">登记付款</el-button>
          </template>
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
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="付款申请详情" size="52%">
      <template v-if="detail">
        <h4 class="drawer-title">基础信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="付款编号">{{ detail.paymentCode }}</el-descriptions-item>
          <el-descriptions-item label="付款类型">{{ dictLabel('payment_type', detail.paymentType) }}</el-descriptions-item>
          <el-descriptions-item label="关联合同" :span="2">{{ detail.contractName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请金额（元）"><span class="num">{{ money(detail.applyAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="审批通过金额（元）"><span class="num">{{ money(detail.approvedAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="实付金额（元）"><span class="num">{{ money(detail.paidAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="申请时点累计已付（元）"><span class="num">{{ money(detail.appliedAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="本次关联发票额（元）"><span class="num">{{ money(detail.invoiceAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="形象进度">{{ detail.imageProgressPct != null ? detail.imageProgressPct + '%' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColor(detail.status)" size="small">{{ STATUS[detail.status] || detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="付款日期">{{ fmtDate(detail.paidDate) }}</el-descriptions-item>
          <el-descriptions-item label="付款说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="drawer-title">关联合同付款计划</h4>
        <el-table :data="paymentPlans" border size="small">
          <el-table-column prop="termNo" label="期数" width="60" align="center" />
          <el-table-column prop="termDesc" label="付款条件" min-width="150" show-overflow-tooltip />
          <el-table-column label="计划金额（元）" width="130" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.planAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="已付（元）" width="120" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
          </el-table-column>
          <el-table-column label="计划日期" width="100" align="center">
            <template #default="{ row }">{{ fmtDate(row.planDate) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑付款申请' : '新增付款申请'" width="540px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="关联合同" required>
          <el-select v-model="form.contractId" filterable style="width: 100%">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name + '（' + c.contractCode + '）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="付款类型" required>
              <el-select v-model="form.paymentType" style="width: 100%">
                <el-option v-for="(label, key) in DICTS.payment_type" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请金额" required>
              <el-input-number v-model="form.applyAmount" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联发票额">
              <el-input-number v-model="form.invoiceAmount" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="形象进度%">
              <el-input-number v-model="form.imageProgressPct" :controls="false" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="付款说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 登记付款 -->
    <el-dialog v-model="payVisible" title="登记付款" width="440px">
      <el-form :model="payForm" label-width="90px">
        <el-form-item label="实付金额" required>
          <el-input-number v-model="payForm.paidAmount" :controls="false" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="payForm.paidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onPay">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, statusColor, dictLabel, DICTS } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions, fetchContractPaymentPlans,
  fetchPayments, fetchPaymentSummary, fetchPaymentDetail,
  createPayment, updatePayment, deletePayment, submitPayment, registerPayment
} from '../../../api/cost'

const STATUS = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '审批通过', PAID: '已付款', REJECTED: '已驳回' }

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const contracts = ref([])
const rows = ref([])
const total = ref(0)
const summary = ref({})
const query = reactive({ projectId: null, contractId: null, paymentType: null, status: null, kw: '', current: 1, size: 20 })

const detailVisible = ref(false)
const detail = ref(null)
const paymentPlans = ref([])

const formVisible = ref(false)
const form = reactive({ id: null, contractId: null, paymentType: 'PROGRESS', applyAmount: null, invoiceAmount: null, imageProgressPct: null, description: '' })
const payVisible = ref(false)
const payForm = reactive({ id: null, paidAmount: null, paidDate: null })

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchPayments(query),
      fetchPaymentSummary({ projectId: query.projectId, contractId: query.contractId })
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    summary.value = sum || {}
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.contractId = null
  query.paymentType = null
  query.status = null
  query.kw = ''
  onSearch()
}

const loadContracts = async () => {
  contracts.value = query.projectId ? ((await fetchContractOptions({ projectId: query.projectId })) || []) : []
}

const onProjectChange = async () => {
  query.contractId = null
  await loadContracts()
  onSearch()
}

const openDetail = async row => {
  detail.value = row
  paymentPlans.value = []
  detailVisible.value = true
  const [d, pps] = await Promise.all([
    fetchPaymentDetail(row.id),
    row.contractId ? fetchContractPaymentPlans(row.contractId) : Promise.resolve([])
  ])
  detail.value = { ...row, ...d }
  paymentPlans.value = pps || []
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, contractId: row.contractId, paymentType: row.paymentType || 'PROGRESS',
      applyAmount: row.applyAmount, invoiceAmount: row.invoiceAmount,
      imageProgressPct: row.imageProgressPct, description: row.description || ''
    })
  } else {
    Object.assign(form, { id: null, contractId: null, paymentType: 'PROGRESS', applyAmount: null, invoiceAmount: null, imageProgressPct: null, description: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.contractId) return ElMessage.warning('请选择关联合同')
  if (form.applyAmount == null) return ElMessage.warning('请填写申请金额')
  saving.value = true
  try {
    if (form.id) {
      await updatePayment({ ...form, projectId: query.projectId })
      ElMessage.success('付款申请已更新')
    } else {
      await createPayment({ ...form, projectId: query.projectId })
      ElMessage.success('付款申请已创建')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const openPay = row => {
  Object.assign(payForm, { id: row.id, paidAmount: row.approvedAmount ?? row.applyAmount, paidDate: new Date() })
  payVisible.value = true
}

const onPay = async () => {
  if (payForm.paidAmount == null) return ElMessage.warning('请填写实付金额')
  if (!payForm.paidDate) return ElMessage.warning('请选择付款日期')
  saving.value = true
  try {
    await registerPayment(payForm.id, { paidAmount: payForm.paidAmount, paidDate: payForm.paidDate })
    ElMessage.success('付款登记成功，累计付款已回写')
    payVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onSubmit = async row => {
  await ElMessageBox.confirm('提交后将进入付款审批流程，确认提交？', '提示', { type: 'warning' })
  await submitPayment(row.id)
  ElMessage.success('已提交审批')
  await loadList()
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除付款申请【${row.paymentCode}】？仅草稿或驳回状态可删除`, '提示', { type: 'warning' })
  await deletePayment(row.id)
  ElMessage.success('已删除')
  await loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) query.projectId = projects.value[0].id
  await loadContracts()
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.drawer-title { margin: 14px 0 10px; font-size: 14px; color: #0c447c; }
</style>
