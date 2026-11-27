<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="需求编号/标题" clearable style="width: 190px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 170px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
              <el-option v-for="(m, k) in statusOptions" :key="k" :label="m[0]" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item label="紧急程度">
            <el-select v-model="query.urgency" placeholder="全部" clearable style="width: 110px">
              <el-option label="普通" value="NORMAL" />
              <el-option label="紧急" value="URGENT" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增需求</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="reqCode" label="需求编号" width="140" show-overflow-tooltip />
        <el-table-column label="项目" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="需求标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="需求部门" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column label="采购品类" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || catName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="预估金额(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.estimateAmount) }}</template>
        </el-table-column>
        <el-table-column label="期望时间" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtDate(row.expectDate) }}</template>
        </el-table-column>
        <el-table-column label="紧急程度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.urgency)[1]" size="small">{{ statusMeta(row.urgency)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="集采覆盖" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.coveredByFramework === 1" type="success" size="small">已覆盖</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="['DRAFT', 'APPROVED'].includes(row.status)" link type="danger" size="small" @click="onVoid(row)">作废</el-button>
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑采购需求' : '新增采购需求'" width="680px" destroy-on-close>
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
            <el-form-item label="需求标题" prop="title">
              <el-input v-model="form.data.title" placeholder="如：一期机电安装总承包需求" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成本科目">
              <el-cascader v-model="form.data.accountId" :options="accounts" :props="cascaderProps" placeholder="请选择科目" clearable style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购品类">
              <el-cascader v-model="form.data.categoryId" :options="categories" :props="cascaderProps" placeholder="请选择品类" clearable style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估金额" prop="estimateAmount">
              <el-input-number v-model="form.data.estimateAmount" :min="0" :precision="2" :controls="false" placeholder="元" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望时间">
              <el-date-picker v-model="form.data.expectDate" type="date" value-format="YYYY-MM-DD" placeholder="期望到货/开工" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急程度">
              <el-radio-group v-model="form.data.urgency">
                <el-radio value="NORMAL">普通</el-radio>
                <el-radio value="URGENT">紧急</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="集采覆盖">
              <el-switch v-model="form.data.coveredByFramework" :active-value="1" :inactive-value="0" active-text="已有战略集采协议覆盖" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="需求描述">
              <el-input v-model="form.data.description" type="textarea" :rows="3" placeholder="采购范围/技术要求等" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="采购需求详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="需求编号">{{ detail.data.reqCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务项目">{{ detail.data.projectName || projectName(detail.data.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="需求部门">{{ detail.data.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求标题" :span="2">{{ detail.data.title }}</el-descriptions-item>
        <el-descriptions-item label="成本科目">{{ detail.data.accountName || accountName(detail.data.accountId) }}</el-descriptions-item>
        <el-descriptions-item label="采购品类">{{ detail.data.categoryName || catName(detail.data.categoryId) }}</el-descriptions-item>
        <el-descriptions-item label="预估金额(元)"><span class="num">{{ money(detail.data.estimateAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="期望时间">{{ fmtDate(detail.data.expectDate) }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <el-tag :type="statusMeta(detail.data.urgency)[1]" size="small">{{ statusMeta(detail.data.urgency)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="集采覆盖">{{ detail.data.coveredByFramework === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.data.createdByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求描述" :span="2">{{ detail.data.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">审批记录</div>
      <el-timeline v-if="approval.tasks.length">
        <el-timeline-item
          v-for="t in approval.tasks"
          :key="t.id"
          :timestamp="fmtTime(t.actedAt || t.arrivedAt)"
          :type="taskMeta(t.status)[1] === 'danger' ? 'danger' : taskMeta(t.status)[1] === 'success' ? 'success' : taskMeta(t.status)[1] === 'warning' ? 'warning' : 'info'"
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
import { money, fmtDate, fmtTime } from '../../../utils/format'
import {
  reqPage, reqDetail, reqCreate, reqUpdate, reqSubmit, reqVoid,
  fetchProjectOptions, fetchPurchaseCategories, fetchCostAccounts,
  statusMeta, TASK_META, fetchApprovalTrail
} from '../../../api/proc'

const taskMeta = s => TASK_META[s] || [s || '-', 'info']
const statusOptions = {
  DRAFT: ['草稿', 'info'], APPROVING: ['审批中', 'warning'], APPROVED: ['已通过', 'success'],
  MERGED: ['已并入计划', 'info'], REJECTED: ['已驳回', 'danger'], VOIDED: ['已作废', 'info']
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, status: '', urgency: '' })

const projects = ref([])
const categories = ref([])
const accounts = ref([])
const catMap = ref({})
const accountMap = ref({})
const cascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }

const flattenTree = (nodes, out = {}) => {
  (nodes || []).forEach(n => { out[n.id] = n.name; if (n.children?.length) flattenTree(n.children, out) })
  return out
}
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'
const catName = id => catMap.value[id] || '-'
const accountName = id => accountMap.value[id] || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await reqPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.status = ''; query.urgency = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  estimateAmount: [{ required: true, message: '请输入预估金额', trigger: 'blur' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    projectId: row?.projectId ?? null, title: row?.title || '', accountId: row?.accountId ?? null,
    categoryId: row?.categoryId ?? null, estimateAmount: row?.estimateAmount ?? undefined,
    expectDate: row?.expectDate || null, urgency: row?.urgency || 'NORMAL',
    coveredByFramework: row?.coveredByFramework ?? 0, description: row?.description || '', remark: row?.remark || ''
  }
  form.visible = true
}
const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    if (form.id) await reqUpdate({ id: form.id, ...form.data })
    else await reqCreate(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交需求 ${row.reqCode} 进入审批流？`, '提交审批', { type: 'warning' })
  await reqSubmit(row.id)
  ElMessage.success('已提交审批')
  load()
}
const onVoid = async row => {
  await ElMessageBox.confirm(`确认作废需求 ${row.reqCode}？作废后不可恢复。`, '作废', { type: 'warning' })
  await reqVoid(row.id)
  ElMessage.success('已作废')
  load()
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const approval = reactive({ tasks: [] })
const openDetail = async row => {
  detail.data = await reqDetail(row.id) || row
  detail.visible = true
  approval.tasks = []
  fetchApprovalTrail('PROC_REQUIREMENT', row.id).then(r => { approval.tasks = r.tasks }).catch(() => {})
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
  fetchPurchaseCategories().then(rs => {
    categories.value = rs || []
    catMap.value = flattenTree(rs)
  }).catch(() => {})
  fetchCostAccounts().then(rs => {
    accounts.value = rs || []
    accountMap.value = flattenTree(rs)
  }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
