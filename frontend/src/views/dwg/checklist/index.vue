<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.discipline" placeholder="专业" clearable style="width: 140px" @change="onSearch">
            <el-option label="通用（ALL）" value="ALL" />
            <el-option v-for="d in DISCIPLINES" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
          <el-select v-model="query.level" placeholder="严重度" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="l in LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
          <el-input v-model="query.kw" placeholder="关键词" clearable style="width: 200px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增审查要点</el-button>
      </div>
    </el-card>

    <el-alert type="success" :closable="false" show-icon class="hint-alert">
      审查要点库是「AI/系统预审」的规则源：AI 预审时按图纸的<strong>专业 + 阶段</strong>匹配下列启用要点生成审查问题。
      「通用」对所有专业生效；「阶段」留空对所有阶段生效。停用（enabled=0）的要点不会参与预审。
    </el-alert>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="专业" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.discipline === 'ALL' ? 'warning' : ''">{{ row.discipline === 'ALL' ? '通用' : disciplineLabel(row.discipline) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="阶段" width="90" align="center">
          <template #default="{ row }">{{ row.stage ? stageLabel(row.stage) : '全阶段' }}</template>
        </el-table-column>
        <el-table-column label="严重度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelMeta(row.level).type" size="small">{{ levelMeta(row.level).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110" align="center">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="审查要点" min-width="260" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="修改建议" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.suggestion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="editId ? '编辑审查要点' : '新增审查要点'" width="640px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="专业">
              <el-select v-model="form.discipline" style="width: 100%">
                <el-option label="通用（ALL）" value="ALL" />
                <el-option v-for="d in DISCIPLINES" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="阶段">
              <el-select v-model="form.stage" clearable placeholder="全阶段" style="width: 100%">
                <el-option v-for="s in STAGES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="严重度">
              <el-select v-model="form.level" style="width: 100%">
                <el-option v-for="l in LEVELS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" allow-create filterable clearable style="width: 100%">
                <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="审查要点" required>
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" placeholder="描述该专业/阶段需核查的要点" />
        </el-form-item>
        <el-form-item label="修改建议">
          <el-input v-model="form.suggestion" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchChecklistPage, addChecklist, updateChecklist, deleteChecklist, toggleChecklist,
  DISCIPLINES, STAGES, LEVELS, CATEGORIES, disciplineLabel, stageLabel, levelMeta
} from '../../../api/dwg'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ discipline: null, level: null, kw: '', current: 1, size: 20 })

const formVisible = ref(false)
const editId = ref(null)
const form = reactive({ discipline: 'ALL', stage: null, level: 'MEDIUM', category: '规范符合性', description: '', suggestion: '', sort: 0, enabled: 1 })

const loadList = async () => {
  loading.value = true
  try {
    const p = await fetchChecklistPage(query)
    rows.value = p?.records || []
    total.value = Number(p?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.discipline = null; query.level = null; query.kw = ''; onSearch() }

const openForm = (row) => {
  if (row) {
    editId.value = row.id
    Object.assign(form, {
      discipline: row.discipline, stage: row.stage || null, level: row.level,
      category: row.category || '规范符合性', description: row.description,
      suggestion: row.suggestion || '', sort: row.sort || 0, enabled: row.enabled ?? 1
    })
  } else {
    editId.value = null
    Object.assign(form, { discipline: 'ALL', stage: null, level: 'MEDIUM', category: '规范符合性', description: '', suggestion: '', sort: 0, enabled: 1 })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.description) return ElMessage.warning('请填写审查要点描述')
  saving.value = true
  try {
    if (editId.value) {
      await updateChecklist(editId.value, { ...form })
      ElMessage.success('要点已更新')
    } else {
      await addChecklist({ ...form })
      ElMessage.success('要点已新增')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onToggle = async row => {
  await toggleChecklist(row.id)
  ElMessage.success(row.enabled === 1 ? '已停用' : '已启用')
  await loadList()
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除审查要点「${row.description.slice(0, 20)}…」？`, '提示', { type: 'warning' })
  await deleteChecklist(row.id)
  ElMessage.success('已删除')
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint-alert { margin-bottom: 12px; }
.pagination-bar { margin-top: 12px; text-align: right; }
</style>
