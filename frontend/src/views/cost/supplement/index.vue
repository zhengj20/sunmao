<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.contractId" placeholder="关联合同" clearable filterable style="width: 240px" @change="onSearch">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="编号/标题" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增补充协议</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="supplementCode" label="协议编号" width="170" show-overflow-tooltip />
        <el-table-column prop="title" label="协议标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contractName" label="关联合同" min-width="180" show-overflow-tooltip />
        <el-table-column label="补充金额（元）" width="140" align="right">
          <template #default="{ row }">
            <span class="num" :style="row.amount > 0 ? 'color:#c45656' : 'color:#529b2e'">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="协议日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.supplementDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" size="small" @click="onSubmit(row)">提交</el-button>
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
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="补充协议详情" size="46%">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="协议编号">{{ detail.supplementCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColor(detail.status)" size="small">{{ STATUS[detail.status] || detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="协议标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="关联合同" :span="2">{{ detail.contractName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="补充金额（元）">
            <span class="num" :style="detail.amount > 0 ? 'color:#c45656' : 'color:#529b2e'">{{ money(detail.amount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="协议日期">{{ fmtDate(detail.supplementDate) }}</el-descriptions-item>
          <el-descriptions-item label="协议说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑补充协议' : '新增补充协议'" width="540px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="关联合同" required>
          <el-select v-model="form.contractId" filterable style="width: 100%">
            <el-option v-for="c in contracts" :key="c.id" :label="c.name + '（' + c.contractCode + '）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="如：设计变更补充协议" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="补充金额" required>
              <el-input-number v-model="form.amount" :controls="true" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议日期">
              <el-date-picker v-model="form.supplementDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="协议说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, statusColor } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions,
  fetchSupplements, fetchSupplementDetail,
  createSupplement, updateSupplement, deleteSupplement, submitSupplement
} from '../../../api/cost'

const STATUS = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已生效', REJECTED: '已驳回' }

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const contracts = ref([])
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, contractId: null, status: null, kw: '', current: 1, size: 20 })

const detailVisible = ref(false)
const detail = ref(null)
const formVisible = ref(false)
const form = reactive({ id: null, contractId: null, title: '', amount: null, supplementDate: null, description: '' })

const loadList = async () => {
  loading.value = true
  try {
    const page = await fetchSupplements(query)
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.contractId = null
  query.status = null
  query.kw = ''
  onSearch()
}

const loadContracts = async () => {
  contracts.value = query.projectId ? ((await fetchContractOptions({ projectId: query.projectId })) || []) : []
}

const onProjectChange = async () => {
  query.contractId = null
  await loadContracts()
  onSearch()
}

const openDetail = async row => {
  detail.value = await fetchSupplementDetail(row.id)
  detailVisible.value = true
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, contractId: row.contractId, title: row.title || '',
      amount: row.amount, supplementDate: row.supplementDate, description: row.description || '', remark: row.remark || ''
    })
  } else {
    Object.assign(form, { id: null, contractId: null, title: '', amount: null, supplementDate: null, description: '', remark: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.contractId) return ElMessage.warning('请选择关联合同')
  if (!form.title) return ElMessage.warning('请填写协议标题')
  if (form.amount == null) return ElMessage.warning('请填写补充金额')
  saving.value = true
  try {
    if (form.id) {
      await updateSupplement({ ...form, projectId: query.projectId })
      ElMessage.success('补充协议已更新')
    } else {
      await createSupplement({ ...form, projectId: query.projectId })
      ElMessage.success('补充协议已创建')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onSubmit = async row => {
  await ElMessageBox.confirm('提交后将进入审批流程，确认提交？', '提示', { type: 'warning' })
  await submitSupplement(row.id)
  ElMessage.success('已提交审批')
  await loadList()
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除补充协议【${row.supplementCode}】？`, '提示', { type: 'warning' })
  await deleteSupplement(row.id)
  ElMessage.success('已删除')
  await loadList()
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
</style>
