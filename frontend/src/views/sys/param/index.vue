<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent>
          <el-form-item label="关键字">
            <el-input v-model="kw" placeholder="参数名称/参数键" clearable style="width: 240px" :prefix-icon="Search" />
          </el-form-item>
        </el-form>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredRows" border style="width: 100%">
        <el-table-column prop="name" label="参数名称" width="200" show-overflow-tooltip />
        <el-table-column prop="key" label="参数键" min-width="220" show-overflow-tooltip />
        <el-table-column prop="value" label="参数值" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="num">{{ row.value ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filteredRows.length" description="暂无参数" :image-size="70" />
    </el-card>

    <div class="pagination-bar">
      <span style="margin-right: auto; color: #6b7280; font-size: 12.5px;">共 {{ filteredRows.length }} 项系统参数（业务规则阈值配置化）</span>
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="filteredRows.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>

    <el-dialog v-model="dlg.visible" title="修改系统参数" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="参数名称">
          <el-input :model-value="dlg.row?.name" disabled />
        </el-form-item>
        <el-form-item label="参数键">
          <el-input :model-value="dlg.row?.key" disabled />
        </el-form-item>
        <el-form-item label="参数值" required>
          <el-input
            v-model="dlg.value"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            placeholder="请输入参数值"
          />
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
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { paramList, paramSave } from '../../../api/sys'

const loading = ref(false)
const rows = ref([])
const kw = ref('')
const page = reactive({ current: 1, size: 20 })

const filteredRows = computed(() =>
  rows.value.filter(r => !kw.value || r.name?.includes(kw.value) || r.key?.includes(kw.value))
)

const load = async () => {
  loading.value = true
  try {
    rows.value = await paramList() || []
  } finally {
    loading.value = false
  }
}

// ================= 弹窗 =================
const dlg = reactive({ visible: false, saving: false, row: null, value: '' })

const openDialog = row => {
  dlg.row = row
  dlg.value = row.value ?? ''
  dlg.visible = true
}

const onSave = async () => {
  if (dlg.value === '' || dlg.value === null) return ElMessage.warning('参数值不能为空')
  dlg.saving = true
  try {
    await paramSave(dlg.row.key, dlg.value)
    ElMessage.success('保存成功')
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
