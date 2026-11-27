<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="组织名称/编码" clearable style="width: 220px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="组织类型">
            <el-select v-model="query.orgType" placeholder="全部" clearable style="width: 150px" @change="load">
              <el-option v-for="(label, value) in ORG_TYPE" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="load">
              <el-option label="正常" value="ACTIVE" />
              <el-option label="冻结" value="FROZEN" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增组织</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        :expand-row-keys="[]"
        border
        style="width: 100%"
      >
        <el-table-column prop="name" label="组织名称" min-width="260" show-overflow-tooltip />
        <el-table-column prop="code" label="组织编码" width="130" show-overflow-tooltip />
        <el-table-column label="组织类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="ORG_TYPE_TAG[row.orgType] || 'info'" effect="plain">{{ ORG_TYPE[row.orgType] || row.orgType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ userMap[row.leaderUserId] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="costCenterCode" label="成本中心编码" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.costCenterCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" class="status-tag">
              {{ row.status === 'ACTIVE' ? '正常' : '冻结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" class="num" />
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(null, row)">新增下级</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑组织' : '新增组织'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="110px">
        <el-form-item label="上级组织" prop="parentId">
          <el-tree-select
            v-model="dlg.form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="组织名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="请输入组织名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="组织编码" prop="code">
          <el-input v-model="dlg.form.code" placeholder="请输入组织编码" maxlength="64" />
        </el-form-item>
        <el-form-item label="组织类型" prop="orgType">
          <el-select v-model="dlg.form.orgType" placeholder="请选择组织类型" style="width: 100%">
            <el-option v-for="(label, value) in ORG_TYPE" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人" prop="leaderUserId">
          <el-select v-model="dlg.form.leaderUserId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="成本中心编码" prop="costCenterCode">
          <el-input v-model="dlg.form.costCenterCode" placeholder="财务成本中心映射编码" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dlg.form.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="FROZEN">冻结</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="dlg.form.sort" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { orgTree, orgCreate, orgUpdate, orgDelete } from '../../../api/mdm'
import { userPage } from '../../../api/sys'

const ORG_TYPE = { GROUP: '集团', REGION: '区域', CITY: '城市', PROJECT: '项目公司', DEPT: '部门' }
const ORG_TYPE_TAG = { GROUP: 'danger', REGION: 'warning', CITY: '', PROJECT: 'success', DEPT: 'info' }

const loading = ref(false)
const tree = ref([])
const users = ref([])
const query = reactive({ kw: '', orgType: '', status: '' })

const userMap = computed(() => {
  const m = {}
  users.value.forEach(u => { m[u.id] = u.realName || u.username })
  return m
})

const parentOptions = computed(() => [{ id: 0, name: '顶级（根节点）', children: tree.value }])

/** 关键字过滤：命中节点的祖先链保留 */
const filterTree = (nodes, kw, orgType, status) => {
  const result = []
  for (const n of nodes || []) {
    const children = filterTree(n.children, kw, orgType, status)
    const selfOk =
      (!kw || n.name?.includes(kw) || n.code?.includes(kw)) &&
      (!orgType || n.orgType === orgType) &&
      (!status || n.status === status)
    if (selfOk || children.length) result.push({ ...n, children })
  }
  return result
}
const filteredTree = computed(() => filterTree(tree.value, query.kw.trim(), query.orgType, query.status))

const load = async () => {
  loading.value = true
  try {
    tree.value = await orgTree()
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  const data = await userPage({ current: 1, size: 500 })
  users.value = data.records || []
}

const resetQuery = () => {
  query.kw = ''
  query.orgType = ''
  query.status = ''
  load()
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择组织类型', trigger: 'change' }]
}

const openDialog = (row, parent) => {
  if (row) {
    dlg.form = { ...row, children: undefined }
  } else {
    dlg.form = {
      parentId: parent ? parent.id : 0,
      name: '', code: '', orgType: 'DEPT', leaderUserId: null,
      costCenterCode: '', status: 'ACTIVE', sort: 0
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    const payload = { ...dlg.form, children: undefined }
    if (payload.id) {
      await orgUpdate(payload)
      ElMessage.success('修改成功')
    } else {
      await orgCreate(payload)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除组织「${row.name}」吗？存在下级组织或挂接项目时将无法删除。`, '删除确认', {
    type: 'warning'
  }).then(async () => {
    await orgDelete(row.id)
    ElMessage.success('删除成功')
    load()
  }).catch(() => {})
}

onMounted(() => {
  load()
  loadUsers()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
