<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="计划编号/名称" clearable style="width: 180px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 170px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划月份">
            <el-date-picker v-model="query.planMonth" type="month" value-format="YYYY-MM" placeholder="全部月份" style="width: 130px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已作废" value="VOIDED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增计划</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="planCode" label="计划编号" width="140" show-overflow-tooltip />
        <el-table-column label="项目" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="planMonth" label="计划月份" width="90" align="center" />
        <el-table-column prop="name" label="计划名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="明细数" width="75" align="center">
          <template #default="{ row }">{{ row.itemCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="计划金额(元)" width="140" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
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
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onConfirm(row)">确认</el-button>
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

    <!-- 新增/编辑（含明细子表） -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑采购计划' : '新增采购计划'" width="900px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="业务项目" prop="projectId">
              <el-select v-model="form.data.projectId" placeholder="请选择项目" filterable style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划月份" prop="planMonth">
              <el-date-picker v-model="form.data.planMonth" type="month" value-format="YYYY-MM" placeholder="yyyy-MM" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划名称" prop="name">
              <el-input v-model="form.data.name" placeholder="如：9月采购计划" maxlength="60" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="block-title">
        计划明细
        <el-button type="primary" size="small" icon="Plus" style="margin-left: 10px" @click="openImport">引入采购需求</el-button>
        <el-button size="small" icon="Plus" @click="addItem">手工添加行</el-button>
      </div>
      <el-table :data="form.data.items" border size="small" :max-height="300">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column label="来源需求编号" width="130">
          <template #default="{ row }">{{ row.reqCode || (row.requirementId ? '#' + row.requirementId : '手工') }}</template>
        </el-table-column>
        <el-table-column label="采购内容" min-width="170">
          <template #default="{ row }"><el-input v-model="row.title" placeholder="采购内容" size="small" /></template>
        </el-table-column>
        <el-table-column label="预估金额(元)" width="150" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.estimateAmount" :min="0" :precision="2" :controls="false" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="期望时间" width="135">
          <template #default="{ row }">
            <el-date-picker v-model="row.expectDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="110">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" maxlength="100" /></template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="form.data.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="item-total">
        合计：<span class="num">{{ money(itemsTotal) }}</span> 元（共 {{ form.data.items.length }} 条）
      </div>
      <el-form label-width="90px" style="margin-top: 8px">
        <el-form-item label="备注">
          <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 引入需求 -->
    <el-dialog v-model="importDlg.visible" title="引入已审批采购需求" width="820px" destroy-on-close>
      <el-alert type="info" :closable="false" title="仅显示已审批通过且未并入其他计划的采购需求" style="margin-bottom: 10px" />
      <el-table
        ref="importTableRef"
        :data="importDlg.list"
        v-loading="importDlg.loading"
        border
        size="small"
        :max-height="360"
        @selection-change="sel => (importDlg.selection = sel)"
      >
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column prop="reqCode" label="需求编号" width="130" show-overflow-tooltip />
        <el-table-column prop="title" label="需求标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="预估金额(元)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.estimateAmount) }}</template>
        </el-table-column>
        <el-table-column label="期望时间" width="100">
          <template #default="{ row }">{{ fmtDate(row.expectDate) }}</template>
        </el-table-column>
        <el-table-column label="紧急" width="70" align="center">
          <template #default="{ row }">{{ statusMeta(row.urgency)[0] }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importDlg.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!importDlg.selection.length" @click="onImport">引入 {{ importDlg.selection.length }} 条</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="采购计划详情" size="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="计划编号">{{ detail.data.planCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务项目">{{ detail.data.projectName || projectName(detail.data.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="计划月份">{{ detail.data.planMonth }}</el-descriptions-item>
        <el-descriptions-item label="计划名称" :span="2">{{ detail.data.name }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">计划明细（{{ detail.items.length }} 条）</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column label="来源需求" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reqCode || (row.requirementId ? '#' + row.requirementId : '手工') }}</template>
        </el-table-column>
        <el-table-column prop="title" label="采购内容" min-width="160" show-overflow-tooltip />
        <el-table-column label="预估金额(元)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.estimateAmount) }}</template>
        </el-table-column>
        <el-table-column label="期望时间" width="100">
          <template #default="{ row }">{{ fmtDate(row.expectDate) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      </el-table>
      <div class="item-total">
        合计：<span class="num">{{ money(detailItemsTotal) }}</span> 元
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, fmtTime } from '../../../utils/format'
import {
  planPage, planDetail, planSave, planUpdate, planConfirm, planVoid,
  reqPage, fetchProjectOptions, statusMeta
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, planMonth: null, status: '' })

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await planPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.planMonth = null; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: { items: [] } })
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  planMonth: [{ required: true, message: '请选择计划月份', trigger: 'change' }],
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }]
}
const itemsTotal = computed(() => (form.data.items || []).reduce((s, i) => s + (Number(i.estimateAmount) || 0), 0))

