<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.invalidType" placeholder="无效成本类型" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="t in INVALID_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="责任部门/描述" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增登记</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">无效成本总额（元）</div>
          <div class="value" style="color: #c45656">{{ money(summary.totalAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">已确认金额（元）</div>
          <div class="value">{{ money(summary.confirmedAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">登记笔数</div>
          <div class="value">{{ summary.count ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">占比最高的责任部门</div>
          <div class="value" style="font-size: 15px; padding-top: 4px">{{ summary.topDept || '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.invalidType === '其他' ? 'info' : 'danger'" effect="plain">{{ row.invalidType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="130" align="right">
          <template #default="{ row }"><span class="num" style="color: #c45656">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="发生日期" width="100" align="center">
          <template #default="{ row }">{{ fmtDate(row.occurredDate) }}</template>
        </el-table-column>
        <el-table-column prop="contractName" label="关联合同" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="responsibleDept" label="责任部门" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.responsibleDept || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="事由描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" width="140" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmit(row)">提交</el-button>
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

    <!-- 登记表单 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑无效成本' : '无效成本登记'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="form.invalidType" style="width: 100%">
                <el-option v-for="t in INVALID_TYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="金额（元）" required>
              <el-input-number v-model="form.amount" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生日期" required>
              <el-date-picker v-model="form.occurredDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任部门" required>
              <el-select v-model="form.responsibleDept" allow-create filterable default-first-option style="width: 100%">
                <el-option v-for="d in DEPTS" :key="d" :label="d" :value="d" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="关联合同">
              <el-select v-model="form.contractId" clearable filterable style="width: 100%">
                <el-option v-for="c in contracts" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="事由描述" required>
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" placeholder="如：2#楼三层剪力墙胀模返工" />
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
import { money, fmtDate, fmtTime, statusColor } from '../../../utils/format'
import {
  fetchProjectOptions, fetchContractOptions,
  fetchInvalids, fetchInvalidSummary, createInvalid, updateInvalid, deleteInvalid, submitInvalid
} from '../../../api/cost'

const STATUS = { DRAFT: '草稿', APPROVING: '审批中', CONFIRMED: '已确认' }
const INVALID_TYPES = ['超合同支付', '返工损失', '赔偿', '罚款', '其他']
const DEPTS = ['工程管理部', '设计管理部', '成本管理部', '采购管理部', '营销管理部', '项目工程部', '其他']

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const contracts = ref([])
const rows = ref([])
const total = ref(0)
const summary = ref({})
const query = reactive({ projectId: null, invalidType: null, status: null, kw: '', current: 1, size: 20 })

const formVisible = ref(false)
const form = reactive({ id: null, invalidType: '返工损失', amount: null, occurredDate: null, contractId: null, responsibleDept: '', description: '' })

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchInvalids(query),
      fetchInvalidSummary({ projectId: query.projectId })
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    summary.value = sum || {}
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.invalidType = null
  query.status = null
  query.kw = ''
  onSearch()
}

const loadContracts = async () => {
  contracts.value = query.projectId ? ((await fetchContractOptions({ projectId: query.projectId })) || []) : []
}

const onProjectChange = async () => {
  await loadContracts()
  onSearch()
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, invalidType: row.invalidType, amount: row.amount, occurredDate: row.occurredDate,
      contractId: row.contractId, responsibleDept: row.responsibleDept || '', description: row.description || ''
    })
  } else {
    Object.assign(form, { id: null, invalidType: '返工损失', amount: null, occurredDate: null, contractId: null, responsibleDept: '', description: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.invalidType) return ElMessage.warning('请选择类型')
  if (form.amount == null) return ElMessage.warning('请填写金额')
  if (!form.occurredDate) return ElMessage.warning('请选择发生日期')
  if (!form.responsibleDept) return ElMessage.warning('请填写责任部门')
  if (!form.description) return ElMessage.warning('请填写事由描述')
  saving.value = true
  try {
    if (form.id) {
      await updateInvalid({ ...form, projectId: query.projectId })
      ElMessage.success('登记已更新')
    } else {
      await createInvalid({ ...form, projectId: query.projectId })
      ElMessage.success('登记成功')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onSubmit = async row => {
  await ElMessageBox.confirm('提交后将进入认定审批流程，确认提交？', '提示', { type: 'warning' })
  await submitInvalid(row.id)
  ElMessage.success('已提交审批')
  await loadList()
}

const onDelete = async row => {
  await ElMessageBox.confirm('确认删除该条无效成本登记？', '提示', { type: 'warning' })
  await deleteInvalid(row.id)
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
.stat-row { margin-bottom: 12px; }
</style>
