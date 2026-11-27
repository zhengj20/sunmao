<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="发标编号" clearable style="width: 160px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="招采立项">
            <el-select v-model="query.procProjectId" placeholder="全部立项" clearable filterable style="width: 210px">
              <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="发标中" value="PUBLISHED" />
              <el-option label="答疑中" value="CLARIFY" />
              <el-option label="已开标" value="OPENED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增发标</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="tenderCode" label="发标编号" width="135" show-overflow-tooltip />
        <el-table-column label="招采立项(标的)" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.procProjectTitle || projLabel(row.procProjectId) }}</template>
        </el-table-column>
        <el-table-column prop="docVersion" label="标书版本" width="85" align="center" />
        <el-table-column label="保证金(元)" width="115" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.depositAmount) }}</template>
        </el-table-column>
        <el-table-column label="保证金退还" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.depositAmount" :type="row.depositRefunded === 1 ? 'success' : 'info'" size="small">
              {{ row.depositRefunded === 1 ? '已退还' : '未退还' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="发标时间" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="回标截止" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="['PUBLISHED', 'CLARIFY'].includes(row.status)" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="['PUBLISHED', 'CLARIFY'].includes(row.status)" link type="warning" size="small" @click="openClarify(row)">答疑登记</el-button>
            <el-button v-if="['PUBLISHED', 'CLARIFY'].includes(row.status)" link type="success" size="small" @click="onOpen(row)">开标</el-button>
            <el-button v-if="row.status === 'OPENED'" link type="info" size="small" @click="onClose(row)">关闭</el-button>
            <el-button v-if="row.depositAmount && row.depositRefunded !== 1 && row.status !== 'CLOSED'" link type="info" size="small" @click="onRefund(row)">退保证金</el-button>
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
    <el-dialog v-model="form.visible" :title="form.id ? '编辑发标' : '新增发标'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-form-item label="招采立项" prop="procProjectId">
          <el-select v-model="form.data.procProjectId" placeholder="请选择入围完成的立项" filterable style="width: 100%">
            <el-option v-for="p in addableProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标书版本" prop="docVersion">
              <el-input v-model="form.data.docVersion" placeholder="如 V1.0" maxlength="16" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保证金(元)">
              <el-input-number v-model="form.data.depositAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发标时间" prop="publishTime">
              <el-date-picker v-model="form.data.publishTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="发标时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回标截止" prop="deadline">
              <el-date-picker v-model="form.data.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="回标截止时间" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="保存后即发布发标通知（供应商门户可下载标书文件包）；迟于截止时间回标将拒收" />
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存并发布</el-button>
      </template>
    </el-dialog>

    <!-- 答疑登记 -->
    <el-dialog v-model="clarify.visible" title="答疑澄清纪要" width="640px" destroy-on-close>
      <div v-if="clarify.notes.length" class="block-title" style="margin-top: 0">已登记答疑（{{ clarify.notes.length }} 条）</div>
      <el-timeline v-if="clarify.notes.length" style="margin-bottom: 14px">
        <el-timeline-item v-for="(n, i) in clarify.notes" :key="i" :timestamp="fmtTime(n.clarifyAt)">
          <div style="font-weight: 600">问：{{ n.question }}</div>
          <div style="color: #185fa5">答：{{ n.answer }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无答疑记录" :image-size="70" style="padding: 6px 0 14px" />
      <el-divider style="margin: 10px 0" />
      <el-form ref="clarifyRef" :model="clarify.data" :rules="clarifyRules" label-width="70px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="clarify.data.question" type="textarea" :rows="2" placeholder="供应商提出的疑问" maxlength="300" />
        </el-form-item>
        <el-form-item label="澄清答复" prop="answer">
          <el-input v-model="clarify.data.answer" type="textarea" :rows="2" placeholder="澄清内容将全员同步至所有入围供应商" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clarify.visible = false">关闭</el-button>
        <el-button type="primary" :loading="clarify.saving" @click="onClarifySave">登记纪要</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="发标详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="发标编号">{{ detail.data.tenderCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="招采立项(标的)" :span="2">{{ detail.data.procProjectTitle || projLabel(detail.data.procProjectId) }}</el-descriptions-item>
        <el-descriptions-item label="标书版本">{{ detail.data.docVersion }}</el-descriptions-item>
        <el-descriptions-item label="保证金(元)"><span class="num">{{ money(detail.data.depositAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="发标时间">{{ fmtTime(detail.data.publishTime) }}</el-descriptions-item>
        <el-descriptions-item label="回标截止">{{ fmtTime(detail.data.deadline) }}</el-descriptions-item>
        <el-descriptions-item label="保证金退还">{{ detail.data.depositRefunded === 1 ? '已退还' : '未退还' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">答疑纪要（{{ detail.notes.length }} 条）</div>
      <el-timeline v-if="detail.notes.length">
        <el-timeline-item v-for="(n, i) in detail.notes" :key="i" :timestamp="fmtTime(n.clarifyAt)">
          <div style="font-weight: 600">问：{{ n.question }}</div>
          <div style="color: #185fa5">答：{{ n.answer }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无答疑记录" :image-size="70" />
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { money, fmtTime } from '../../../utils/format'
import {
  tenderPage, tenderDetail, tenderCreate, tenderUpdate,
  tenderOpen, tenderClose, tenderRefund, tenderClarify, projPage,
  statusMeta
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', procProjectId: null, status: '' })

const procProjects = ref([])
const projLabel = id => {
  const p = procProjects.value.find(p => p.id === id)
  return p ? `${p.projectCode} ${p.title}` : (id ? '#' + id : '-')
}
/** 可发标的立项：已立项/入围中/发标中（未定标） */
const addableProjects = computed(() => procProjects.value.filter(p => ['APPROVED', 'ENTRY', 'TENDERING'].includes(p.status)))

const load = async () => {
  loading.value = true
  try {
    const page = await tenderPage(query)
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

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = {
  procProjectId: [{ required: true, message: '请选择招采立项', trigger: 'change' }],
  docVersion: [{ required: true, message: '请输入标书版本', trigger: 'blur' }],
  publishTime: [{ required: true, message: '请选择发标时间', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择回标截止时间', trigger: 'change' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    procProjectId: row?.procProjectId ?? null, docVersion: row?.docVersion || 'V1.0',
    depositAmount: row?.depositAmount ?? undefined,
    publishTime: row?.publishTime || dayjs().format('YYYY-MM-DD HH:mm:ss'),
    deadline: row?.deadline || null, remark: row?.remark || ''
  }
  form.visible = true
}
const onSave = async () => {
  await formRef.value.validate()
  if (form.data.deadline && form.data.publishTime && dayjs(form.data.deadline).isBefore(dayjs(form.data.publishTime))) {
    return ElMessage.warning('回标截止时间不能早于发标时间')
  }
  form.saving = true
  try {
    if (form.id) await tenderUpdate({ id: form.id, ...form.data })
    else await tenderCreate(form.data)
    ElMessage.success(form.id ? '保存成功' : '发标成功，已通知供应商')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onOpen = async row => {
  const overdue = row.deadline && dayjs().isAfter(dayjs(row.deadline))
  await ElMessageBox.confirm(
    overdue ? `确认对 ${row.tenderCode} 开标？开标后不可再修改回标。` : `当前未到回标截止时间（${fmtTime(row.deadline)}），确认提前开标？`,
    '开标', { type: 'warning' }
  )
  await tenderOpen(row.id)
  ElMessage.success('已开标')
  load()
}
const onClose = async row => {
  await ElMessageBox.confirm(`确认关闭标段 ${row.tenderCode}？关闭后本标段流程归档。`, '关闭标段', { type: 'warning' })
  await tenderClose(row.id)
  ElMessage.success('已关闭')
  load()
}
const onRefund = async row => {
  await ElMessageBox.confirm(`确认退还 ${row.tenderCode} 的投标保证金 ${money(row.depositAmount)} 元？`, '退保证金', { type: 'warning' })
  await tenderRefund(row.id)
  ElMessage.success('保证金已退还')
  load()
}

// ============ 答疑 ============
const clarifyRef = ref()
const clarify = reactive({ visible: false, saving: false, id: null, notes: [], data: {} })
const clarifyRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入澄清答复', trigger: 'blur' }]
}
const openClarify = row => {
  clarify.id = row.id
  clarify.notes = row.clarifyNotes || []
  clarify.data = { question: '', answer: '' }
  clarify.visible = true
}
const onClarifySave = async () => {
  await clarifyRef.value.validate()
  clarify.saving = true
  try {
    await tenderClarify(clarify.id, clarify.data)
    ElMessage.success('答疑纪要已登记，已同步全部入围供应商')
    clarify.data = { question: '', answer: '' }
    const d = await tenderDetail(clarify.id)
    clarify.notes = d?.clarifyNotes || []
    load()
  } finally {
    clarify.saving = false
  }
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {}, notes: [] })
const openDetail = async row => {
  const d = await tenderDetail(row.id)
  detail.data = d || row
  detail.notes = d?.clarifyNotes || []
  detail.visible = true
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
