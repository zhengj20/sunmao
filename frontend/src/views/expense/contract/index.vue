<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(CONTRACT_STATUS)" :key="v" :label="CONTRACT_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.supplier" placeholder="供应商" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-input v-model="query.kw" placeholder="合同编号/名称" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建合同</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="contractNo" label="合同编号" width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplier" label="供应商" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplier || '-' }}</template>
        </el-table-column>
        <el-table-column label="合同金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="已付（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :style="paidWarn(row) ? 'color:#c45656' : ''">{{ money(row.paidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="未付（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount - Number(row.paidAmount || 0)) }}</span></template>
        </el-table-column>
        <el-table-column label="付款进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="progress(row)" :stroke-width="8" :status="progress(row) >= 100 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column label="签订日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.signDate) }}</template>
        </el-table-column>
        <el-table-column label="起止" width="180" align="center">
          <template #default="{ row }">{{ fmtDate(row.startDate) }} ~ {{ fmtDate(row.endDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPayDlg(row)">详情</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" size="small" @click="onStatus(row, 'TERMINATED')">终止</el-button>
            <el-button v-if="row.status === 'TERMINATED'" link type="success" size="small" @click="onStatus(row, 'ACTIVE')">恢复</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="info" size="small" @click="onStatus(row, 'EXPIRED')">到期</el-button>
            <el-button v-if="Number(row.paidAmount) === 0" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 新建合同 -->
    <el-dialog v-model="formVisible" title="新建费用合同" width="600px">
      <el-form label-width="100px">
        <el-form-item label="合同名称" required>
          <el-input v-model="form.name" maxlength="200" placeholder="如：办公场地租赁合同 / 物业服务费合同" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplier" maxlength="128" placeholder="对方单位（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同金额" required>
              <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="签订日期">
              <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="到期日期">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" placeholder="付款条款等说明" />
        </el-form-item>
        <el-form-item>
          <div class="hint">合同登记后，在「费用付款」生成付款单时关联本合同，实付登记自动累计已付金额（超合同额阻断）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 合同详情 -->
    <el-dialog v-model="payDlgVisible" :title="`合同详情 - ${payDlg.row?.name || ''}`" width="780px" @open="loadPayments">
      <el-descriptions :column="3" border size="small" class="detail-desc">
        <el-descriptions-item label="合同编号">{{ payDlg.row?.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="合同名称" :span="2">{{ payDlg.row?.name }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ payDlg.row?.supplier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="meta(payDlg.row?.status)[1]" size="small">{{ meta(payDlg.row?.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="合同金额"><span class="num">{{ money(payDlg.row?.amount) }}</span></el-descriptions-item>
        <el-descriptions-item label="已付金额"><span class="num">{{ money(payDlg.row?.paidAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="未付金额"><span class="num">{{ money(remain()) }}</span></el-descriptions-item>
        <el-descriptions-item label="付款进度" :span="3">
          <el-progress :percentage="progress(payDlg.row)" :stroke-width="10" :status="progress(payDlg.row) >= 100 ? 'success' : ''" />
        </el-descriptions-item>
        <el-descriptions-item label="签订日期">{{ fmtDate(payDlg.row?.signDate) }}</el-descriptions-item>
        <el-descriptions-item label="生效至到期" :span="2">{{ fmtDate(payDlg.row?.startDate) }} ~ {{ fmtDate(payDlg.row?.endDate) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ payDlg.row?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="pay-section-title">付款管理</div>
      <div v-if="canNewPay" class="pay-new">
        <span class="pay-new-label">新建付款申请：</span>
        <el-input-number v-model="newPay.amount" :controls="false" :min="0.01" :max="Number(remain())" :precision="2" style="width: 200px" />
        <el-button type="primary" size="small" :loading="newPay.saving" @click="onNewPay">生成付款申请</el-button>
      </div>
      <div v-else class="pay-new"><span class="hint">合同已付满或不可付款，仅可查看付款记录。</span></div>

      <el-table :data="payDlg.pays" border size="small" class="pay-table">
        <el-table-column prop="payNo" label="付款单号" width="150" show-overflow-tooltip />
        <el-table-column label="金额（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="付款日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.paidDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag :type="payMeta(row.status)[1]" size="small">{{ payMeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="openPay(row)">实付登记</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 实付登记 -->
    <el-dialog v-model="payVisible" title="实付登记" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="付款单号">
          <span>{{ pay.row?.payNo }}</span>
        </el-form-item>
        <el-form-item label="实付金额" required>
          <el-input-number v-model="pay.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="pay.paidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <div class="hint">实付后付款单置「已付」，合同已付金额自动累计（超合同额阻断）。</div>
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  contractPage, contractCreate, contractDelete,
  contractPayments, contractPaymentCreate,
  paymentPay, PAY_STATUS,
  CONTRACT_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, CONTRACT_STATUS)
const progress = row => {
  const amt = Number(row.amount) || 0
  if (amt <= 0) return 0
  return Math.min(100, Math.round((Number(row.paidAmount) || 0) / amt * 100))
}
const paidWarn = row => Number(row.paidAmount) >= (Number(row.amount) || 0) && (Number(row.amount) || 0) > 0

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ status: null, supplier: '', kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await contractPage({
      current: query.current, size: query.size,
      status: query.status || undefined, supplier: query.supplier || undefined,
      kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.supplier = ''; query.kw = ''; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', supplier: '', amount: null, signDate: null, startDate: null, endDate: null, remark: '' })
const openForm = () => {
  Object.assign(form, { name: '', supplier: '', amount: null, signDate: null, startDate: null, endDate: null, remark: '' })
  formVisible.value = true
}
const onSave = async () => {
  if (!form.name.trim()) return ElMessage.warning('请填写合同名称')
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写合同金额')
  saving.value = true
  try {
    await contractCreate({
      name: form.name, supplier: form.supplier || undefined, amount: form.amount,
      signDate: form.signDate || undefined, startDate: form.startDate || undefined,
      endDate: form.endDate || undefined, remark: form.remark || undefined
    })
    ElMessage.success('合同已创建')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

const onStatus = async (row, status) => {
  const label = { ACTIVE: '恢复为生效', EXPIRED: '标记已到期', TERMINATED: '终止' }[status]
  await ElMessageBox.confirm(`确认将合同 ${row.contractNo} ${label}？`, '提示', { type: 'warning' })
  await contractStatus(row.id, status)
  ElMessage.success('状态已更新')
  loadList()
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除合同 ${row.contractNo}？`, '提示', { type: 'warning' })
  await contractDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

// ================= 合同付款 =================
const payMeta = s => statusMeta(s, PAY_STATUS)
const payDlgVisible = ref(false)
const payDlg = reactive({ row: null, pays: [] })
const remain = () => (Number(payDlg.row?.amount) || 0) - (Number(payDlg.row?.paidAmount) || 0)
const canNewPay = computed(() => payDlg.row?.status === 'ACTIVE' && remain() > 0.009)
const newPay = reactive({ amount: null, saving: false })

const openPayDlg = row => {
  payDlg.row = row
  payDlg.pays = []
  newPay.amount = remain()
  payDlgVisible.value = true
}
const loadPayments = async () => {
  payDlg.pays = (await contractPayments(payDlg.row.id).catch(() => [])) || []
}
const onNewPay = async () => {
  if (!newPay.amount || Number(newPay.amount) <= 0) return ElMessage.warning('请填写付款金额')
  newPay.saving = true
  try {
    await contractPaymentCreate(payDlg.row.id, { amount: newPay.amount })
    ElMessage.success('付款申请已生成（待付）')
    newPay.amount = null
    await Promise.all([loadPayments(), loadList()])
  } finally {
    newPay.saving = false
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
    ElMessage.success('实付登记成功，合同已付金额已更新')
    payVisible.value = false
    await Promise.all([loadPayments(), loadList()])
  } finally {
    pay.saving = false
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
.detail-desc { margin-bottom: 18px; }
.pay-section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px; padding-left: 8px; border-left: 3px solid var(--el-color-primary); }
.pay-new { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.pay-new-label { font-size: 13px; color: #606266; }
.pay-table { margin-top: 4px; }
</style>
