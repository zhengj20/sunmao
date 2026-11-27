<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 180px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="审批中" value="APPROVING" />
              <el-option label="已通过" value="APPROVED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增零星采购</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="项目" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="采购事项" min-width="180" show-overflow-tooltip />
        <el-table-column label="采购金额(元)" width="140" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">比价明细</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
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

    <!-- 新增/编辑 + 比价明细 -->
    <el-dialog v-model="form.visible" :title="form.id ? '零星采购·比价明细' : '新增零星采购'" width="680px" destroy-on-close>
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
            <el-form-item label="采购事项" prop="title">
              <el-input v-model="form.data.title" placeholder="如：零星材料采购" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购金额" prop="amount">
              <el-input-number v-model="form.data.amount" :min="0" :precision="2" :controls="false" placeholder="元" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="比价附件">
              <el-input v-model="form.data.compareAttachment" placeholder="三家报价单等附件标识" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="事由">
          <el-input v-model="form.data.reason" type="textarea" :rows="2" placeholder="如：突发维修、小额零星补料" maxlength="300" />
        </el-form-item>
      </el-form>

      <template v-if="form.id">
        <div class="block-title">比价明细（提交要求：≥3家 且各家报价一致）</div>
        <el-table :data="compares" size="small" border>
          <el-table-column prop="supplierName" label="供应商" min-width="160" />
          <el-table-column label="报价(元)" width="140" align="right" class="num">
            <template #default="{ row }">{{ money(row.price) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="onDeleteCompare(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="compare-add">
          <el-input v-model="compareInput.supplierName" placeholder="供应商名称" style="width: 200px" />
          <el-input-number v-model="compareInput.price" :min="0" :precision="2" :controls="false" placeholder="报价(元)" style="width: 160px" />
          <el-button type="primary" @click="onAddCompare">添加比价</el-button>
        </div>
        <el-alert type="warning" :closable="false" style="margin-top: 10px"
          :title="`当前比价 ${compares.length} 家，提交审批需满足家数≥阈值且各家报价一致（阈值读 sys_param proc.misc.min_compare，默认3）`" />
      </template>

      <template #footer>
        <el-button @click="form.visible = false">关闭</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  fetchMisc, createMisc, updateMisc, deleteMisc, submitMisc,
  fetchMiscCompares, addMiscCompare, deleteMiscCompare,
  fetchProjectOptions, statusMeta
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, projectId: null, status: '' })

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await fetchMisc(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.projectId = null; query.status = ''
  load()
}

// ============ 表单 + 比价明细 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入采购事项', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入采购金额', trigger: 'blur' }]
}
const compares = ref([])
const compareInput = reactive({ supplierName: '', price: undefined })

const openForm = async row => {
  form.id = row?.id || null
  form.data = {
    projectId: row?.projectId ?? null, title: row?.title || '',
    amount: row?.amount ?? undefined, reason: row?.reason || '',
    compareAttachment: row?.compareAttachment || ''
  }
  compares.value = []
  compareInput.supplierName = ''
  compareInput.price = undefined
  form.visible = true
  if (form.id) await loadCompares(form.id)
}
const loadCompares = async id => {
  compares.value = (await fetchMiscCompares(id).catch(() => [])) || []
}
const onAddCompare = async () => {
  if (!compareInput.supplierName || !compareInput.supplierName.trim()) return ElMessage.warning('请输入供应商名称')
  if (compareInput.price === undefined || compareInput.price === null) return ElMessage.warning('请输入报价')
  await addMiscCompare(form.id, { supplierName: compareInput.supplierName.trim(), price: compareInput.price })
  compareInput.supplierName = ''
  compareInput.price = undefined
  await loadCompares(form.id)
}
const onDeleteCompare = async c => {
  await ElMessageBox.confirm(`确认删除供应商【${c.supplierName}】的比价？`, '删除', { type: 'warning' })
  await deleteMiscCompare(form.id, c.id)
  await loadCompares(form.id)
}

const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    if (form.id) {
      await updateMisc({ id: form.id, ...form.data })
    } else {
      const id = await createMisc(form.data)
      form.id = id
    }
    ElMessage.success('保存成功')
    if (form.id) await loadCompares(form.id)
    load()
  } finally {
    form.saving = false
  }
}
const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交零星采购【${row.title}】进入审批流？（需满足比价家数且报价一致）`, '提交审批', { type: 'warning' })
  await submitMisc(row.id)
  ElMessage.success('已提交审批')
  load()
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除零星采购【${row.title}】？`, '删除', { type: 'warning' })
  await deleteMisc(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
.compare-add { display: flex; gap: 8px; margin-top: 10px; align-items: center; }
</style>
