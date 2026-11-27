<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.kw" placeholder="模板名称" clearable style="width: 220px" @keyup.enter="onSearch" />
          <el-select v-model="query.contractType" placeholder="适用类型" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="(label, key) in DICTS.contract_type" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openUpload">上传模板</el-button>
      </div>
    </el-card>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      上传含占位符（如 {{合同编号}}、{{承包商}}、{{签约金额}}）的 .docx 合同模板；在「合同管理」页可对任一合同「生成合同」，系统会用合同数据填充占位符，支持在线预览与下载。
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="name" label="模板名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="适用类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.contractType" size="small" effect="plain">{{ dictLabel('contract_type', row.contractType) }}</el-tag>
            <span v-else class="muted">通用</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="90" align="right">
          <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="占位符" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-for="p in placeholdersOf(row)" :key="p" class="ph-tag">{{ p }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
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

    <!-- 上传模板 -->
    <el-dialog v-model="uploadVisible" title="上传合同模板" width="560px">
      <el-form :model="upForm" label-width="90px">
        <el-form-item label="模板文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".docx"
            :on-change="onFileChange"
            :on-exceed="() => ElMessage.warning('一次仅上传一个 .docx 模板')"
          >
            <el-button>选择 .docx 文件</el-button>
            <template #tip><div class="el-upload__tip">仅支持 Word .docx 格式；正文中可用 {{合同编号}} 等占位符</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="upForm.name" maxlength="100" placeholder="如：施工总承包合同模板" />
        </el-form-item>
        <el-form-item label="适用类型">
          <el-select v-model="upForm.contractType" clearable style="width: 100%">
            <el-option v-for="(label, key) in DICTS.contract_type" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="upForm.description" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-drawer v-model="previewVisible" :title="previewTitle" size="70%">
      <div class="preview-toolbar">
        <el-button size="small" @click="download(previewRow)" :disabled="!previewRow">下载原文件</el-button>
      </div>
      <div class="preview-body" v-if="previewRow">
        <DocPreview :file-url="fileUrl(previewRow.filePath)" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictLabel, DICTS } from '../../../utils/format'
import { fetchTemplates, deleteTemplate, uploadTemplate, fileUrl } from '../../../api/cost'
import DocPreview from '../../../components/DocPreview.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ kw: '', contractType: null, current: 1, size: 20 })

const uploadVisible = ref(false)
const upForm = reactive({ name: '', contractType: null, description: '' })
const fileRef = ref(null)
const uploadRef = ref(null)

const previewVisible = ref(false)
const previewRow = ref(null)
const previewTitle = ref('模板预览')

const fmtSize = n => {
  if (n == null) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

const placeholdersOf = row => {
  try {
    const arr = row.placeholders ? JSON.parse(row.placeholders) : []
    return arr.slice(0, 8)
  } catch { return [] }
}

const loadList = async () => {
  loading.value = true
  try {
    const page = await fetchTemplates(query)
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.kw = ''; query.contractType = null; onSearch() }

const openUpload = () => {
  upForm.name = ''; upForm.contractType = null; upForm.description = ''; fileRef.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
  uploadVisible.value = true
}

const onFileChange = file => { fileRef.value = file.raw }

const onUpload = async () => {
  if (!fileRef.value) return ElMessage.warning('请选择 .docx 模板文件')
  if (!upForm.name) return ElMessage.warning('请填写模板名称')
  saving.value = true
  try {
    await uploadTemplate(fileRef.value, { name: upForm.name, contractType: upForm.contractType, description: upForm.description })
    ElMessage.success('模板上传成功')
    uploadVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const openPreview = row => {
  previewRow.value = row
  previewTitle.value = '模板预览：' + row.name
  previewVisible.value = true
}

const download = row => {
  if (!row) return
  const a = document.createElement('a')
  a.href = fileUrl(row.filePath)
  a.download = row.fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除模板【${row.name}】？`, '提示', { type: 'warning' })
  await deleteTemplate(row.id)
  ElMessage.success('已删除')
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.muted { color: #a0a4ab; }
.ph-tag {
  display: inline-block; margin: 2px 4px 2px 0; padding: 1px 8px;
  font-size: 12px; color: #409eff; background: #ecf5ff; border: 1px solid #d9ecff; border-radius: 4px;
}
.preview-toolbar { padding: 0 4px 12px; }
.preview-body { height: calc(100% - 52px); }
</style>
