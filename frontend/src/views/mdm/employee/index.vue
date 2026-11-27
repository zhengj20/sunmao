<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="姓名/工号/手机号" clearable style="width: 200px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="所属组织">
            <el-tree-select
              v-model="query.orgId"
              :data="orgOptions"
              node-key="id"
              :props="{ label: 'name', children: 'children' }"
              check-strictly
              clearable
              default-expand-all
              placeholder="全部组织"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="load">
              <el-option v-for="(label, value) in EMP_STATUS" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增员工</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="empNo" label="工号" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.empNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" width="100" show-overflow-tooltip />
        <el-table-column prop="orgName" label="所属组织" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="position" label="岗位" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.position || '-' }}</template>
        </el-table-column>
        <el-table-column prop="jobLevel" label="职务级别" width="90" align="center">
          <template #default="{ row }">{{ row.jobLevel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="关联账号" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="entryDate" label="入职日期" width="105" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="EMP_STATUS_TAG[row.status] || 'info'" class="status-tag">
              {{ EMP_STATUS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑员工' : '新增员工'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="dlg.form.realName" placeholder="请输入姓名" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工号" prop="empNo">
              <el-input v-model="dlg.form.empNo" placeholder="请输入工号" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属组织" prop="orgId">
              <el-tree-select
                v-model="dlg.form.orgId"
                :data="orgOptions"
                node-key="id"
                :props="{ label: 'name', children: 'children' }"
                check-strictly
                clearable
                default-expand-all
                placeholder="请选择所属组织"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联账号" prop="userId">
              <el-select v-model="dlg.form.userId" placeholder="关联登录账号" clearable filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位" prop="position">
              <el-input v-model="dlg.form.position" placeholder="请输入岗位" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职务级别" prop="jobLevel">
              <el-select v-model="dlg.form.jobLevel" placeholder="请选择职务级别" clearable style="width: 100%">
                <el-option v-for="l in JOB_LEVELS" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="dlg.form.mobile" placeholder="请输入手机号" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="dlg.form.email" placeholder="请输入邮箱" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="证件号" prop="idCard">
              <el-input v-model="dlg.form.idCard" placeholder="身份证号" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="dlg.form.status" style="width: 100%">
                <el-option v-for="(label, value) in EMP_STATUS" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期" prop="entryDate">
              <el-date-picker v-model="dlg.form.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离职日期" prop="leaveDate">
              <el-date-picker v-model="dlg.form.leaveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detail.visible" title="员工详情" width="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工号">{{ detail.data.empNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.data.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ detail.data.orgName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.data.position || '-' }}</el-descriptions-item>
        <el-descriptions-item label="职务级别">{{ detail.data.jobLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联账号">{{ detail.data.userName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.data.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.data.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="证件号">{{ maskIdCard(detail.data.idCard) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="EMP_STATUS_TAG[detail.data.status] || 'info'">{{ EMP_STATUS[detail.data.status] || detail.data.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="入职日期">{{ detail.data.entryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="离职日期">{{ detail.data.leaveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ fmtTime(detail.data.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { orgTree, employeePage, employeeDetail, employeeCreate, employeeUpdate, employeeDelete } from '../../../api/mdm'
import { userPage } from '../../../api/sys'
import { fmtTime } from '../../../utils/format'

const EMP_STATUS = { IN_SERVICE: '在职', TRANSFER: '调动', LEAVE: '离职' }
const EMP_STATUS_TAG = { IN_SERVICE: 'success', TRANSFER: 'warning', LEAVE: 'info' }
const JOB_LEVELS = ['M1', 'M2', 'M3', 'M4', 'M5', 'M6', 'E1', 'E2', 'E3', 'E4', 'E5', 'E6', 'E7', 'E8', 'E9']

const loading = ref(false)
const rows = ref([])
const orgs = ref([])
const users = ref([])
const query = reactive({ kw: '', orgId: null, status: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const orgOptions = ref([])

const load = async () => {
  loading.value = true
  try {
    const data = await employeePage({
      current: page.current, size: page.size,
      kw: query.kw || undefined,
      orgId: query.orgId || undefined,
      status: query.status || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  query.orgId = null
  query.status = ''
  page.current = 1
  load()
}

/** 证件号脱敏：保留前4后4 */
const maskIdCard = v => {
  if (!v) return '-'
  if (v.length <= 8) return v
  return v.slice(0, 4) + '****' + v.slice(-4)
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const openDialog = async row => {
  if (row) {
    const detail = await employeeDetail(row.id)
    dlg.form = { ...detail }
  } else {
    dlg.form = {
      realName: '', empNo: '', orgId: null, userId: null, position: '', jobLevel: '',
      mobile: '', email: '', idCard: '', entryDate: null, leaveDate: null, status: 'IN_SERVICE'
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    if (dlg.form.id) {
      await employeeUpdate(dlg.form)
      ElMessage.success('修改成功')
    } else {
      await employeeCreate(dlg.form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除员工「${row.realName}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await employeeDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

const detail = reactive({ visible: false, data: {} })
const openDetail = row => {
  detail.data = row
  detail.visible = true
}

onMounted(async () => {
  load()
  orgOptions.value = await orgTree()
  const data = await userPage({ current: 1, size: 500 })
  users.value = data.records || []
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
