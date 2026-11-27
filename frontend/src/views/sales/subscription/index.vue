<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="v in Object.keys(SUB_STATUS)" :key="v" :label="SUB_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="认购单号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建认购</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="subNo" label="认购单号" width="150" show-overflow-tooltip />
        <el-table-column prop="roomCode" label="房号" width="110" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || ('客户#' + row.customerId) }}</template>
        </el-table-column>
        <el-table-column label="定金(元)" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.deposit) }}</span></template>
        </el-table-column>
        <el-table-column label="期望签约" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.expectedSignDate) }}</template>
        </el-table-column>
        <el-table-column label="置业顾问" width="110" align="center">
          <template #default="{ row }">{{ row.agentId ? '#' + row.agentId : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联合同" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractId ? '#' + row.contractId : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" size="small" @click="openConvert(row)">转签约</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" size="small" @click="openRefund(row)">退定</el-button>
            <el-button v-if="row.status === 'REJECTED'" link type="warning" size="small" @click="openConvert(row)">转签约</el-button>
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

    <!-- 新建认购 -->
    <el-dialog v-model="formVisible" title="新建认购" width="600px">
      <el-form label-width="90px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房源" required>
          <el-select v-model="form.roomId" filterable style="width: 100%" :loading="roomOptionsLoading" placeholder="选择可售房源" @change="onRoomChange">
            <el-option v-for="r in sellableRooms" :key="r.roomId" :label="r.roomCode + '（' + (r.layout || '') + '）'" :value="r.roomId" />
          </el-select>
          <div class="hint" v-if="form.roomInfo">
            面积 {{ money(form.roomInfo.area) }}㎡ · 表价 <span class="num">{{ money(form.roomInfo.tablePrice) }}</span> 元
          </div>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable remote reserve-keyword :remote-method="searchCustomer"
                     :loading="customerLoading" style="width: 100%" placeholder="输入客户名称搜索">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="定金(元)"><el-input-number v-model="form.deposit" :controls="false" :min="0" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望签约"><el-date-picker v-model="form.expectedSignDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="置业顾问">
          <el-input-number v-model="form.agentId" :controls="false" :min="1" placeholder="顾问用户ID（选填）" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转签约 -->
    <el-dialog v-model="convertVisible" title="转签约" width="520px">
      <el-form label-width="100px">
        <el-form-item label="成交总价(元)" required>
          <el-input-number v-model="convert.contractPrice" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式" required>
          <el-select v-model="convert.payMethod" style="width: 100%">
            <el-option v-for="m in PAY_METHODS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="签约日期">
          <el-date-picker v-model="convert.signedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="convert.saving" @click="onConvert">确认转签约</el-button>
      </template>
    </el-dialog>

    <!-- 退定 -->
    <el-dialog v-model="refundVisible" title="退定" width="480px">
      <el-form label-width="80px">
        <el-form-item label="退定原因" required>
          <el-input v-model="refund.reason" type="textarea" :rows="3" maxlength="300" placeholder="请填写退定原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="refund.saving" @click="onRefund">确认退定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, fetchCustomerOptions, fetchRoomOptions,
  subPage, subCreate, subSubmit, subConvert, subRefund,
  SUB_STATUS, PAY_METHODS, statusMeta
} from '../../../api/sales'

const meta = s => statusMeta(s, SUB_STATUS)

const projects = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, kw: '', current: 1, size: 20 })

// 表单数据源
const customers = ref([])
const customerLoading = ref(false)
const allRooms = ref([])
const roomOptionsLoading = ref(false)
const sellableRooms = computed(() => allRooms.value.filter(r => r.status === 'SELLABLE'))

const loadList = async () => {
  loading.value = true
  try {
    const page = await subPage({
      current: query.current, size: query.size, projectId: query.projectId,
      status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.kw = ''; onSearch() }

const searchCustomer = async kw => {
  customerLoading.value = true
  try { customers.value = await fetchCustomerOptions(kw || '') } finally { customerLoading.value = false }
}

// 新建
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ projectId: null, roomId: null, customerId: null, deposit: null, agentId: null, expectedSignDate: null })
const roomInfo = ref(null)
const onRoomChange = id => { roomInfo.value = allRooms.value.find(r => r.roomId === id) || null }

const openForm = async () => {
  Object.assign(form, { projectId: query.projectId, roomId: null, customerId: null, deposit: null, agentId: null, expectedSignDate: null })
  roomInfo.value = null
  formVisible.value = true
  await Promise.all([
    (async () => { customerLoading.value = true; try { customers.value = await fetchCustomerOptions() } finally { customerLoading.value = false } })(),
    (async () => { if (form.projectId) { roomOptionsLoading.value = true; try { allRooms.value = await fetchRoomOptions(form.projectId) } finally { roomOptionsLoading.value = false } } })()
  ])
}
const onSave = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (!form.roomId) return ElMessage.warning('请选择房源')
  if (!form.customerId) return ElMessage.warning('请选择客户')
  saving.value = true
  try {
    await subCreate({
      projectId: form.projectId, roomId: form.roomId, customerId: form.customerId,
      deposit: form.deposit ?? 0, agentId: form.agentId || undefined, expectedSignDate: form.expectedSignDate
    })
    ElMessage.success('认购已创建（草稿，房源已预占）')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 提交审批
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交认购 ${row.subNo} 进入审批流？`, '提交审批', { type: 'warning' })
  await subSubmit(row.id)
  ElMessage.success('已提交审批')
  loadList()
}

// 转签约
const convertVisible = ref(false)
const convert = reactive({ saving: false, row: null, contractPrice: null, payMethod: 'MORTGAGE', signedDate: null })
const openConvert = row => {
  convert.row = row
  convert.contractPrice = roomTablePrice(row)
  convert.payMethod = 'MORTGAGE'
  convert.signedDate = null
  convertVisible.value = true
}
const roomTablePrice = row => {
  const r = allRooms.value.find(x => x.roomId === row.roomId)
  return r?.tablePrice ?? null
}
const onConvert = async () => {
  if (convert.contractPrice == null) return ElMessage.warning('请填写成交总价')
  convert.saving = true
  try {
    const contractId = await subConvert(convert.row.id, {
      contractPrice: convert.contractPrice, payMethod: convert.payMethod, signedDate: convert.signedDate
    })
    ElMessage.success(`转签约成功，合同草稿已生成（合同#${contractId}），请前往签约管理完善`)
    convertVisible.value = false
    loadList()
  } finally {
    convert.saving = false
  }
}

// 退定
const refundVisible = ref(false)
const refund = reactive({ saving: false, row: null, reason: '' })
const openRefund = row => { refund.row = row; refund.reason = ''; refundVisible.value = true }
const onRefund = async () => {
  if (!refund.reason) return ElMessage.warning('请填写退定原因')
  refund.saving = true
  try {
    const refundId = await subRefund(refund.row.id, refund.reason)
    ElMessage.success(`退定已发起审批（退换单#${refundId}）`)
    refundVisible.value = false
    loadList()
  } finally {
    refund.saving = false
  }
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
