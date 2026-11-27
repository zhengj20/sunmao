<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="(label, key) in MSTATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="事项编号/名称" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增事项</el-button>
      </div>
    </el-card>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      事项申请是签证 / 设计变更的上游"申请与背景"：先立一个事项（说明为什么变更），再在该事项下登记具体的工程签证与设计变更。<b>一个事项可关联多笔变更</b>，并在此汇总预估金额与已确认金额。
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="matterCode" label="事项编号" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="事项名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="matterTypeColor(row.matterType)" effect="plain">{{ matterTypeLabel(row.matterType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonCategory" label="原因归类" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reasonCategory || '-' }}</template>
        </el-table-column>
        <el-table-column label="预估金额（元）" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.estimateTotal) }}</span></template>
        </el-table-column>
        <el-table-column label="申请日期" width="110" align="center">
          <template #default="{ row }">{{ row.applyDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColorM(row.status)" size="small">{{ MSTATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onApprove(row)">批准</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status !== 'CLOSED'" link type="info" size="small" @click="onClose(row)">关闭</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑事项申请' : '新增事项申请'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="事项名称" required>
          <el-input v-model="form.title" maxlength="100" placeholder="如：1#楼地下室底板加厚专项" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="事项类型">
              <el-select v-model="form.matterType" style="width: 100%">
                <el-option v-for="(label, key) in MTYPE" :key="key" :label="label" :value="key" />
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
            <el-form-item label="关联合同">
              <el-select v-model="form.contractId" clearable filterable style="width: 100%">
                <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估金额">
              <el-input-number v-model="form.estimateTotal" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请日期">
              <el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="事项背景" required>
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" placeholder="说明变更起因：为什么发生、涉及范围、预期影响等" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉：事项 + 关联变更 + 汇总 -->
    <el-drawer v-model="detailVisible" title="事项申请详情" size="60%">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="事项编号">{{ detail.matter.matterCode }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ matterTypeLabel(detail.matter.matterType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColorM(detail.matter.status)" size="small">{{ MSTATUS[detail.matter.status] || detail.matter.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="事项名称" :span="3">{{ detail.matter.title }}</el-descriptions-item>
          <el-descriptions-item label="原因归类">{{ detail.matter.reasonCategory || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请日期">{{ detail.matter.applyDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="预估金额（元）"><span class="num">{{ money(detail.matter.estimateTotal) }}</span></el-descriptions-item>
          <el-descriptions-item label="事项背景" :span="3">{{ detail.matter.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="sum-strip">
          <div class="sum-item"><div class="sum-label">关联变更</div><div class="sum-value">{{ detail.changeCount }} 笔</div></div>
          <div class="sum-item"><div class="sum-label">变更估价合计（元）</div><div class="sum-value">{{ money(detail.estimateSum) }}</div></div>
          <div class="sum-item"><div class="sum-label">已确认净额（元）</div><div class="sum-value sum-green">{{ money(detail.confirmedSum) }}</div></div>
          <div class="sum-item"><div class="sum-label">预估（元）</div><div class="sum-value">{{ money(detail.matter.estimateTotal) }}</div></div>
        </div>

        <div class="detail-sub">
          <div class="sub-head">
            <span>该事项下的签证 / 设计变更</span>
            <el-button size="small" type="primary" plain @click="gotoChange">去变更管理新增</el-button>
          </div>
          <el-table :data="detail.changes" border size="small" empty-text="该事项下暂无变更，请到「变更管理」新增并选择本事项">
            <el-table-column prop="changeCode" label="变更编号" width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ dictLabel('change_type', row.changeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="变更标题" min-width="160" show-overflow-tooltip />
            <el-table-column label="估价（元）" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.estimateAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="确认（元）" width="110" align="right">
              <template #default="{ row }"><span class="num">{{ money(row.confirmedAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="changeStatusColor(row.status)" size="small">{{ CSTATUS[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { money, dictLabel } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions,
  fetchMatters, fetchMatterSummary, createMatter, updateMatter, deleteMatter, approveMatter, closeMatter
} from '../../../api/cost'

const router = useRouter()

const MTYPE = { ZH: '综合', SJBG: '设计变更类', GCQZ: '工程签证类' }
const MSTATUS = { DRAFT: '草稿', APPROVED: '已批准', CLOSED: '已关闭' }
const CSTATUS = {
  ESTIMATING: '估价中', ESTIMATE_APPROVING: '估价审批中', CONFIRMING: '待确认',
  APPROVING: '确认审批中', CONFIRMED: '已确认', REJECTED: '已驳回'
}
const REASONS = ['地质条件变化', '产品优化', '设计错误', '施工错误', '政策法规调整', '成本优化', '其他']

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const contracts = ref([])
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, status: null, kw: '', current: 1, size: 20 })

const formVisible = ref(false)
const form = reactive({
  id: null, title: '', matterType: 'ZH', reasonCategory: '', contractId: null,
  estimateTotal: null, applyDate: '', description: '', remark: ''
})
const detailVisible = ref(false)
const detail = ref(null)

const matterTypeLabel = t => MTYPE[t] || t || '-'
const matterTypeColor = t => (t === 'SJBG' ? 'warning' : t === 'GCQZ' ? 'success' : 'primary')
const statusColorM = s => (s === 'APPROVED' ? 'success' : s === 'CLOSED' ? 'info' : 'warning')
const changeStatusColor = s => (s === 'CONFIRMED' ? 'success' : s === 'REJECTED' ? 'danger' : s === 'ESTIMATING' ? 'warning' : 'primary')

const loadList = async () => {
  loading.value = true
  try {
    const page = await fetchMatters(query)
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.status = null; query.kw = ''; onSearch() }

const loadContracts = async () => {
  contracts.value = query.projectId ? ((await fetchContractOptions({ projectId: query.projectId })) || []) : []
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, title: row.title, matterType: row.matterType || 'ZH',
      reasonCategory: row.reasonCategory || '', contractId: row.contractId,
      estimateTotal: row.estimateTotal, applyDate: row.applyDate || '',
      description: row.description || '', remark: row.remark || ''
    })
  } else {
    Object.assign(form, { id: null, title: '', matterType: 'ZH', reasonCategory: '', contractId: null, estimateTotal: null, applyDate: '', description: '', remark: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.title) return ElMessage.warning('请填写事项名称')
  if (!form.description) return ElMessage.warning('请填写事项背景')
  saving.value = true
  try {
    if (form.id) {
      await updateMatter({ ...form })
      ElMessage.success('事项已更新')
    } else {
      await createMatter({ ...form, projectId: query.projectId })
      ElMessage.success('事项已创建')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const openDetail = async row => {
  detail.value = null
  detailVisible.value = true
  detail.value = await fetchMatterSummary(row.id)
}

const onApprove = async row => {
  await ElMessageBox.confirm(`批准事项【${row.title}】？批准后该事项正式生效，可在其下登记变更。`, '提示', { type: 'info' })
  await approveMatter(row.id)
  ElMessage.success('已批准')
  await loadList()
}

const onClose = async row => {
  await ElMessageBox.confirm(`关闭事项【${row.title}】？`, '提示', { type: 'warning' })
  await closeMatter(row.id)
  ElMessage.success('已关闭')
  await loadList()
}

const onDelete = async row => {
  await ElMessageBox.confirm(`删除事项【${row.title}】？`, '提示', { type: 'warning' })
  await deleteMatter(row.id)
  ElMessage.success('已删除')
  await loadList()
}

const gotoChange = () => {
  detailVisible.value = false
  router.push('/cost/change')
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
.num { font-variant-numeric: tabular-nums; }
.sum-strip { display: flex; gap: 12px; margin: 16px 0 12px; }
.sum-item { flex: 1; background: #f7f8fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 16px; }
.sum-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.sum-value { font-size: 18px; font-weight: 600; color: #303133; }
.sum-green { color: #529b2e; }
.detail-sub { margin-top: 8px; }
.sub-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-weight: 600; color: #303133; }
</style>
