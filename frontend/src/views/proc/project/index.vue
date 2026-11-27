<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="立项编号/标的名称" clearable style="width: 180px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 160px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="采购方式">
            <el-select v-model="query.method" placeholder="全部方式" clearable style="width: 130px">
              <el-option v-for="m in PROC_METHODS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
              <el-option v-for="(m, k) in statusOptions" :key="k" :label="m[0]" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新建立项</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="projectCode" label="立项编号" width="135" show-overflow-tooltip />
        <el-table-column label="项目" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标的名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="采购方式" width="100" align="center">
          <template #default="{ row }">{{ methodLabel(row.procureMethod) }}</template>
        </el-table-column>
        <el-table-column label="预算金额(元)" width="135" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.budgetAmount) }}</template>
        </el-table-column>
        <el-table-column prop="fundSource" label="资金来源" width="100" show-overflow-tooltip />
        <el-table-column label="工期(天)" width="80" align="right">
          <template #default="{ row }">{{ row.periodDays ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status, projStatus)[1]" size="small">{{ statusMeta(row.status, projStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="onVoid(row)">作废</el-button>
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑招采立项' : '新增招采立项'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="业务项目" prop="projectId">
              <el-select v-model="form.data.projectId" placeholder="请选择项目" filterable style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标的名称" prop="title">
              <el-input v-model="form.data.title" placeholder="如：一期机电安装总承包招标" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算金额" prop="budgetAmount">
              <el-input-number v-model="form.data.budgetAmount" :min="0" :precision="2" :controls="false" placeholder="元（来源目标成本/合约规划）" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资金来源">
              <el-input v-model="form.data.fundSource" placeholder="如：自有资金/开发贷" maxlength="40" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="关联计划">
              <div style="display: flex; gap: 8px; width: 100%">
                <el-select v-model="form.planId" placeholder="选择已确认采购计划" clearable filterable style="flex: 1" @change="onPlanChange">
                  <el-option v-for="p in plans" :key="p.id" :label="`${p.planCode} ${p.name || ''}`" :value="p.id" />
                </el-select>
                <el-select v-model="form.data.planItemId" placeholder="选择计划明细" clearable style="flex: 1" @change="onItemChange">
                  <el-option v-for="i in planItems" :key="i.id" :label="i.title" :value="i.id" />
                </el-select>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购方式" prop="procureMethod">
              <el-select v-model="form.data.procureMethod" placeholder="请选择采购方式" style="width: 100%">
                <el-option v-for="m in PROC_METHODS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工期(天)">
              <el-input-number v-model="form.data.periodDays" :min="0" :controls="false" placeholder="工期天数" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="methodTip"
          :title="methodTip"
          :type="reasonRequired ? 'warning' : 'info'"
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-form-item v-if="reasonRequired" label="方式选择理由" prop="methodReason">
          <el-input v-model="form.data.methodReason" type="textarea" :rows="2" placeholder="越级选择采购方式，必须填写理由（集团审批重点核查）" maxlength="200" />
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
    <el-drawer v-model="detail.visible" title="招采立项详情" size="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="立项编号">{{ detail.data.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status, projStatus)[1]" size="small">{{ statusMeta(detail.data.status, projStatus)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务项目">{{ detail.data.projectName || projectName(detail.data.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="采购方式">{{ methodLabel(detail.data.procureMethod) }}</el-descriptions-item>
        <el-descriptions-item label="标的名称" :span="2">{{ detail.data.title }}</el-descriptions-item>
        <el-descriptions-item label="预算金额(元)"><span class="num">{{ money(detail.data.budgetAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="资金来源">{{ detail.data.fundSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工期(天)">{{ detail.data.periodDays ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.data.methodReason" label="方式选择理由" :span="2">{{ detail.data.methodReason }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">流程进度</div>
      <el-steps :active="flowStep" align-center finish-status="success">
        <el-step title="立项审批" />
        <el-step title="入围考察" />
        <el-step title="发标答疑" />
        <el-step title="回标评标" />
        <el-step title="定标归档" />
      </el-steps>

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
  projPage, projDetail, projCreate, projUpdate, projSubmit, projVoid,
  planPage, planDetail, fetchProjectOptions,
  statusMeta, TASK_META, PROC_METHODS, methodLabel, fetchApprovalTrail
} from '../../../api/proc'

const taskMeta = s => TASK_META[s] || [s || '-', 'info']
const timelineType = s => ({ success: 'success', danger: 'danger', warning: 'warning' }[taskMeta(s)[1]] || 'info')
const projStatus = { APPROVED: ['已立项', 'success'], CLOSED: ['已归档', 'info'] }
const statusOptions = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'], APPROVED: ['已立项', 'success'],
  ENTRY: ['入围中', ''], TENDERING: ['发标中', ''], EVALUATING: ['评标中', ''],
  AWARDED: ['已定标', 'success'], CLOSED: ['已归档', 'info'], VOIDED: ['已作废', 'info']
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, method: '', status: '' })

const projects = ref([])
const plans = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await projPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.method = ''; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, planId: null, data: {} })
const planItems = ref([])
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入标的名称', trigger: 'blur' }],
  budgetAmount: [{ required: true, message: '请输入预算金额', trigger: 'blur' }],
  procureMethod: [{ required: true, message: '请选择采购方式', trigger: 'change' }]
}
/** 金额-方式匹配建议（可配规则，此处 ≥500万 建议公开招标） */
const reasonRequired = computed(() => ['DIRECT', 'INQUIRY', 'FRAMEWORK'].includes(form.data.procureMethod))
const methodTip = computed(() => {
  const amt = Number(form.data.budgetAmount) || 0
  if (!form.data.procureMethod) return amt >= 5000000 ? '按金额-方式匹配规则：预算 ≥ 500万 建议采用公开招标' : ''
  if (amt >= 5000000 && form.data.procureMethod !== 'PUBLIC_BID') {
    return `预算 ${(amt / 10000).toFixed(2)} 万元 ≥ 500万，规则建议【公开招标】；采用${methodLabel(form.data.procureMethod)}属越级选择，必须填写理由并升级集团审批`
  }
  if (['DIRECT', 'INQUIRY', 'FRAMEWORK'].includes(form.data.procureMethod)) {
    return `选择【${methodLabel(form.data.procureMethod)}】须填写理由（垄断/应急/集采覆盖等），并按金额分级审批`
  }
  return ''
})
const openForm = row => {
  form.id = row?.id || null
  form.planId = null
  planItems.value = []
  form.data = {
    projectId: row?.projectId ?? null, title: row?.title || '', budgetAmount: row?.budgetAmount ?? undefined,
    planItemId: row?.planItemId ?? null, fundSource: row?.fundSource || '',
    procureMethod: row?.procureMethod || '', methodReason: row?.methodReason || '',
    periodDays: row?.periodDays ?? undefined, remark: row?.remark || ''
  }
  form.visible = true
}
const onPlanChange = async planId => {
  form.data.planItemId = null
  planItems.value = []
  if (!planId) return
  const d = await planDetail(planId)
  planItems.value = (d?.items || []).map(i => ({ id: i.id, title: i.title, estimateAmount: i.estimateAmount }))
}
const onItemChange = itemId => {
  const item = planItems.value.find(i => i.id === itemId)
  if (item && !form.data.title) form.data.title = item.title
  if (item && form.data.budgetAmount === undefined) form.data.budgetAmount = item.estimateAmount
}
const onSave = async () => {
  await formRef.value.validate()
  if (reasonRequired.value && !form.data.methodReason) return ElMessage.warning('越级选择采购方式，必须填写方式选择理由')
  form.saving = true
  try {
    if (form.id) await projUpdate({ id: form.id, ...form.data })
    else await projCreate(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交立项 ${row.projectCode} 进入审批流？`, '提交审批', { type: 'warning' })
  await projSubmit(row.id)
  ElMessage.success('已提交审批')
  load()
}
const onVoid = async row => {
  await ElMessageBox.confirm(`确认作废立项 ${row.projectCode}？`, '作废', { type: 'warning' })
  await projVoid(row.id)
  ElMessage.success('已作废')
  load()
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const approval = reactive({ tasks: [] })
const flowStep = computed(() => {
  const map = { DRAFT: 0, APPROVING: 0, REJECTED: 0, APPROVED: 1, ENTRY: 1, TENDERING: 2, EVALUATING: 3, AWARDED: 5, CLOSED: 5 }
  return map[detail.data.status] ?? 0
})
const openDetail = async row => {
  detail.data = await projDetail(row.id) || row
  detail.visible = true
  approval.tasks = []
  fetchApprovalTrail('PROC_PROJECT', row.id).then(r => { approval.tasks = r.tasks }).catch(() => {})
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
  planPage({ current: 1, size: 100, status: 'CONFIRMED' }).then(p => { plans.value = p?.records || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
