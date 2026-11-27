<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.contractId" placeholder="关联合同" clearable filterable style="width: 240px" @change="onContractChange">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="query.changeType" placeholder="变更类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in DICTS.change_type" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="变更编号/标题" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增变更</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">变更单数（张）</div>
          <div class="value">{{ summary.count ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">估价总额（元）</div>
          <div class="value">{{ money(summary.estimateAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">确认金额累计（元）</div>
          <div class="value">{{ money(summary.confirmedAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">净增加（确认-扣款，元）</div>
          <div class="value">{{ money(netIncrease) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="changeRateWarn" type="warning" :closable="false" show-icon class="rate-warn">
      <template #title>
        变更率预警：当前关联合同变更率 <b>{{ changeRateData.changeRate }}%</b>，已超过预警阈值 <b>{{ changeRateData.warnRate }}%</b>
      </template>
    </el-alert>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="16">
        <el-card shadow="never" class="table-card">
          <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="changeCode" label="变更编号" width="170" show-overflow-tooltip />
        <el-table-column prop="title" label="变更标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contractName" label="关联合同" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractName || '无合同变更' }}</template>
        </el-table-column>
        <el-table-column label="所属事项" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.matterId" class="matter-tag">{{ matterCodeOf(row) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dictLabel('change_type', row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonCategory" label="原因归类" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reasonCategory || '-' }}</template>
        </el-table-column>
        <el-table-column label="估价金额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.estimateAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="确认金额（元）" width="120" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.confirmedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="扣款（元）" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.deductAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="净影响（元）" width="120" align="right">
          <template #default="{ row }">
            <span class="num" :style="netOf(row) > 0 ? 'color:#c45656' : netOf(row) < 0 ? 'color:#529b2e' : ''">{{ money(netOf(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'ESTIMATING' || row.status === 'REJECTED'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'ESTIMATING' || (row.status === 'REJECTED' && !row.confirmedAmount)" link type="success" size="small" @click="onSubmitEstimate(row)">提交估价</el-button>
            <el-button v-if="row.status === 'CONFIRMING' || (row.status === 'REJECTED' && row.confirmedAmount)" link type="success" size="small" @click="openConfirm(row)">确认</el-button>
          </template>
        </el-table-column>
      </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="label">原因归类统计</div>
          <div class="reason-stats">
            <div v-for="(count, reason) in reasonStats" :key="reason" class="reason-item">
              <span class="reason-name">{{ reason || '未归类' }}</span>
              <span class="reason-count">{{ count }} 单</span>
            </div>
            <el-empty v-if="Object.keys(reasonStats).length === 0" description="暂无数据" :image-size="40" />
          </div>
        </el-card>
      </el-col>
    </el-row>
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
    <el-drawer v-model="detailVisible" title="变更详情" size="46%">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="变更编号">{{ detail.changeCode }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ dictLabel('change_type', detail.changeType) }}</el-descriptions-item>
          <el-descriptions-item label="变更标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="关联合同" :span="2">{{ detail.contractName || '无合同变更' }}</el-descriptions-item>
          <el-descriptions-item label="原因归类">{{ detail.reasonCategory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否有合同">{{ detail.requireContract ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="估价金额（元）"><span class="num">{{ money(detail.estimateAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="确认金额（元）"><span class="num">{{ money(detail.confirmedAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="扣款金额（元）"><span class="num">{{ money(detail.deductAmount) }}</span></el-descriptions-item>
          <el-descriptions-item label="净影响（元）"><span class="num">{{ money(netOf(detail)) }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColor(detail.status)" size="small">{{ STATUS[detail.status] || detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑变更单' : '新增变更单'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="变更标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="如：1#楼地下室底板加厚签证" />
        </el-form-item>
        <el-form-item label="所属事项">
          <el-select v-model="form.matterId" clearable filterable placeholder="该签证/设计变更由哪个事项申请引起（可空）" style="width: 100%">
            <el-option v-for="m in matters" :key="m.id" :label="m.matterCode + ' · ' + m.title" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="变更类型" required>
              <el-select v-model="form.changeType" style="width: 100%">
                <el-option v-for="(label, key) in DICTS.change_type" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联合同">
              <el-select v-model="form.contractId" clearable filterable style="width: 100%">
                <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因归类">
              <el-select v-model="form.reasonCategory" allow-create filterable default-first-option style="width: 100%">
                <el-option v-for="r in REASONS" :key="r" :label="r" :value="r" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="估价金额">
              <el-input-number v-model="form.estimateAmount" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="变更说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 确认金额 -->
    <el-dialog v-model="confirmVisible" title="变更确认登记" width="440px">
      <el-alert type="info" :closable="false" title="二阶段确认：登记最终确认金额与扣款金额，确认后计入合同变更累计与动态成本" style="margin-bottom: 14px" />
      <el-form :model="confirmForm" label-width="90px">
        <el-form-item label="确认金额" required>
          <el-input-number v-model="confirmForm.confirmedAmount" :controls="false" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="扣款金额">
          <el-input-number v-model="confirmForm.deductAmount" :controls="false" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onConfirm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime, statusColor, dictLabel, DICTS } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions,
  fetchChanges, fetchChangeSummary, fetchChangeDetail,
  createChange, updateChange, confirmChange, fetchContractChangeRate,
  fetchMatters
} from '../../../api/cost'

const STATUS = {
  ESTIMATING: '估价中', ESTIMATE_APPROVING: '估价审批中', CONFIRMING: '待确认',
  APPROVING: '确认审批中', CONFIRMED: '已确认', REJECTED: '已驳回'
}
const REASONS = ['地质条件变化', '产品优化', '设计错误', '施工错误', '政策法规调整', '成本优化', '现场签证', '其他']

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const contracts = ref([])
const matters = ref([])
const matterMap = computed(() => Object.fromEntries(matters.value.map(m => [m.id, m])))
const rows = ref([])
const total = ref(0)
const summary = ref({})
const query = reactive({ projectId: null, contractId: null, changeType: null, status: null, kw: '', current: 1, size: 20 })

const detailVisible = ref(false)
const detail = ref(null)
const formVisible = ref(false)
const form = reactive({ id: null, title: '', changeType: 'GCQZ', contractId: null, matterId: null, reasonCategory: '', estimateAmount: null, description: '' })
const matterCodeOf = row => (matterMap.value[row.matterId] || {}).matterCode || (row.matterId ? `事项#${row.matterId}` : '')
const confirmVisible = ref(false)
const confirmForm = reactive({ id: null, confirmedAmount: null, deductAmount: 0 })

const changeRateData = ref(null)
const changeRateWarn = computed(() => changeRateData.value?.warn)

const netIncrease = computed(() => (Number(summary.value.confirmedAmount) || 0) - (Number(summary.value.deductAmount) || 0))
const netOf = row => (row.confirmedAmount != null ? (Number(row.confirmedAmount) || 0) - (Number(row.deductAmount) || 0) : null)

const reasonStats = computed(() => {
  const stats = {}
  rows.value.forEach(row => {
    const reason = row.reasonCategory || '未归类'
    stats[reason] = (stats[reason] || 0) + 1
  })
  return stats
})

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchChanges(query),
      fetchChangeSummary({ projectId: query.projectId, contractId: query.contractId })
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
  query.changeType = null
  query.status = null
  query.kw = ''
  onSearch()
}

const loadContracts = async () => {
  contracts.value = query.projectId ? ((await fetchContractOptions({ projectId: query.projectId })) || []) : []
}

const onProjectChange = async () => {
  query.contractId = null
  changeRateData.value = null
  await loadContracts()
  onSearch()
}

const onContractChange = async contractId => {
  changeRateData.value = null
  if (contractId) {
    try {
      changeRateData.value = await fetchContractChangeRate(contractId)
    } catch { /* 忽略 */ }
  }
  onSearch()
}

const openDetail = async row => {
  detail.value = row
  detailVisible.value = true
  detail.value = await fetchChangeDetail(row.id)
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, changeType: row.changeType || 'GCQZ',
      contractId: row.contractId, matterId: row.matterId, reasonCategory: row.reasonCategory || '',
      estimateAmount: row.estimateAmount, description: row.description || ''
    })
  } else {
    Object.assign(form, { id: null, title: '', changeType: 'GCQZ', contractId: null, matterId: null, reasonCategory: '', estimateAmount: null, description: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.title) return ElMessage.warning('请填写变更标题')
  saving.value = true
  try {
    if (form.id) {
      await updateChange({ ...form, projectId: query.projectId })
      ElMessage.success('变更单已更新')
    } else {
      await createChange({ ...form, projectId: query.projectId })
      ElMessage.success('变更单已创建，进入估价阶段')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const openConfirm = row => {
  Object.assign(confirmForm, {
    id: row.id,
    confirmedAmount: row.confirmedAmount ?? row.estimateAmount ?? null,
    deductAmount: row.deductAmount ?? 0
  })
  confirmVisible.value = true
}

const onConfirm = async () => {
  if (confirmForm.confirmedAmount == null) return ElMessage.warning('请填写确认金额')
  saving.value = true
  try {
    await confirmChange(confirmForm.id, { confirmedAmount: confirmForm.confirmedAmount, deductAmount: confirmForm.deductAmount })
    ElMessage.success('已确认，金额回写合同变更累计')
    confirmVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onSubmitEstimate = async row => {
  await ElMessageBox.confirm('提交后将进入变更估价审批流程，确认提交？', '提示', { type: 'warning' })
  await submitChange(row.id)
  ElMessage.success('已提交估价审批')
  await loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) query.projectId = projects.value[0].id
  await loadContracts()
  matters.value = (await fetchMatters({ projectId: query.projectId, current: 1, size: 200 }).catch(() => null))?.records || []
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.rate-warn { margin-bottom: 12px; }
.reason-stats { padding: 10px 0; }
.reason-item { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #f0f0f0; }
.reason-item:last-child { border-bottom: none; }
.reason-name { color: #606266; }
.reason-count { color: #409eff; font-weight: 600; }
.matter-tag { color: #67c23a; background: #f0f9eb; border: 1px solid #e1f3d8; padding: 1px 8px; border-radius: 4px; font-size: 12px; }
.muted { color: #a0a4ab; }
</style>