const openForm = async row => {
  form.id = row?.id || null
  if (row?.id) {
    const d = await planDetail(row.id)
    form.data = {
      projectId: d.projectId, planMonth: d.planMonth, name: d.name, remark: d.remark || '',
      items: (d.items || []).map(i => ({
        requirementId: i.requirementId, reqCode: i.reqCode, title: i.title,
        estimateAmount: i.estimateAmount, expectDate: i.expectDate, remark: i.remark || ''
      }))
    }
  } else {
    form.data = { projectId: query.projectId, planMonth: null, name: '', remark: '', items: [] }
  }
  form.visible = true
}
const addItem = () => {
  form.data.items.push({ requirementId: null, reqCode: '', title: '', estimateAmount: undefined, expectDate: null, remark: '' })
}
const onSave = async () => {
  await formRef.value.validate()
  if (!form.data.items.length) return ElMessage.warning('请至少添加一条计划明细')
  form.saving = true
  try {
    if (form.id) await planUpdate({ id: form.id, ...form.data })
    else await planSave(form.data)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onConfirm = async row => {
  await ElMessageBox.confirm(`确认定稿计划 ${row.planCode}？确认后不可再编辑。`, '确认计划', { type: 'warning' })
  await planConfirm(row.id)
  ElMessage.success('计划已确认')
  load()
}
const onVoid = async row => {
  await ElMessageBox.confirm(`确认作废计划 ${row.planCode}？`, '作废', { type: 'warning' })
  await planVoid(row.id)
  ElMessage.success('已作废')
  load()
}

// ============ 引入需求 ============
const importTableRef = ref()
const importDlg = reactive({ visible: false, loading: false, list: [], selection: [] })
const openImport = async () => {
  importDlg.visible = true
  importDlg.loading = true
  try {
    const page = await reqPage({ current: 1, size: 200, status: 'APPROVED', projectId: form.data.projectId || undefined })
    const exists = new Set(form.data.items.map(i => i.requirementId).filter(Boolean))
    importDlg.list = (page?.records || []).filter(r => !exists.has(r.id))
  } finally {
    importDlg.loading = false
  }
}
const onImport = () => {
  importDlg.selection.forEach(r => {
    form.data.items.push({
      requirementId: r.id, reqCode: r.reqCode, title: r.title,
      estimateAmount: r.estimateAmount, expectDate: r.expectDate, remark: ''
    })
  })
  ElMessage.success(`已引入 ${importDlg.selection.length} 条需求`)
  importDlg.visible = false
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {}, items: [] })
const detailItemsTotal = computed(() => detail.items.reduce((s, i) => s + (Number(i.estimateAmount) || 0), 0))
const openDetail = async row => {
  const d = await planDetail(row.id)
  detail.data = d || row
  detail.items = d?.items || []
  detail.visible = true
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 14px 0 8px; font-size: 14px; font-weight: 600; color: #0c447c; }
.item-total { margin-top: 8px; text-align: right; font-size: 13px; color: #0c447c; font-weight: 600; }
</style>
