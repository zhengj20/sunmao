<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="tip">
          角色权限：维护系统角色及其可见菜单范围。勾选叶子菜单后父级目录将自动关联保存。
        </div>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增角色</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredRows" border style="width: 100%">
        <el-table-column prop="name" label="角色名" width="140" show-overflow-tooltip />
        <el-table-column prop="code" label="角色编码" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" class="status-tag">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="openDialog(row, true)">分配权限</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dlg.visible"
      :title="dlg.form.id ? (dlg.permOnly ? '分配权限 - ' + dlg.form.name : '编辑角色') : '新增角色'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="90px">
        <template v-if="!dlg.permOnly">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="角色名" prop="name">
                <el-input v-model="dlg.form.name" placeholder="如：成本经理" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="角色编码" prop="code">
                <el-input v-model="dlg.form.code" placeholder="如：COST_MGR" maxlength="64" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="dlg.form.status">
                  <el-radio :value="1">启用</el-radio>
                  <el-radio :value="0">停用</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注" prop="remark">
                <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="角色说明" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <el-form-item v-else label="角色">
          <el-input :model-value="`${dlg.form.name}（${dlg.form.code}）`" disabled />
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="menu-tree-wrap">
            <el-tree
              ref="menuTreeRef"
              :data="menus"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'name', children: 'children' }"
            >
              <template #default="{ data }">
                <span class="menu-node">
                  {{ data.name }}
                  <el-tag v-if="data.type === 'D'" size="small" type="info" effect="plain">目录</el-tag>
                  <el-tag v-else-if="data.type === 'B'" size="small" type="warning" effect="plain">按钮</el-tag>
                </span>
              </template>
            </el-tree>
          </div>
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { roleList, roleMenuIds, roleSave, menuTree } from '../../../api/sys'
import { fmtTime } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const menus = ref([])
const kw = ref('')

const filteredRows = computed(() =>
  rows.value.filter(r => !kw.value || r.name?.includes(kw.value) || r.code?.includes(kw.value))
)

/** 收集树中所有含子节点的节点ID（用于回显时剔除父节点，避免父选中带动全选） */
const parentIds = new Set()
const collectParents = nodes => (nodes || []).forEach(n => {
  if (n.children && n.children.length) {
    parentIds.add(n.id)
    collectParents(n.children)
  }
})

const load = async () => {
  loading.value = true
  try {
    rows.value = await roleList() || []
  } finally {
    loading.value = false
  }
}

// ================= 弹窗 =================
const formRef = ref()
const menuTreeRef = ref()
const dlg = reactive({ visible: false, saving: false, permOnly: false, form: {} })
const rules = {
  name: [{ required: true, message: '请输入角色名', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

const openDialog = async (row, permOnly = false) => {
  dlg.permOnly = permOnly
  if (row) {
    dlg.form = { ...row }
  } else {
    dlg.form = { name: '', code: '', remark: '', status: 1 }
  }
  dlg.visible = true
  await nextTick()
  if (row) {
    const ids = await roleMenuIds(row.id)
    // 仅回显叶子节点勾选，父级由树自动推导半选/全选
    const leafChecked = (ids || []).filter(id => !parentIds.has(id))
    menuTreeRef.value?.setCheckedKeys(leafChecked, true)
  } else {
    menuTreeRef.value?.setCheckedKeys([], true)
  }
}

const onSave = async () => {
  if (!dlg.permOnly) await formRef.value.validate()
  dlg.saving = true
  try {
    const checked = menuTreeRef.value?.getCheckedKeys() || []
    const half = menuTreeRef.value?.getHalfCheckedKeys() || []
    const menuIds = [...half, ...checked]
    await roleSave(dlg.form, menuIds)
    ElMessage.success(dlg.form.id ? '保存成功' : '新增成功')
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

onMounted(async () => {
  load()
  menus.value = await menuTree() || []
  collectParents(menus.value)
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.tip { font-size: 12.5px; color: #6b7280; }
.menu-tree-wrap {
  width: 100%; max-height: 320px; overflow: auto;
  border: 1px solid #e5e7eb; border-radius: 4px; padding: 6px 10px;
}
.menu-node { display: inline-flex; align-items: center; gap: 6px; }
</style>
