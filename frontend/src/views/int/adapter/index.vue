<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-input
            v-model="adapterCode"
            placeholder="适配器编码"
            clearable
            style="width: 180px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="adapterType" placeholder="适配器类型" clearable style="width: 150px" @change="search">
            <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="status" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增适配器</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="adapterCode" label="适配器编码" width="150" show-overflow-tooltip />
        <el-table-column prop="adapterName" label="适配器名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="typeTagColor(row.adapterType)">
              {{ TYPE_LABEL[row.adapterType] || row.adapterType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="vendor" label="厂商" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.vendor || '-' }}</template>
        </el-table-column>
        <el-table-column label="同步方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.syncDirection === 'BOTH'" size="small" type="primary" effect="light">双向</el-tag>
            <el-tag v-else-if="row.syncDirection === 'PUSH'" size="small" type="warning" effect="light">推送</el-tag>
            <el-tag v-else size="small" type="success" effect="light">拉取</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接参数" min-width="220">
          <template #default="{ row }">
            <code class="config-json">{{ prettyJson(row.configJson) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="最近同步" width="150">
          <template #default="{ row }">{{ fmtTime(row.lastSyncAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 'ENABLED'" @change="v => onToggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="success" link @click="onTest(row)">测试连接</el-button>
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
    <el-dialog v-model="formDlg" :title="form.id ? '编辑适配器' : '新增适配器'" width="580px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="适配器编码" required>
          <el-input v-model="form.adapterCode" placeholder="如：ADP_KINGDEE" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="适配器名称" required>
          <el-input v-model="form.adapterName" placeholder="如：金蝶财务适配器" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="适配器类型" required>
              <el-select v-model="form.adapterType" style="width: 100%">
                <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厂商">
              <el-input v-model="form.vendor" placeholder="如：金蝶" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="同步方向">
              <el-select v-model="form.syncDirection" style="width: 100%">
                <el-option label="推送（PUSH）" value="PUSH" />
                <el-option label="拉取（PULL）" value="PULL" />
                <el-option label="双向（BOTH）" value="BOTH" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="formEnabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="连接参数">
          <el-input
            v-model="form.configJson"
            type="textarea"
            :rows="5"
            placeholder='JSON 格式，如：{"host":"https://api.example","org":"SUNMAO"}'
          />
          <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
          <div v-else class="form-tip">参数化配置，支持城市/版本差异；保存前将校验 JSON 格式</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
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
import { Plus, Search } from '@element-plus/icons-vue'
import { adapterCreate, adapterDelete, adapterPage, adapterTest, adapterUpdate } from '../../../api/int'
import { fmtTime } from '../../../utils/format'

const TYPE_LABEL = {
  FINANCE: '财务', TAX: '税务', NETSIGN: '网签', MORTGAGE: '按揭', IM: '企微钉钉'
}
const typeTagColor = t => ({ FINANCE: '', TAX: 'warning', NETSIGN: 'success', MORTGAGE: 'danger', IM: 'info' }[t] || 'info')

const prettyJson = text => {
  if (!text) return '-'
  try {
    return JSON.stringify(JSON.parse(text))
  } catch {
    return text
  }
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const adapterCode = ref('')
const adapterType = ref('')
const status = ref('')
const query = reactive({ current: 1, size: 20 })

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await adapterPage({
      ...query,
      adapterCode: adapterCode.value || undefined,
      adapterType: adapterType.value || undefined,
      status: status.value || undefined
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const onToggle = async (row, enabled) => {
  await adapterUpdate(row.id, { ...row, status: enabled ? 'ENABLED' : 'DISABLED' })
  ElMessage.success(enabled ? '已启用' : '已停用')
  load()
}

const onTest = async row => {
  const r = await adapterTest(row.id)
  if (r && r.success) {
    ElMessage.success(`连接成功：${r.message || '适配器连通正常'}`)
  } else {
    ElMessage.error(`连接失败：${r?.message || '未知原因'}`)
  }
}

// ============ 新增/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, adapterCode: '', adapterName: '', adapterType: 'FINANCE', vendor: '',
  configJson: '', syncDirection: 'BOTH', status: 'ENABLED', remark: ''
})
const form = reactive(emptyForm())
const formEnabled = computed({
  get: () => form.status === 'ENABLED',
  set: v => { form.status = v ? 'ENABLED' : 'DISABLED' }
})
const jsonError = computed(() => {
  if (!form.configJson?.trim()) return ''
  try {
    JSON.parse(form.configJson)
    return ''
  } catch {
    return 'JSON 格式不正确，请检查'
  }
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
  if (!form.adapterCode) return ElMessage.warning('请填写适配器编码')
  if (!form.adapterName) return ElMessage.warning('请填写适配器名称')
  if (jsonError.value) return ElMessage.warning('连接参数 JSON 格式不正确')
  saving.value = true
  try {
    if (form.id) {
      await adapterUpdate(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await adapterCreate({ ...form })
      ElMessage.success('已新增')
    }
    formDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  try {
    await ElMessageBox.confirm(`确定删除适配器「${row.adapterName}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await adapterDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.config-json {
  font-family: "SF Mono", Consolas, Menlo, monospace;
  font-size: 12px; color: #4b5563; background: #f0f5fa;
  border-radius: 4px; padding: 2px 8px;
  display: inline-block; max-width: 100%;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.form-tip { font-size: 12px; color: #9ca3af; }
.json-error { font-size: 12px; color: #f56c6c; margin-top: 4px; }
</style>
