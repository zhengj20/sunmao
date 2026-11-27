<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="委托事项/供应商" clearable style="width: 180px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 160px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="委托理由">
            <el-select v-model="query.reason" placeholder="全部" clearable style="width: 110px">
              <el-option v-for="r in DIRECT_REASONS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="审批中" value="APPROVING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增委托</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="项目" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="委托事项" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="190" show-overflow-tooltip />
        <el-table-column label="委托金额(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="directReason" label="委托理由" width="90" align="center" />
        <el-table-column prop="reasonDesc" label="理由说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑直接委托' : '新增直接委托'" width="620px" destroy-on-close>
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
            <el-form-item label="委托事项" prop="title">
              <el-input v-model="form.data.title" placeholder="如：桩基检测委托" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.data.supplierId" placeholder="选择合格供应商" filterable style="width: 100%" @change="onSupplierChange">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="委托金额" prop="amount">
              <el-input-number v-model="form.data.amount" :min="0" :precision="2" :controls="false" placeholder="元" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="委托理由" prop="directReason">
              <el-select v-model="form.data.directReason" placeholder="选择理由分类" style="width: 100%">
                <el-option v-for="r in DIRECT_REASONS" :key="r" :label="r" :value="r" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="理由说明" prop="reasonDesc">
          <el-input v-model="form.data.reasonDesc" type="textarea" :rows="3" placeholder="如：本地区具备资质的机构仅两家，另一家排期冲突" maxlength="300" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-alert type="warning" :closable="false" title="直接委托属阳光采购重点监控事项：金额越高审批层级越高，将自动计入阳光度报表（直接委托占比）" />
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="直接委托详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="业务项目">{{ detail.data.projectName || projectName(detail.data.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="委托事项" :span="2">{{ detail.data.title }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ detail.data.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="委托金额(元)"><span class="num">{{ money(detail.data.amount) }}</span></el-descriptions-item>
        <el-descriptions-item label="委托理由">{{ detail.data.directReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="理由说明" :span="2">{{ detail.data.reasonDesc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

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
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  directPage, directDetail, directCreate, directUpdate, directSubmit, directVoid,
  fetchProjectOptions, fetchSupplierOptions,
  statusMeta, TASK_META, DIRECT_REASONS, fetchApprovalTrail
} from '../../../api/proc'

const taskMeta = s => TASK_META[s] || [s || '-', 'info']
const timelineType = s => ({ success: 'success', danger: 'danger', warning: 'warning' }[taskMeta(s)[1]] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, reason: '', status: '' })

const projects = ref([])
const suppliers = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await directPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.reason = ''; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入委托事项', trigger: 'blur' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  amount: [{ required: true, message: '请输入委托金额', trigger: 'blur' }],
  directReason: [{ required: true, message: '请选择委托理由', trigger: 'change' }],
  reasonDesc: [{ required: true, message: '请填写理由说明', trigger: 'blur' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    projectId: row?.projectId ?? null, title: row?.title || '',
    supplierId: row?.supplierId ?? null, supplierName: row?.supplierName || '',
    amount: row?.amount ?? undefined, directReason: row?.directReason || '',
    reasonDesc: row?.reasonDesc || '', remark: row?.remark || ''
  }
  form.visible = true
}
const onSupplierChange = sid => {
  form.data.supplierName = suppliers.value.find(s => s.id === sid)?.name || ''
}
const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    if (form.id) await directUpdate({ id: form.id, ...form.data })
    else await directCreate(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交直接委托【${row.title}】进入审批流？`, '提交审批', { type: 'warning' })
  await directSubmit(row.id)
  ElMessage.success('已提交审批（按金额分级路由）')
  load()
}
const onVoid = async row => {
  await ElMessageBox.confirm(`确认作废直接委托【${row.title}】？`, '作废', { type: 'warning' })
  await directVoid(row.id)
  ElMessage.success('已作废')
  load()
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const approval = reactive({ tasks: [] })
const openDetail = async row => {
  detail.data = await directDetail(row.id) || row
  detail.visible = true
  approval.tasks = []
  fetchApprovalTrail('PROC_DIRECT', row.id).then(r => { approval.tasks = r.tasks }).catch(() => {})
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
  fetchSupplierOptions().then(rs => { suppliers.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
