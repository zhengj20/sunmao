<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" clearable placeholder="全部项目" style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <span class="hint">点击下方合同列表选择合同，其付款计划在下方子表中显示并维护</span>
        </div>
        <div class="actions">
          <el-button @click="refreshAll">刷新</el-button>
          <el-button type="primary" :disabled="!contractId" @click="openForm">+ 新增付款计划</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">合同列表（{{ contracts.length }}）</span>
          <span class="card-sub">点击行选中合同；付款计划是资金计划成本线的数据来源，也是成本付款月度预算管控（B3）的依据</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="contracts" border stripe highlight-current-row row-key="id"
        :current-row-key="contractId" @current-change="onContractSelect" empty-text="暂无合同">
        <el-table-column prop="contractCode" label="合同编号" width="200" show-overflow-tooltip />
        <el-table-column prop="name" label="合同名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="项目" width="110">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="承包商" width="170" show-overflow-tooltip />
        <el-table-column label="签约金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.signedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click.stop="selectContract(row)">查看付款计划</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="contract" shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">「{{ contract.name }}」付款计划（{{ plans.length }} 期）</span>
        </div>
      </template>
      <div class="contract-info">
        <div class="ci-item"><span class="ci-label">合同编号</span><span class="ci-value">{{ contract.contractCode }}</span></div>
        <div class="ci-item"><span class="ci-label">承包商</span><span class="ci-value">{{ contract.supplierName || '-' }}</span></div>
        <div class="ci-item"><span class="ci-label">签约金额</span><span class="ci-value num">{{ money(contract.signedAmount) }} 元</span></div>
        <div class="ci-item"><span class="ci-label">已付款</span><span class="ci-value num">{{ money(contractPaid) }} 元</span></div>
        <div class="ci-item"><span class="ci-label">未付</span><span class="ci-value num" style="color:#c45656">{{ money(contractRemain) }} 元</span></div>
      </div>
      <el-table :data="plans" border size="small" show-summary :summary-method="planSummary"
        empty-text="该合同暂无付款计划，点击右上角「新增付款计划」">
        <el-table-column prop="termNo" label="期数" width="60" align="center" />
        <el-table-column prop="termDesc" label="付款条件" min-width="200" show-overflow-tooltip />
        <el-table-column label="计划金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.planAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="计划日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.planDate) }}</template>
        </el-table-column>
        <el-table-column label="已付（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.paidAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="未付（元）" width="130" align="right">
          <template #default="{ row }"><span class="num" :style="(num(row.planAmount) - num(row.paidAmount)) > 0 ? 'color:#c45656' : ''">{{ money(num(row.planAmount) - num(row.paidAmount)) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="planStatus(row).type">{{ planStatus(row).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openForm(row)">编辑</el-button>
            <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑付款计划' : '新增付款计划'" width="480px">
      <div v-if="contract" class="dlg-contract">合同：{{ contract.contractCode }} {{ contract.name }}</div>
      <el-form :model="form" label-width="100px" style="margin-top: 12px">
        <el-form-item label="期数" required>
          <el-input-number v-model="form.termNo" :controls="false" :min="1" :step="1" style="width: 120px" />
        </el-form-item>
        <el-form-item label="付款条件" required>
          <el-input v-model="form.termDesc" maxlength="100" placeholder="如：开工后30天内付至合同额的30%" />
        </el-form-item>
        <el-form-item label="计划金额（元）" required>
          <el-input-number v-model="form.planAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划日期" required>
          <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="选择计划付款日期" />
        </el-form-item>
        <el-form-item v-if="form.id" label="已付金额">
          <span class="num">{{ money(form.paidAmount) }} 元（由付款单自动回写，不可修改）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, statusColor } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions, fetchContractPayments,
  fetchContractPaymentPlans, addContractPaymentPlan, updateContractPaymentPlan, deleteContractPaymentPlan
} from '../../../api/cost'

const STATUS = { DRAFT: '草稿', APPROVING: '审批中', EFFECTIVE: '已生效', SETTLED: '已结算', CLOSED: '已关闭' }

const projects = ref([])
const contracts = ref([])
const query = reactive({ projectId: null })
const contractId = ref(null)
const plans = ref([])
const loading = ref(false)
const saving = ref(false)

const formVisible = ref(false)
const form = reactive({ id: null, termNo: 1, termDesc: '', planAmount: null, planDate: null, paidAmount: 0 })

const num = v => Number(v) || 0
const contract = computed(() => contracts.value.find(c => c.id === contractId.value) || null)
const contractPaid = ref(0)
const contractRemain = computed(() => {
  const c = contract.value
  if (!c) return 0
  return num(c.signedAmount) + num(c.changeAmount) - contractPaid.value
})

const projectName = pid => {
  const p = projects.value.find(x => x.id === pid)
  return p ? p.name : '-'
}

