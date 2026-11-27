<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="科目范围">
            <el-select v-model="scope" style="width: 260px" @change="onScopeChange">
              <el-option label="集团模板科目" :value="0" />
              <el-option v-for="p in projects" :key="p.id" :label="`项目科目 - ${p.name}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="kw" placeholder="科目编码/名称" clearable style="width: 180px" :prefix-icon="Search" />
          </el-form-item>
          <el-form-item>
            <el-button :icon="Search" @click="load">筛选</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button v-if="scope === 0" type="warning" plain :icon="CopyDocument" @click="cloneDlg.visible = true">克隆到项目</el-button>
          <el-button type="primary" :icon="Plus" @click="openDialog()">新增科目</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-alert
        v-if="scope !== 0 && !tree.length && !loading"
        type="info" :closable="false" show-icon style="margin: 12px"
        title="该项目尚未克隆成本科目，请先从集团模板克隆初始化项目科目树"
      />
      <el-table
        v-loading="loading"
        :data="filteredTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
        style="width: 100%"
      >
        <el-table-column prop="code" label="科目编码" width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="科目名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="level" label="级次" width="70" align="center" class="num" />
        <el-table-column label="末级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.leaf === 1 ? 'success' : 'info'" effect="plain" size="small">
              {{ row.leaf === 1 ? '末级' : '非末级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="controlMode" label="控制方式" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.controlMode" :type="CTRL_TAG[row.controlMode] || 'info'" size="small">{{ row.controlMode }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="allocateRule" label="分摊规则" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.allocateRule || '-' }}</template>
        </el-table-column>
        <el-table-column prop="financeAccount" label="财务核算科目" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.financeAccount || '-' }}</template>
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

    <!-- 新增/编辑科目 -->
    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑成本科目' : '新增成本科目'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="110px">
        <el-form-item label="科目范围">
          <el-input :model-value="scopeLabel" disabled />
        </el-form-item>
        <el-form-item label="上级科目" prop="parentId">
          <el-tree-select
            v-model="dlg.form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="科目编码" prop="code">
          <el-input v-model="dlg.form.code" placeholder="如：0301" maxlength="64" />
        </el-form-item>
        <el-form-item label="科目名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="如：主体建筑工程" maxlength="128" />
        </el-form-item>
        <el-form-item label="控制方式" prop="controlMode">
          <el-select v-model="dlg.form.controlMode" placeholder="请选择控制方式" clearable style="width: 100%">
            <el-option v-for="m in CTRL_MODES" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="分摊规则" prop="allocateRule">
          <el-input v-model="dlg.form.allocateRule" placeholder="如：按建筑面积分摊" maxlength="64" />
        </el-form-item>
        <el-form-item label="财务核算科目" prop="financeAccount">
          <el-input v-model="dlg.form.financeAccount" placeholder="财务核算科目映射编码" maxlength="64" />
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

    <!-- 克隆到项目 -->
    <el-dialog v-model="cloneDlg.visible" title="集团模板克隆到项目" width="460px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="仅允许克隆一次，已存在项目科目的项目将克隆失败" />
      <el-form label-width="90px">
        <el-form-item label="目标项目">
          <el-select v-model="cloneDlg.projectId" placeholder="请选择目标项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cloneDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="cloneDlg.saving" @click="onClone">开始克隆</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, CopyDocument } from '@element-plus/icons-vue'
import { accountTree, accountCreate, accountUpdate, accountDelete, accountClone, projectOptions } from '../../../api/mdm'

const CTRL_MODES = ['刚性', '预警', '不限']
const CTRL_TAG = { 刚性: 'danger', 预警: 'warning', 不限: 'info' }

const loading = ref(false)
const scope = ref(0)
const kw = ref('')
const tree = ref([])
const projects = ref([])

const scopeLabel = computed(() =>
  scope.value === 0 ? '集团模板科目' : `项目科目 - ${projects.value.find(p => p.id === scope.value)?.name || ''}`
)

/** 给树节点加 label（编码+名称）供树选择器显示 */
const decorate = nodes => (nodes || []).map(n => ({
  ...n,
  label: `${n.code} ${n.name}`,
  children: decorate(n.children)
}))

const parentOptions = computed(() => {
  const root = { id: 0, code: '', name: '顶级（一级科目）', label: '顶级（一级科目）', children: decorate(tree.value) }
  return [root]
})

const filterTree = (nodes, keyword) => {
  const result = []
  for (const n of nodes || []) {
    const children = filterTree(n.children, keyword)
    const selfOk = !keyword || n.code?.includes(keyword) || n.name?.includes(keyword)
    if (selfOk || children.length) result.push({ ...n, children })
  }
  return result
}
const filteredTree = computed(() => filterTree(tree.value, kw.value.trim()))

const load = async () => {
  loading.value = true
  try {
    tree.value = await accountTree({ projectId: scope.value === 0 ? undefined : scope.value }) || []
  } finally {
    loading.value = false
  }
}

const onScopeChange = () => load()

// ================= 科目弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  code: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入科目名称', trigger: 'blur' }]
}

const openDialog = (row, parent) => {
  if (row) {
    dlg.form = { ...row, children: undefined }
  } else {
    dlg.form = {
      parentId: parent ? parent.id : 0,
      code: '', name: '', controlMode: '预警', allocateRule: '', financeAccount: '', sort: 0
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
      await accountUpdate(payload)
      ElMessage.success('修改成功')
    } else {
      payload.projectId = scope.value === 0 ? null : scope.value
      await accountCreate(payload)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除科目「${row.code} ${row.name}」吗？存在下级科目时将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await accountDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

// ================= 克隆 =================
const cloneDlg = reactive({ visible: false, saving: false, projectId: null })
const onClone = async () => {
  if (!cloneDlg.projectId) return ElMessage.warning('请选择目标项目')
  cloneDlg.saving = true
  try {
    const cnt = await accountClone(cloneDlg.projectId)
    ElMessage.success(`克隆完成，共生成 ${cnt} 条项目科目`)
    cloneDlg.visible = false
  } finally {
    cloneDlg.saving = false
  }
}

onMounted(async () => {
  projects.value = await projectOptions() || []
  load()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
