<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="分类类型">
            <el-radio-group v-model="query.categoryType" @change="load">
              <el-radio-button v-for="(label, value) in CAT_TYPE" :key="value" :value="value">{{ label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="分类编码/名称" clearable style="width: 180px" :prefix-icon="Search" />
          </el-form-item>
          <el-form-item>
            <el-button :icon="Search" @click="load">筛选</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增分类</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
        style="width: 100%"
      >
        <el-table-column prop="code" label="分类编码" width="160" show-overflow-tooltip />
        <el-table-column prop="name" label="分类名称" min-width="240" show-overflow-tooltip />
        <el-table-column label="分类类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="CAT_TYPE_TAG[row.categoryType] || 'info'" effect="plain">{{ CAT_TYPE[row.categoryType] || row.categoryType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
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

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑分类' : '新增分类'" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-form-item label="分类类型" prop="categoryType">
          <el-select v-model="dlg.form.categoryType" :disabled="!!dlg.form.id" style="width: 100%">
            <el-option v-for="(label, value) in CAT_TYPE" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="上级分类" prop="parentId">
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
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="dlg.form.code" placeholder="如：GC" maxlength="64" />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="dlg.form.name" placeholder="如：工程总包类" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dlg.form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="备注信息" />
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
import { Search, Plus } from '@element-plus/icons-vue'
import { categoryTree, categoryCreate, categoryUpdate, categoryDelete } from '../../../api/mdm'

const CAT_TYPE = { CONTRACT: '合同分类', PURCHASE: '采购品类', CHANGE: '变更分类', EXPENSE: '费用科目' }
const CAT_TYPE_TAG = { CONTRACT: '', PURCHASE: 'success', CHANGE: 'warning', EXPENSE: 'danger' }

const loading = ref(false)
const tree = ref([])
const query = reactive({ categoryType: 'CONTRACT', kw: '' })

const decorate = nodes => (nodes || []).map(n => ({
  ...n,
  label: `${n.code} ${n.name}`,
  children: decorate(n.children)
}))

const parentOptions = computed(() => {
  const root = { id: 0, code: '', name: '顶级（一级分类）', label: '顶级（一级分类）', children: decorate(tree.value) }
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
const filteredTree = computed(() => filterTree(tree.value, query.kw.trim()))

const load = async () => {
  loading.value = true
  try {
    tree.value = await categoryTree({ type: query.categoryType }) || []
  } finally {
    loading.value = false
  }
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  categoryType: [{ required: true, message: '请选择分类类型', trigger: 'change' }],
  code: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const openDialog = (row, parent) => {
  if (row) {
    dlg.form = { ...row, children: undefined }
  } else {
    dlg.form = {
      categoryType: query.categoryType,
      parentId: parent ? parent.id : 0,
      code: '', name: '', remark: '', sort: 0
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
      await categoryUpdate(payload)
      ElMessage.success('修改成功')
    } else {
      await categoryCreate(payload)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除分类「${row.code} ${row.name}」吗？存在下级分类时将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => {
      await categoryDelete(row.id)
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
