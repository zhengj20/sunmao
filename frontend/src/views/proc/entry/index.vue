<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="供应商名称" clearable style="width: 170px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="招采立项">
            <el-select v-model="query.procProjectId" placeholder="全部立项" clearable filterable style="width: 200px">
              <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="资格预审">
            <el-select v-model="query.precheck" placeholder="全部" clearable style="width: 100px">
              <el-option label="通过" value="PASSED" />
              <el-option label="未通过" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item label="考察结论">
            <el-select v-model="query.conclusion" placeholder="全部" clearable style="width: 100px">
              <el-option label="优秀" value="GOOD" />
              <el-option label="合格" value="PASS" />
              <el-option label="不合格" value="BAD" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增入围供应商</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="招采立项" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.procProjectTitle || projLabel(row.procProjectId) }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="200" show-overflow-tooltip />
        <el-table-column label="资格预审" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.precheckResult" :type="statusMeta(row.precheckResult)[1]" size="small">{{ statusMeta(row.precheckResult)[0] }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="precheckRemark" label="预审备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="考察评分" width="90" align="right" class="num">
          <template #default="{ row }">{{ row.inspectionScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="考察结论" width="85" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.inspectionConclusion" :type="statusMeta(row.inspectionConclusion)[1]" size="small">{{ statusMeta(row.inspectionConclusion)[0] }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="入围/备选" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isCandidate === 1 ? 'success' : 'info'" size="small">{{ row.isCandidate === 1 ? '入围' : '备选' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="openInspect(row)">考察登记</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 新增/编辑（支持批量选择供应商） -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑入围供应商' : '新增入围供应商'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="90px">
        <el-form-item label="招采立项" prop="procProjectId">
          <el-select v-model="form.data.procProjectId" placeholder="请选择已通过审批的立项" filterable style="width: 100%">
            <el-option v-for="p in addableProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="supplierIds">
          <el-select
            v-model="form.supplierIds"
            placeholder="支持多选批量入围"
            multiple
            filterable
            :disabled="!!form.id"
            style="width: 100%"
          >
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入围类型">
          <el-radio-group v-model="form.data.isCandidate">
            <el-radio :value="1">正式入围</el-radio>
            <el-radio :value="0">备选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert type="info" :closable="false" title="规则：招标项目入围供应商（含备选）应 ≥ 3 家；黑名单供应商已被过滤" style="margin-bottom: 6px" />
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 考察登记 -->
    <el-dialog v-model="inspect.visible" :title="`考察登记 - ${inspect.data.supplierName || ''}`" width="520px" destroy-on-close>
      <el-form ref="inspectRef" :model="inspect.data" label-width="90px">
        <el-form-item label="资格预审">
          <el-radio-group v-model="inspect.data.precheckResult">
            <el-radio value="PASSED">通过</el-radio>
            <el-radio value="FAILED">未通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预审备注">
          <el-input v-model="inspect.data.precheckRemark" type="textarea" :rows="2" placeholder="证照有效性/业绩/在建项目核查情况" maxlength="200" />
        </el-form-item>
        <el-form-item label="考察评分">
          <el-input-number v-model="inspect.data.inspectionScore" :min="0" :max="100" :precision="1" :controls="false" style="width: 180px" />
          <span style="margin-left: 8px; color: #6b7280">0-100 分（实地考察评分表汇总）</span>
        </el-form-item>
        <el-form-item label="考察结论">
          <el-radio-group v-model="inspect.data.inspectionConclusion">
            <el-radio value="GOOD">优秀</el-radio>
            <el-radio value="PASS">合格</el-radio>
            <el-radio value="BAD">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspect.visible = false">取消</el-button>
        <el-button type="primary" :loading="inspect.saving" @click="onInspectSave">保存考察记录</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="入围供应商详情" size="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="招采立项" :span="2">{{ detail.data.procProjectTitle || projLabel(detail.data.procProjectId) }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ detail.data.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="资格预审">
          <el-tag v-if="detail.data.precheckResult" :type="statusMeta(detail.data.precheckResult)[1]" size="small">{{ statusMeta(detail.data.precheckResult)[0] }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="预审备注">{{ detail.data.precheckRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="考察评分"><span class="num">{{ detail.data.inspectionScore ?? '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="考察结论">
          <el-tag v-if="detail.data.inspectionConclusion" :type="statusMeta(detail.data.inspectionConclusion)[1]" size="small">{{ statusMeta(detail.data.inspectionConclusion)[0] }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="入围类型">{{ detail.data.isCandidate === 1 ? '正式入围' : '备选' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="block-title">同立项入围名单（{{ entryList.length }} 家）</div>
      <el-table :data="entryList" border size="small">
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="预审" width="70" align="center">
          <template #default="{ row }">{{ row.precheckResult ? statusMeta(row.precheckResult)[0] : '-' }}</template>
        </el-table-column>
        <el-table-column label="考察评分" width="85" align="right" class="num">
          <template #default="{ row }">{{ row.inspectionScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="考察结论" width="80" align="center">
          <template #default="{ row }">{{ row.inspectionConclusion ? statusMeta(row.inspectionConclusion)[0] : '-' }}</template>
        </el-table-column>
        <el-table-column label="入围/备选" width="85" align="center">
          <template #default="{ row }">{{ row.isCandidate === 1 ? '入围' : '备选' }}</template>
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
      <el-empty v-else description="暂无审批记录（未提交）" :image-size="70" />
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  entryPage, entryDetail, entryCreate, entryUpdate, entryInspect, entrySubmit, entryDelete,
  projPage, fetchSupplierOptions,
  statusMeta, TASK_META, fetchApprovalTrail
} from '../../../api/proc'

const taskMeta = s => TASK_META[s] || [s || '-', 'info']
const timelineType = s => ({ success: 'success', danger: 'danger', warning: 'warning' }[taskMeta(s)[1]] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', procProjectId: null, precheck: '', conclusion: '' })

const procProjects = ref([])
const suppliers = ref([])
const projLabel = id => {
  const p = procProjects.value.find(p => p.id === id)
  return p ? `${p.projectCode} ${p.title}` : (id ? '#' + id : '-')
}
/** 入围登记可选立项：已通过审批 / 入围中 */
const addableProjects = computed(() => procProjects.value.filter(p => ['APPROVED', 'ENTRY'].includes(p.status)))

const load = async () => {
  loading.value = true
  try {
    const page = await entryPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.procProjectId = null; query.precheck = ''; query.conclusion = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, supplierIds: [], data: {} })
const rules = {
  procProjectId: [{ required: true, message: '请选择招采立项', trigger: 'change' }],
  supplierIds: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.supplierIds = row?.supplierId ? [row.supplierId] : []
  form.data = {
    procProjectId: row?.procProjectId ?? null, supplierId: row?.supplierId ?? null,
    supplierName: row?.supplierName || '', isCandidate: row?.isCandidate ?? 1
  }
  form.visible = true
}
const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    if (form.id) {
      await entryUpdate({ id: form.id, ...form.data })
    } else {
      for (const sid of form.supplierIds) {
        const s = suppliers.value.find(x => x.id === sid)
        await entryCreate({ ...form.data, supplierId: sid, supplierName: s?.name || '' })
      }
    }
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认将 ${row.supplierName} 提交入围名单审批？`, '提交审批', { type: 'warning' })
  await entrySubmit(row.procProjectId)
  ElMessage.success('已提交审批')
  load()
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认从入围名单移除 ${row.supplierName}？`, '删除', { type: 'warning' })
  await entryDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 考察登记 ============
const inspectRef = ref()
const inspect = reactive({ visible: false, saving: false, id: null, data: {} })
const openInspect = row => {
  inspect.id = row.id
  inspect.data = {
    precheckResult: row.precheckResult || 'PASSED', precheckRemark: row.precheckRemark || '',
    inspectionScore: row.inspectionScore ?? undefined, inspectionConclusion: row.inspectionConclusion || 'PASS',
    supplierName: row.supplierName
  }
  inspect.visible = true
}
const onInspectSave = async () => {
  inspect.saving = true
  try {
    await entryInspect(inspect.id, inspect.data)
    ElMessage.success('考察记录已保存')
    inspect.visible = false
    load()
  } finally {
    inspect.saving = false
  }
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const approval = reactive({ tasks: [] })
const entryList = ref([])
const openDetail = async row => {
  detail.data = await entryDetail(row.id) || row
  detail.visible = true
  approval.tasks = []
  entryList.value = []
  fetchApprovalTrail('PROC_ENTRY', row.id).then(r => { approval.tasks = r.tasks }).catch(() => {})
  entryPage({ current: 1, size: 100, procProjectId: row.procProjectId }).then(p => { entryList.value = p?.records || [] }).catch(() => {})
}

onMounted(async () => {
  load()
  projPage({ current: 1, size: 200 }).then(p => { procProjects.value = p?.records || [] }).catch(() => {})
  fetchSupplierOptions().then(rs => { suppliers.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
