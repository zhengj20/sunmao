<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(REFUND_STATUS)" :key="v" :label="REFUND_STATUS[v][0]" :value="v" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(REFUND_TYPE)" :key="v" :label="REFUND_TYPE[v][0]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">发起退换</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="refundNo" label="退换单号" width="150" show-overflow-tooltip />
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.contractId ? 'danger' : 'warning'" size="small">{{ row.contractId ? '退房' : '退定' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合同/认购" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractId ? (row.contractNo || ('合同#' + row.contractId)) : ('认购#' + row.subscriptionId) }}</template>
        </el-table-column>
        <el-table-column prop="roomCode" label="房号" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.roomCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="退还金额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.refundAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
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

    <!-- 发起退换 -->
    <el-dialog v-model="formVisible" title="发起退换" width="520px">
      <el-form label-width="90px">
        <el-form-item label="退换类型" required>
          <el-radio-group v-model="form.mode">
            <el-radio value="contract">退房（退签约合同）</el-radio>
            <el-radio value="sub">退定（退认购）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'contract'" label="合同ID" required>
          <el-input-number v-model="form.contractId" :controls="false" :min="1" placeholder="生效/网签合同ID" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="认购ID" required>
          <el-input-number v-model="form.subscriptionId" :controls="false" :min="1" placeholder="生效状态认购ID" style="width: 100%" />
        </el-form-item>
        <el-form-item label="退换原因" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="300" placeholder="请填写退换原因" />
        </el-form-item>
        <el-form-item label="退还金额(元)">
          <el-input-number v-model="form.refundAmount" :controls="false" :min="0" :precision="2" placeholder="留空则按合同/认购金额自动带出" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="退换详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="退换单号">{{ detail.refundNo }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.contractId ? '退房' : '退定' }}</el-descriptions-item>
        <el-descriptions-item label="合同号">{{ detail.contractNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="房号">{{ detail.roomCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退还金额"><span class="num">{{ money(detail.refundAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="原因类别">{{ detail.reasonCategory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="meta(detail.status)[1]" size="small">{{ meta(detail.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ detail.reason || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchProjectOptions, refundPage, refundCreate, refundSubmit, refundDetail,
  REFUND_STATUS, REFUND_TYPE, statusMeta
} from '../../../api/sales'

const meta = s => statusMeta(s, REFUND_STATUS)

const projects = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, type: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await refundPage({
      current: query.current, size: query.size, projectId: query.projectId,
      status: query.status || undefined, type: query.type || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.type = null; onSearch() }

// 发起退换
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ mode: 'contract', contractId: null, subscriptionId: null, reason: '', refundAmount: null })
const openForm = () => {
  Object.assign(form, { mode: 'contract', contractId: null, subscriptionId: null, reason: '', refundAmount: null })
  formVisible.value = true
}
const onSave = async () => {
  if (form.mode === 'contract' && !form.contractId) return ElMessage.warning('请填写合同ID')
  if (form.mode === 'sub' && !form.subscriptionId) return ElMessage.warning('请填写认购ID')
  if (!form.reason) return ElMessage.warning('请填写退换原因')
  saving.value = true
  try {
    const payload = {
      contractId: form.mode === 'contract' ? form.contractId : null,
      subscriptionId: form.mode === 'sub' ? form.subscriptionId : null,
      reason: form.reason, refundAmount: form.refundAmount ?? undefined
    }
    const id = await refundCreate(payload)
    ElMessage.success(`退换单已创建（草稿，单号待生成），ID：${id}`)
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// 提交审批
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交退换单 ${row.refundNo} 进入审批流？`, '提交审批', { type: 'warning' })
  await refundSubmit(row.id)
  ElMessage.success('已提交审批')
  loadList()
}

// 详情
const detailVisible = ref(false)
const detail = ref(null)
const onDetail = async row => {
  detail.value = await refundDetail(row.id)
  detailVisible.value = true
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList()
  } else {
    loadList()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
</style>
