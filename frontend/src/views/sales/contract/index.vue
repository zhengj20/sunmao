<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="v in Object.keys(CONTRACT_STATUS)" :key="v" :label="CONTRACT_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="合同号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建签约</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="contractNo" label="合同号" width="150" show-overflow-tooltip />
        <el-table-column prop="roomCode" label="房号" width="110" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || ('客户#' + row.customerId) }}</template>
        </el-table-column>
        <el-table-column label="成交总价(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.contractPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="付款方式" width="100" align="center">
          <template #default="{ row }">{{ payMethodLabel(row.payMethod) }}</template>
        </el-table-column>
        <el-table-column label="折扣" width="90" align="right">
          <template #default="{ row }">{{ pct(row.discountRate) }}</template>
        </el-table-column>
        <el-table-column label="回款率" width="90" align="right">
          <template #default="{ row }">{{ row.collectionRate != null ? row.collectionRate + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="签约日" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.signedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" size="small" @click="onSpecialPrice(row)">特价审批</el-button>
            <el-button v-if="row.status === 'ACTIVE' || row.status === 'FILED'" link type="danger" size="small" @click="openRefund(row)">退房</el-button>
            <el-button link type="primary" size="small" @click="onDetail(row)">详情</el-button>
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

    <!-- 新建签约 -->
    <el-dialog v-model="formVisible" title="新建签约" width="620px">
      <el-form label-width="90px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="房源" required>
          <el-select v-model="form.roomId" filterable style="width: 100%" :loading="roomOptionsLoading" placeholder="选择可售/已认购房源" @change="onRoomChange">
            <el-option v-for="r in signableRooms" :key="r.roomId" :label="r.roomCode + '（' + (r.layout || '') + '）'" :value="r.roomId" />
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
            <el-form-item label="成交总价(元)" required>
              <el-input-number v-model="form.contractPrice" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款方式" required>
              <el-select v-model="form.payMethod" style="width: 100%">
                <el-option v-for="m in PAY_METHODS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="签约日期">
          <el-date-picker v-model="form.signedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成交折扣">
          <el-input :model-value="form.contractPrice && form.roomInfo?.tablePrice ? pct(Number(form.contractPrice) / Number(form.roomInfo.tablePrice)) : '提交后由系统按 成交总价/表价 计算'" readonly placeholder="自动计算" style="width: 100%" />
          <div class="hint">折扣低于置业顾问权限上限时，须先发起特价审批方可提交（R3 刚性）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 退房 -->
    <el-dialog v-model="refundVisible" title="退房" width="480px">
      <el-form label-width="80px">
        <el-form-item label="退房原因" required>
          <el-input v-model="refund.reason" type="textarea" :rows="3" maxlength="300" placeholder="请填写退房原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="refund.saving" @click="onRefund">确认退房</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="合同详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="合同号">{{ detail.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="房号">{{ detail.roomCode }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName || ('客户#' + detail.customerId) }}</el-descriptions-item>
        <el-descriptions-item label="成交总价"><span class="num">{{ money(detail.contractPrice) }}</span></el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ payMethodLabel(detail.payMethod) }}</el-descriptions-item>
        <el-descriptions-item label="成交折扣">{{ pct(detail.discountRate) }}</el-descriptions-item>
        <el-descriptions-item label="回款率">{{ detail.collectionRate != null ? detail.collectionRate + '%' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="签约日期">{{ fmtDate(detail.signedAt) }}</el-descriptions-item>
        <el-descriptions-item label="来源认购">{{ detail.subscriptionId ? '#' + detail.subscriptionId : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="meta(detail.status)[1]" size="small">{{ meta(detail.status)[0] }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, fetchCustomerOptions, fetchRoomOptions,
  contractPage, contractCreate, contractSubmit, contractSpecialPrice, contractRefund, contractDetail,
  CONTRACT_STATUS, PAY_METHODS, payMethodLabel, statusMeta
} from '../../../api/sales'

const meta = s => statusMeta(s, CONTRACT_STATUS)
const pct = v => (v === null || v === undefined || v === '' ? '-' : (Number(v) * 100).toFixed(1) + '%')

const projects = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, kw: '', current: 1, size: 20 })

const customers = ref([])
const customerLoading = ref(false)
const allRooms = ref([])
const roomOptionsLoading = ref(false)
const signableRooms = computed(() => allRooms.value.filter(r => ['SELLABLE', 'LOCKED', 'SUBSCRIBED'].includes(r.status)))

const loadList = async () => {
  loading.value = true
  try {
    const page = await contractPage({
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
const form = reactive({ projectId: null, roomId: null, customerId: null, contractPrice: null, payMethod: 'MORTGAGE', signedAt: null })
const roomInfo = ref(null)
const onRoomChange = id => { roomInfo.value = allRooms.value.find(r => r.roomId === id) || null }

const openForm = async () => {
  Object.assign(form, { projectId: query.projectId, roomId: null, customerId: null, contractPrice: null, payMethod: 'MORTGAGE', signedAt: null })
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
  if (!form.contractPrice || Number(form.contractPrice) <= 0) return ElMessage.warning('请填写成交总价')
  saving.value = true
  try {
    await contractCreate({
      projectId: form.projectId, roomId: form.roomId, customerId: form.customerId,
      contractPrice: form.contractPrice, payMethod: form.payMethod, signedAt: form.signedAt || undefined
    })
    ElMessage.success('签约已创建（草稿，折扣率已自动计算）')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 提交审批（R3 折扣刚性校验）：后端超权限折扣会抛错，request.js 已自动 ElMessage.error 展示，此处不吞错
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交合同 ${row.contractNo} 进入签约审批流？`, '提交审批', { type: 'warning' })
  try {
    await contractSubmit(row.id)
    ElMessage.success('已提交审批')
    loadList()
  } catch (e) {
    // 后端错误（如 R3 折扣超权限）已由 request.js 提示，保持阻断
  }
}

// 特价审批（超权限折扣前置）
const onSpecialPrice = async row => {
  await ElMessageBox.confirm(`确认对合同 ${row.contractNo} 发起特价房审批？`, '特价审批', { type: 'warning' })
  await contractSpecialPrice(row.id)
  ElMessage.success('特价房审批已发起')
  loadList()
}

// 退房
const refundVisible = ref(false)
const refund = reactive({ saving: false, row: null, reason: '' })
const openRefund = row => { refund.row = row; refund.reason = ''; refundVisible.value = true }
const onRefund = async () => {
  if (!refund.reason) return ElMessage.warning('请填写退房原因')
  refund.saving = true
  try {
    const refundId = await contractRefund(refund.row.id, refund.reason)
    ElMessage.success(`退房已发起审批（退换单#${refundId}）`)
    refundVisible.value = false
    loadList()
  } finally {
    refund.saving = false
  }
}

// 详情
const detailVisible = ref(false)
const detail = ref(null)
const onDetail = async row => {
  detail.value = await contractDetail(row.id)
  detailVisible.value = true
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