const planStatus = row => {
  const plan = num(row.planAmount), paid = num(row.paidAmount)
  if (paid >= plan && plan > 0) return { label: '已付清', type: 'success' }
  if (paid > 0) return { label: '部分支付', type: 'warning' }
  return { label: '未支付', type: 'info' }
}

const planSummary = ({ columns, data }) => {
  const plan = data.reduce((s, r) => s + num(r.planAmount), 0)
  const paid = data.reduce((s, r) => s + num(r.paidAmount), 0)
  return columns.map(col => {
    if (col.label === '期数') return '合计'
    if (col.label.includes('计划金额')) return money(plan)
    if (col.label.includes('已付')) return money(paid)
    if (col.label.includes('未付')) return money(plan - paid)
    return ''
  })
}

const loadProjects = async () => {
  projects.value = (await fetchProjectOptions().catch(() => [])) || []
}

const loadContracts = async () => {
  loading.value = true
  try {
    contracts.value = (await fetchContractOptions({ projectId: query.projectId || undefined }).catch(() => [])) || []
  } finally { loading.value = false }
}

// 合同已付款 = 该合同已审/已付成本付款单合计（PAID 取实付额，APPROVED 取审批额）
const loadContractPaid = async () => {
  if (!contractId.value) { contractPaid.value = 0; return }
  const list = (await fetchContractPayments(contractId.value).catch(() => [])) || []
  contractPaid.value = list
    .filter(p => ['APPROVED', 'PAID'].includes(p.status))
    .reduce((s, p) => s + (p.status === 'PAID' ? num(p.paidAmount) : (num(p.approvedAmount) || num(p.applyAmount))), 0)
}

const loadPlans = async () => {
  if (!contractId.value) { plans.value = []; contractPaid.value = 0; return }
  plans.value = (await fetchContractPaymentPlans(contractId.value).catch(() => [])) || []
  await loadContractPaid()
}

const selectContract = row => {
  if (!row || row.id === contractId.value) return
  contractId.value = row.id
  loadPlans()
}

const onContractSelect = row => selectContract(row)

const onProjectChange = async () => {
  contractId.value = null
  plans.value = []
  contractPaid.value = 0
  await loadContracts()
}

const refreshAll = async () => {
  await loadContracts()
  if (contractId.value) await loadPlans()
}

const openForm = row => {
  if (!contractId.value) return
  if (row) {
    Object.assign(form, { id: row.id, termNo: row.termNo, termDesc: row.termDesc || '', planAmount: row.planAmount, planDate: row.planDate, paidAmount: row.paidAmount ?? 0 })
  } else {
    const nextNo = plans.value.length ? Math.max(...plans.value.map(p => num(p.termNo))) + 1 : 1
    Object.assign(form, { id: null, termNo: nextNo, termDesc: '', planAmount: null, planDate: null, paidAmount: 0 })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!String(form.termDesc || '').trim()) return ElMessage.warning('请填写付款条件')
  if (form.planAmount == null || form.planAmount <= 0) return ElMessage.warning('请填写计划金额')
  if (!form.planDate) return ElMessage.warning('请选择计划日期')
  saving.value = true
  try {
    if (form.id) {
      await updateContractPaymentPlan({ contractId: contractId.value, id: form.id, termNo: form.termNo, termDesc: form.termDesc, planAmount: form.planAmount, planDate: form.planDate })
      ElMessage.success('付款计划已更新')
    } else {
      await addContractPaymentPlan(contractId.value, { termNo: form.termNo, termDesc: form.termDesc, planAmount: form.planAmount, planDate: form.planDate })
      ElMessage.success('付款计划已新增')
    }
    formVisible.value = false
    await loadPlans()
  } finally { saving.value = false }
}

const onDelete = async row => {
  const paid = num(row.paidAmount)
  await ElMessageBox.confirm(paid > 0
    ? `该期已付款 ${money(paid)} 元，删除后资金计划与付款统计将不一致，确认删除？`
    : `确认删除第 ${row.termNo} 期付款计划？`, '提示', { type: 'warning' })
  await deleteContractPaymentPlan(contractId.value, row.id)
  ElMessage.success('已删除')
  await loadPlans()
}

onMounted(async () => {
  await loadProjects()
  await loadContracts()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; }
.table-card { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-title { font-weight: 600; }
.card-sub { font-size: 12px; color: #6b7280; }
.hint { font-size: 12px; color: #6b7280; }
.contract-info { display: flex; flex-wrap: wrap; gap: 8px 28px; margin-bottom: 12px; background: #f5f7fa; border-radius: 6px; padding: 10px 14px; }
.ci-item { display: flex; align-items: center; gap: 6px; }
.ci-label { font-size: 12px; color: #6b7280; }
.ci-value { font-size: 14px; font-weight: 600; }
.dlg-contract { font-size: 13px; color: #374151; background: #f5f7fa; border-radius: 6px; padding: 8px 12px; }
</style>
