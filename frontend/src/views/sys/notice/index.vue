<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="noticeType" placeholder="公告类型" clearable style="width: 150px" @change="search">
            <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="status" placeholder="状态" clearable style="width: 130px" @change="search">
            <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">发布公告</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="置顶" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.pinned === 1" @change="v => onTogglePin(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="公告标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TYPE_LABEL[row.noticeType] || row.noticeType || '通知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布人" width="110">
          <template #default="{ row }">{{ row.publisherName || '-' }}</template>
        </el-table-column>
        <el-table-column label="发布时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.publishAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagColor(row.status)">{{ STATUS_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
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
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formDlg" :title="form.id ? '编辑公告' : '发布公告'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="公告标题" required>
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="公告正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="请输入公告正文"
          />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="公告类型">
              <el-select v-model="form.noticeType" style="width: 100%">
                <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="置顶">
          <el-switch v-model="formPinned" active-text="是" inactive-text="否" />
          <span class="form-tip">置顶公告将优先展示在门户工作台</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { fetchNotices, createNotice, updateNotice, deleteNotice, togglePin } from '../../../api/notice'
import { fmtTime } from '../../../utils/format'

const TYPE_LABEL = { NOTICE: '通知', ANNOUNCEMENT: '公告', MAINTENANCE: '维护' }
const STATUS_LABEL = { PUBLISHED: '已发布', DRAFT: '草稿', ARCHIVED: '已归档' }
const statusTagColor = s => ({ PUBLISHED: 'success', DRAFT: 'info', ARCHIVED: 'warning' }[s] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const noticeType = ref('')
const status = ref('')
const query = reactive({ current: 1, size: 20 })

const search = () => {
  query.current = 1
  load()
}

const resetQuery = () => {
  noticeType.value = ''
  status.value = ''
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await fetchNotices({
      ...query,
      noticeType: noticeType.value || undefined,
      status: status.value || undefined
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const onTogglePin = async (row, pinned) => {
  await togglePin(row.id, pinned)
  ElMessage.success(pinned ? '已置顶' : '已取消置顶')
  load()
}

// ============ 新增/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, title: '', content: '', noticeType: 'NOTICE', status: 'PUBLISHED', pinned: 0
})
const form = reactive(emptyForm())
const formPinned = computed({
  get: () => form.pinned === 1,
  set: v => { form.pinned = v ? 1 : 0 }
})

const openCreate = () => {
  Object.assign(form, emptyForm())
  formDlg.value = true
}

const openEdit = row => {
  Object.assign(form, emptyForm(), row)
  formDlg.value = true
}

const save = async () => {
  if (!form.title?.trim()) return ElMessage.warning('请填写公告标题')
  if (!form.content?.trim()) return ElMessage.warning('请填写公告正文')
  saving.value = true
  try {
    if (form.id) {
      await updateNotice(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await createNotice({ ...form })
      ElMessage.success('已发布')
    }
    formDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.form-tip { font-size: 12px; color: #9ca3af; margin-left: 8px; }
</style>
