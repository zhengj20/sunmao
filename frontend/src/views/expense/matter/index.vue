<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.deptId" placeholder="部门" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="t in MATTER_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(MATTER_STATUS)" :key="v" :label="MATTER_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="编号/标题" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建事项申请</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="matterNo" label="事项编号" width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ matterTypeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="事项标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="部门" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="申请人" width="110" align="center">
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <el-table-column label="预计金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.expectAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="计划日期" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.planDate) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="事项说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" size="small" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 新建事项申请 -->
    <el-dialog v-model="formVisible" title="新建事项申请" width="620px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="t in MATTER_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" required>
              <el-select v-model="form.deptId" filterable style="width: 100%">
                <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="事项标题" required>
          <el-input v-model="form.title" maxlength="200" placeholder="如：2026 年三季度集团经营分析会" />
        </el-form-item>
        <el-form-item label="事项说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" placeholder="事项背景、内容、预期结果等" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="申请人" required>
              <el-select v-model="form.userId" filterable style="width: 100%" @change="onUserChange">
                <el-option v-for="u in employees" :key="u.userId" :label="u.name" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计金额">
              <el-input-number v-model="form.expectAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计划日期">
              <el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <div class="hint">提交后走「部门经理 → 财务经理」审批流；审批通过的事项可作为后续费用申请/报销的事由依据（单据备注引用事项编号）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存为草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  fetchOrgOptions, fetchEmployeeOptions,
  matterPage, matterCreate, matterDelete, matterSubmit,
  MATTER_TYPES, matterTypeLabel, MATTER_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, MATTER_STATUS)
const orgs = ref([])
const employees = ref([])
const deptName = id => orgs.value.find(o => o.id === id)?.name || (id ? '部门#' + id : '-')

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ deptId: null, type: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await matterPage({
      current: query.current, size: query.size,
      deptId: query.deptId || undefined, type: query.type || undefined,
      status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.deptId = null; query.type = null; query.status = null; query.kw = ''; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ type: 'MEETING', title: '', description: '', deptId: null, userId: null, userName: null, expectAmount: null, planDate: null })
const onUserChange = id => { form.userName = employees.value.find(u => u.userId === id)?.name || null }
const openForm = async () => {
  Object.assign(form, {
    type: 'MEETING', title: '', description: '', deptId: query.deptId,
    userId: null, userName: null, expectAmount: null, planDate: null
  })
  formVisible.value = true
  if (!employees.value.length) employees.value = await fetchEmployeeOptions()
}
const onSave = async () => {
  if (!form.type) return ElMessage.warning('请选择事项类型')
  if (!form.title.trim()) return ElMessage.warning('请填写事项标题')
  if (!form.deptId) return ElMessage.warning('请选择部门')
  if (!form.userId) return ElMessage.warning('请选择申请人')
  saving.value = true
  try {
    await matterCreate({
      type: form.type, title: form.title, description: form.description || undefined,
      deptId: form.deptId, userId: form.userId, userName: form.userName,
      expectAmount: form.expectAmount || undefined, planDate: form.planDate || undefined
    })
    ElMessage.success('事项申请已创建（草稿），可提交审批')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

const onSubmit = async row => {
  await ElMessageBox.confirm(`确认提交事项 ${row.matterNo}「${row.title}」进入审批（部门→财务）？`, '提交审批', { type: 'warning' })
  await matterSubmit(row.id)
  ElMessage.success('已提交审批')
  loadList()
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除事项 ${row.matterNo}？`, '提示', { type: 'warning' })
  await matterDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(async () => {
  orgs.value = await fetchOrgOptions()
  if (orgs.value.length) query.deptId = orgs.value[0].id
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
