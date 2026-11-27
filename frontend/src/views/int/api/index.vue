<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-input
            v-model="apiCode"
            placeholder="API编码"
            clearable
            style="width: 200px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">注册API</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="apiCode" label="API编码" width="180" show-overflow-tooltip />
        <el-table-column prop="apiName" label="API名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="apiPath" label="接口路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="success" class="method-tag">GET/POST</el-tag>
            <span class="path-text">{{ row.apiPath }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.purpose || '-' }}</template>
        </el-table-column>
        <el-table-column label="QPS限制" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="warning">{{ row.rateLimitQps ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ENABLED'"
              @change="v => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.updatedAt || row.createdAt) }}</template>
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

    <!-- 注册/编辑 -->
    <el-dialog v-model="formDlg" :title="form.id ? '编辑API' : '注册API'" width="540px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="API编码" required>
          <el-input v-model="form.apiCode" placeholder="如：API_SUPPLIER_SYNC" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="API名称" required>
          <el-input v-model="form.apiName" placeholder="如：供应商主数据同步" />
        </el-form-item>
        <el-form-item label="接口路径" required>
          <el-input v-model="form.apiPath" placeholder="如：/open/api/supplier/sync" />
        </el-form-item>
        <el-form-item label="QPS限制">
          <el-input-number v-model="form.rateLimitQps" :min="1" :max="10000" style="width: 180px" />
          <span class="form-tip">超过限流阈值的调用将被熔断拒绝</span>
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.purpose" type="textarea" :rows="2" maxlength="200" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { apiCreate, apiDelete, apiPage, apiUpdate } from '../../../api/int'
import { fmtTime } from '../../../utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const apiCode = ref('')
const status = ref('')
const query = reactive({ current: 1, size: 20 })

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await apiPage({
      ...query,
      apiCode: apiCode.value || undefined,
      status: status.value || undefined
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const onToggle = async (row, enabled) => {
  await apiUpdate(row.id, { ...row, status: enabled ? 'ENABLED' : 'DISABLED' })
  ElMessage.success(enabled ? '已启用' : '已停用')
  load()
}

// ============ 新增/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, apiCode: '', apiName: '', apiPath: '', purpose: '', rateLimitQps: 100, status: 'ENABLED'
})
const form = reactive(emptyForm())

const openCreate = () => {
  Object.assign(form, emptyForm())
  formDlg.value = true
}
const openEdit = row => {
  Object.assign(form, emptyForm(), row)
  formDlg.value = true
}
const save = async () => {
  if (!form.apiCode) return ElMessage.warning('请填写API编码')
  if (!form.apiName) return ElMessage.warning('请填写API名称')
  if (!form.apiPath) return ElMessage.warning('请填写接口路径')
  saving.value = true
  try {
    if (form.id) {
      await apiUpdate(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await apiCreate({ ...form })
      ElMessage.success('已注册')
    }
    formDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  try {
    await ElMessageBox.confirm(`确定删除API「${row.apiName}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.method-tag { margin-right: 8px; font-family: monospace; }
.path-text { font-family: "SF Mono", Consolas, Menlo, monospace; font-size: 12.5px; color: #185fa5; }
.form-tip { font-size: 12px; color: #9ca3af; margin-left: 10px; }
</style>
