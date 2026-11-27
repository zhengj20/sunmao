<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="登录名/姓名/手机号" clearable style="width: 200px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="load">
              <el-option label="启用" :value="1" />
              <el-option label="冻结" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="username" label="登录名" width="120" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" width="110" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机号" width="125" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="所属组织" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ orgMap[row.orgId] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="jobLevel" label="职务级别" width="90" align="center">
          <template #default="{ row }">{{ row.jobLevel || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启用"
              inactive-text="冻结"
              @change="onToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="onResetPwd(row)">重置密码</el-button>
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

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑用户' : '新增用户'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="登录名" prop="username">
              <el-input v-model="dlg.form.username" :disabled="!!dlg.form.id" placeholder="登录账号" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="dlg.form.realName" placeholder="真实姓名" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="!dlg.form.id">
            <el-form-item label="初始密码" prop="password">
              <el-input v-model="dlg.form.password" type="password" show-password placeholder="留空默认 Reip@123" maxlength="64" />
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
            <el-form-item label="职务级别" prop="jobLevel">
              <el-select v-model="dlg.form.jobLevel" placeholder="请选择职务级别" clearable style="width: 100%">
                <el-option v-for="l in JOB_LEVELS" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="dlg.form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">冻结</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色分配" prop="roleIds">
              <el-select v-model="dlg.form.roleIds" multiple placeholder="请选择角色（可多选）" style="width: 100%">
                <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { userPage, userRoleIds, userCreate, userUpdate, userResetPassword, userToggleStatus, roleList } from '../../../api/sys'
import { orgTree } from '../../../api/mdm'
import { fmtTime } from '../../../utils/format'

const JOB_LEVELS = ['M1', 'M2', 'M3', 'M4', 'M5', 'M6', 'E1', 'E2', 'E3', 'E4', 'E5', 'E6', 'E7', 'E8', 'E9']

const loading = ref(false)
const rows = ref([])
const roles = ref([])
const orgOptions = ref([])
const orgMap = ref({})
const query = reactive({ kw: '', status: null })
const page = reactive({ current: 1, size: 20, total: 0 })

const flattenOrg = (list, map) => (list || []).forEach(o => {
  map[o.id] = o.name
  flattenOrg(o.children, map)
})

const load = async () => {
  loading.value = true
  try {
    const data = await userPage({
      current: page.current, size: page.size,
      kw: query.kw || undefined,
      status: query.status === null || query.status === '' ? undefined : query.status
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.kw = ''
  query.status = null
  page.current = 1
  load()
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  username: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ min: 6, message: '密码至少6位', trigger: 'blur' }],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const openDialog = async row => {
  if (row) {
    const roleIds = await userRoleIds(row.id)
    dlg.form = { ...row, password: undefined, roleIds: roleIds || [] }
  } else {
    dlg.form = {
      username: '', realName: '', password: '', mobile: '', email: '',
      orgId: null, jobLevel: '', status: 1, roleIds: []
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    const { password, roleIds, ...user } = dlg.form
    if (user.id) {
      await userUpdate(user, roleIds)
      ElMessage.success('修改成功')
    } else {
      if (password) user.password = password
      await userCreate(user, roleIds)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onToggleStatus = row => {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '冻结'
  ElMessageBox.confirm(`确定${action}用户「${row.realName || row.username}」吗？`, `${action}确认`, { type: 'warning' })
    .then(async () => {
      await userToggleStatus(row.id, next)
      ElMessage.success(`${action}成功`)
      load()
    }).catch(() => {})
}

const onResetPwd = row => {
  ElMessageBox.prompt(`重置用户「${row.realName || row.username}」的密码：`, '重置密码', {
    type: 'warning',
    inputPlaceholder: '请输入新密码（至少6位）',
    inputType: 'password',
    inputValidator: v => (v && v.length >= 6) ? true : '密码不能为空且至少6位'
  }).then(async ({ value }) => {
    await userResetPassword(row.id, value)
    ElMessage.success('密码已重置')
  }).catch(() => {})
}

onMounted(async () => {
  load()
  roles.value = await roleList() || []
  orgOptions.value = await orgTree()
  const map = {}
  flattenOrg(orgOptions.value, map)
  orgMap.value = map
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
