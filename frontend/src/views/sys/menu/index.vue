<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent>
          <el-form-item label="关键字">
            <el-input v-model="kw" placeholder="菜单名称/路径" clearable style="width: 220px" :prefix-icon="Search" />
          </el-form-item>
        </el-form>
        <div>
          <el-button :icon="expandAll ? Fold : Expand" @click="toggleExpand">{{ expandAll ? '全部折叠' : '全部展开' }}</el-button>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增菜单</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        ref="tableRef"
        :data="filteredTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll"
        border
        style="width: 100%"
      >
        <el-table-column prop="name" label="菜单名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="TYPE_TAG[row.type] || 'info'" effect="plain" size="small">{{ TYPE[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="component" label="组件路径" width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.component || '-' }}</template>
        </el-table-column>
        <el-table-column prop="permCode" label="权限码" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.permCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" class="num" />
        <el-table-column label="可见" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">{{ row.visible === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(null, row)">新增下级</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑菜单' : '新增菜单'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="dlg.form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="dlg.form.type">
            <el-radio value="D">目录</el-radio>
            <el-radio value="M">菜单</el-radio>
            <el-radio value="B">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="请输入菜单名称" maxlength="64" />
        </el-form-item>
        <template v-if="dlg.form.type !== 'B'">
          <el-form-item label="路由路径" prop="path">
            <el-input v-model="dlg.form.path" placeholder="如：/mdm/project" maxlength="128" />
          </el-form-item>
          <el-form-item label="组件路径" prop="component" v-if="dlg.form.type === 'M'">
            <el-input v-model="dlg.form.component" placeholder="如：mdm/project/index" maxlength="128" />
          </el-form-item>
        </template>
        <el-form-item label="权限码" prop="permCode">
          <el-input v-model="dlg.form.permCode" placeholder="如：mdm:supplier:create（按钮权限）" maxlength="128" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="dlg.form.icon" placeholder="Element Plus 图标名，如：Setting" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序号" prop="sort">
          <el-input-number v-model="dlg.form.sort" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="是否可见" prop="visible">
          <el-radio-group v-model="dlg.form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
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
import { Search, Plus, Expand, Fold } from '@element-plus/icons-vue'
import { menuTree, menuSave, menuDelete } from '../../../api/sys'

const TYPE = { D: '目录', M: '菜单', B: '按钮' }
const TYPE_TAG = { D: 'warning', M: '', B: 'info' }

const loading = ref(false)
const tree = ref([])
const kw = ref('')
const expandAll = ref(true)
const tableRef = ref()

const parentOptions = computed(() => [{ id: 0, name: '顶级（根菜单）', children: tree.value }])

const filterTree = (nodes, keyword) => {
  const result = []
  for (const n of nodes || []) {
    const children = filterTree(n.children, keyword)
    const selfOk = !keyword || n.name?.includes(keyword) || n.path?.includes(keyword)
    if (selfOk || children.length) result.push({ ...n, children })
  }
  return result
}
const filteredTree = computed(() => filterTree(tree.value, kw.value.trim()))

const load = async () => {
  loading.value = true
  try {
    tree.value = await menuTree() || []
  } finally {
    loading.value = false
  }
}

const toggleExpand = () => {
  expandAll.value = !expandAll.value
  // 强制重建表格以应用展开状态
  loading.value = true
  setTimeout(() => { loading.value = false }, 0)
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }],
  component: [{ required: true, message: '请输入组件路径', trigger: 'blur' }]
}

const openDialog = (row, parent) => {
  if (row) {
    dlg.form = { ...row, children: undefined }
  } else {
    dlg.form = {
      parentId: parent ? parent.id : 0,
      type: 'M', name: '', path: '', component: '', permCode: '', icon: '', sort: 0, visible: 1
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    const payload = { ...dlg.form, children: undefined }
    await menuSave(payload)
    ElMessage.success('保存成功')
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除菜单「${row.name}」吗？存在下级菜单时将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await menuDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
