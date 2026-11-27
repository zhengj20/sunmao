<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="定标编号/供应商" clearable style="width: 170px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="招采立项">
            <el-select v-model="query.procProjectId" placeholder="全部立项" clearable filterable style="width: 210px">
              <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="审批中" value="APPROVING" />
              <el-option label="已定标" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增定标</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="awardCode" label="定标编号" width="135" show-overflow-tooltip />
        <el-table-column label="招采立项(标的)" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.procProjectTitle || projLabel(row.procProjectId) }}</template>
        </el-table-column>
        <el-table-column prop="winnerSupplierName" label="中标供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="定标价(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.awardAmount) }}</template>
        </el-table-column>
        <el-table-column label="预算对照(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.budgetAmount) }}</template>
        </el-table-column>
        <el-table-column label="偏差(元)" width="120" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="deviationStyle(row.deviation)">{{ fmtDeviation(row.deviation) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="偏差率" width="85" align="right" class="num">
          <template #default="{ row }">{{ row.deviationPct !== null && row.deviationPct !== undefined ? row.deviationPct + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="余量校验" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.controlCheck" :type="statusMeta(row.controlCheck)[1]" size="small">{{ statusMeta(row.controlCheck)[0] }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="中标通知" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.noticeStatus)[1]" size="small">{{ statusMeta(row.noticeStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合同" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.contractGenerated === 1" type="success" size="small">已生成</el-tag>
            <el-tag v-else type="info" size="small">未生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status, awardStatus)[1]" size="small">{{ statusMeta(row.status, awardStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'APPROVED' && row.noticeStatus === 'UNSENT'" link type="warning" size="small" @click="onSendNotice(row)">发中标通知</el-button>
            <el-button v-if="row.status === 'APPROVED' && row.contractGenerated !== 1" link type="success" size="small" @click="onGenerateContract(row)">生成合同</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑定标' : '新增定标'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-form-item label="招采立项" prop="procProjectId">
          <el-select v-model="form.data.procProjectId" placeholder="请选择评标完成的立项" filterable style="width: 100%" @change="onFormProjectChange">
            <el-option v-for="p in addableProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="中标供应商" prop="winnerSupplierId">
          <el-select v-model="form.data.winnerSupplierId" placeholder="从回标供应商中选择定标候选人" filterable style="width: 100%" @change="onWinnerChange">
            <el-option v-for="b in formBids" :key="b.id" :label="`${b.supplierName}（报价 ${money(b.priceAmount)}）`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="定标价(元)" prop="awardAmount">
              <el-input-number v-model="form.data.awardAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算对照(元)">
              <el-input-number v-model="form.data.budgetAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-descriptions v-if="formDeviation !== null" :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="偏差(元)">
            <span :style="deviationStyle(formDeviation)">{{ fmtDeviation(formDeviation) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="偏差率">
            <span :style="deviationStyle(formDeviation)">{{ formDeviationPct }}%</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="form.data.budgetAmount && form.data.awardAmount && form.data.awardAmount > form.data.budgetAmount"
          type="warning" :closable="false" style="margin-bottom: 12px"
          title="定标价超预算：将触发余量校验（超合约规划余量阻断、超目标成本升级集团审批）"
        />
        <el-form-item label="推荐理由" prop="reason">
          <el-input v-model="form.data.reason" type="textarea" :rows="3" placeholder="推荐中标人理由（评分排名/报价/履约能力等）" maxlength="300" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="定标详情" size="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="定标编号">{{ detail.data.awardCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status, awardStatus)[1]" size="small">{{ statusMeta(detail.data.status, awardStatus)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="招采立项(标的)" :span="2">{{ detail.data.procProjectTitle || projLabel(detail.data.procProjectId) }}</el-descriptions-item>
        <el-descriptions-item label="中标供应商" :span="2">{{ detail.data.winnerSupplierName }}</el-descriptions-item>
        <el-descriptions-item label="定标价(元)"><span class="num">{{ money(detail.data.awardAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="预算对照(元)"><span class="num">{{ money(detail.data.budgetAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="偏差(元)"><span :style="deviationStyle(detail.data.deviation)">{{ fmtDeviation(detail.data.deviation) }}</span></el-descriptions-item>
        <el-descriptions-item label="偏差率">{{ detail.data.deviationPct ?? '-' }}%</el-descriptions-item>
        <el-descriptions-item label="余量校验">
          <el-tag v-if="detail.data.controlCheck" :type="statusMeta(detail.data.controlCheck)[1]" size="small">{{ statusMeta(detail.data.controlCheck)[0] }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="中标通知">
          <el-tag :type="statusMeta(detail.data.noticeStatus)[1]" size="small">{{ statusMeta(detail.data.noticeStatus)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生成合同">
          <span v-if="detail.data.contractGenerated === 1">已生成（合同ID：{{ detail.data.contractId || '-' }}）</span>
          <span v-else>未生成</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="推荐理由" :span="2">{{ detail.data.reason || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">候选回标对比</div>
      <el-table :data="detailBids" border size="small">
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="商务报价(元)" width="130" align="right" class="num">
          <template #default="{ row }">
            <span :style="row.supplierName === detail.data.winnerSupplierName ? 'color:#67c23a;font-weight:700' : ''">{{ money(row.priceAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="技术得分" width="85" align="right" class="num">
          <template #default="{ row }">{{ row.techScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="符合性" width="75" align="center">
          <template #default="{ row }">{{ row.complianceResult ? statusMeta(row.complianceResult)[0] : '-' }}</template>
        </el-table-column>
        <el-table-column label="回标状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.bidStatus)[1]" size="small">{{ statusMeta(row.bidStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="block-title">审批记录</div>
      <el-timeline v-if="approval.tasks.length">
        <el-timeline-item
          v-for="t in approval.tasks"
          :key="t.id"
          :timestamp="fmtTime(t.actedAt || t.arrivedAt)"
          :type="timelineType(t.status)"
        >
          <div>
            <el-tag size="small" :type="taskMeta(t.status)[1]">{{ taskMeta(t.status)[0] }}</el-tag>
            <b style="margin: 0 6px">{{ t.nodeName }}</b>
            <span>{{ t.agentName ? `${t.assigneeName}(${t.agentName}代) ` : t.assigneeName + ' ' }}</span>
          </div>
          <div v-if="t.comment" style="color: #6b7280; font-size: 12.5px">意见：{{ t.comment }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审批记录（草稿未提交）" :image-size="70" />
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  awardPage, awardDetail, awardCreate, awardUpdate, awardSubmit, awardSendNotice,
  awardGenerateContract, awardDelete, bidPage, projPage,
  statusMeta, TASK_META, fetchApprovalTrail
} from '../../../api/proc'

const taskMeta = s => TASK_META[s] || [s || '-', 'info']
const timelineType = s => ({ success: 'success', danger: 'danger', warning: 'warning' }[taskMeta(s)[1]] || 'info')
const awardStatus = { APPROVED: ['已定标', 'success'] }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', procProjectId: null, status: '' })

const procProjects = ref([])
const projLabel = id => {
  const p = procProjects.value.find(p => p.id === id)
  return p ? `${p.projectCode} ${p.title}` : (id ? '#' + id : '-')
}
/** 可定标的立项：评标中及以后 */
const addableProjects = computed(() => procProjects.value.filter(p => ['EVALUATING', 'AWARDED'].includes(p.status)))

const load = async () => {
  loading.value = true
  try {
    const page = await awardPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.procProjectId = null; query.status = ''
  load()
}

const fmtDeviation = v => (v === null || v === undefined || v === '' ? '-' : (Number(v) > 0 ? '+' : '') + money(v))
const deviationStyle = v => (Number(v) > 0 ? 'color:#f56c6c;font-weight:600' : Number(v) < 0 ? 'color:#67c23a;font-weight:600' : '')

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const formBids = ref([])
const rules = {
  procProjectId: [{ required: true, message: '请选择招采立项', trigger: 'change' }],
  winnerSupplierId: [{ required: true, message: '请选择中标供应商', trigger: 'change' }],
  awardAmount: [{ required: true, message: '请输入定标价', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入推荐理由', trigger: 'blur' }]
}
const formDeviation = computed(() => {
  const a = Number(form.data.awardAmount), b = Number(form.data.budgetAmount)
  if (!a || !b) return null
  return a - b
})
const formDeviationPct = computed(() => {
  const d = formDeviation.value
  if (d === null) return '-'
  const b = Number(form.data.budgetAmount)
  return ((d / b) * 100).toFixed(2)
})
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    procProjectId: row?.procProjectId ?? null, winnerSupplierId: row?.winnerSupplierId ?? null,
    winnerSupplierName: row?.winnerSupplierName || '', awardAmount: row?.awardAmount ?? undefined,
    budgetAmount: row?.budgetAmount ?? undefined, reason: row?.reason || '', remark: row?.remark || ''
  }
  formBids.value = []
  form.visible = true
  if (form.data.procProjectId) onFormProjectChange()
}
const onFormProjectChange = async () => {
  form.data.winnerSupplierId = null
  form.data.winnerSupplierName = ''
  formBids.value = []
  if (!form.data.procProjectId) return
  const p = await bidPage({ current: 1, size: 100, procProjectId: form.data.procProjectId }).catch(() => null)
  formBids.value = (p?.records || []).filter(b => b.bidStatus !== 'INVALID')
  // 预算对照自动带出立项预算
  const proj = procProjects.value.find(x => x.id === form.data.procProjectId)
  if (proj && form.data.budgetAmount === undefined) form.data.budgetAmount = proj.budgetAmount
}
const onWinnerChange = bidId => {
  const b = formBids.value.find(x => x.id === bidId)
  form.data.winnerSupplierName = b?.supplierName || ''
  if (b?.priceAmount && form.data.awardAmount === undefined) form.data.awardAmount = b.priceAmount
}
const onSave = async () => {
  await formRef.value.validate()
  if (!form.data.winnerSupplierName) return ElMessage.warning('请从中标候选回标中选择供应商')
  form.saving = true
  try {
    if (form.id) await awardUpdate({ id: form.id, ...form.data })
    else await awardCreate(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交定标 ${row.awardCode} 进入审批流？审批通过后招采立项将流转为已定标。`, '提交审批', { type: 'warning' })
  await awardSubmit(row.id)
  ElMessage.success('已提交审批')
  load()
}
const onSendNotice = async row => {
  await ElMessageBox.confirm(`确认向 ${row.winnerSupplierName} 发送中标通知书？`, '中标通知', { type: 'warning' })
  await awardSendNotice(row.id)
  ElMessage.success('中标通知书已发送（供应商门户+短信），落标通知将自动发送')
  load()
}
const onGenerateContract = async row => {
  await ElMessageBox.confirm(`确认按定标结果生成合同草稿？将自动带出中标供应商、定标价等信息。`, '生成合同', { type: 'warning' })
  const r = await awardGenerateContract(row.id)
  ElMessage.success(`合同草稿已生成${r?.contractCode ? `（合同编号：${r.contractCode}）` : ''}，请前往成本管理-合同管理完善条款`)
  load()
  if (detail.visible && detail.data.id === row.id) openDetail(row)
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除定标单 ${row.awardCode}？`, '删除', { type: 'warning' })
  await awardDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const approval = reactive({ tasks: [] })
const detailBids = ref([])
const openDetail = async row => {
  detail.data = await awardDetail(row.id) || row
  detail.visible = true
  approval.tasks = []
  detailBids.value = []
  fetchApprovalTrail('PROC_AWARD', row.id).then(r => { approval.tasks = r.tasks }).catch(() => {})
  bidPage({ current: 1, size: 100, procProjectId: row.procProjectId }).then(p => { detailBids.value = p?.records || [] }).catch(() => {})
}

onMounted(async () => {
  load()
  projPage({ current: 1, size: 200 }).then(p => { procProjects.value = p?.records || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
