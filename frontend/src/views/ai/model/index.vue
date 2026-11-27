<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.provider" placeholder="Provider" clearable style="width: 160px" @change="onSearch">
            <el-option v-for="p in MODEL_PROVIDERS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="k in Object.keys(MODEL_STATUS)" :key="k" :label="MODEL_STATUS[k][0]" :value="k" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div class="actions">
          <el-button type="primary" @click="openForm()">新增配置</el-button>
        </div>
      </div>
    </el-card>

    <el-alert type="info" :closable="false" show-icon class="guide">
      <template #title>如何接入通义 / DeepSeek：选择 Provider，填写「模型名 + OpenAI 兼容 base_url（含 /v1）+ API Key」，置「启用」后点「测试连接」，成功即升级为 LLM 模式。</template>
      <template #default>
        <div class="guide-list">
          <div>通义千问：base_url = <code>https://dashscope.aliyuncs.com/compatible-mode/v1</code>，model 如 <code>qwen-max</code></div>
          <div>DeepSeek：base_url = <code>https://api.deepseek.com/v1</code>，model 如 <code>deepseek-chat</code></div>
        </div>
      </template>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="provider" label="Provider" width="150" align="center">
          <template #default="{ row }">{{ providerLabel(row.provider) }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.modelName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.baseUrl || '-' }}</template>
        </el-table-column>
        <el-table-column label="API Key" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.apiKeyMasked" class="muted key">{{ row.apiKeyMasked }}</span>
            <span v-else class="muted">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="超时(ms)" width="100" align="center">
          <template #default="{ row }">{{ row.timeoutMs ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="温度" width="70" align="center">
          <template #default="{ row }">{{ row.temperature ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(row.status, MODEL_STATUS)[1]">{{ statusMeta(row.status, MODEL_STATUS)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" :loading="testLoading === row.id" @click="onTest(row)">测试连接</el-button>
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 测试结果 -->
    <div v-if="testResult" class="test-result">
      <el-tag :type="testResult.success ? 'success' : 'danger'" size="small">{{ testResult.success ? '连接成功' : '连接失败' }}</el-tag>
      <span class="test-model">{{ testResult.model }}</span>
      <span class="muted">耗时 {{ testResult.durationMs }} ms</span>
      <span v-if="testResult.degraded" class="muted">（降级）</span>
      <span :class="testResult.success ? 'muted' : 'test-err'">—— {{ testResult.message }}</span>
    </div>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑配置' : '新增配置'" width="560px">
      <el-form label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="Provider" required>
              <el-select v-model="form.provider" style="width: 100%">
                <el-option v-for="p in MODEL_PROVIDERS" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名" required>
              <el-input v-model="form.modelName" placeholder="如 qwen-max / deepseek-chat" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="OpenAI 兼容地址（含 /v1）" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="form.id ? '留空表示不修改密钥' : '密钥（加密存储，展示脱敏）'" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="超时(ms)">
              <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" placeholder="可选" />
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
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  modelPage, modelCreate, modelUpdate, modelDelete, modelTest,
  MODEL_PROVIDERS, MODEL_STATUS, providerLabel, statusMeta
} from '../../../api/ai'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ provider: null, status: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await modelPage({
      current: query.current, size: query.size,
      provider: query.provider || undefined, status: query.status || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.provider = null; query.status = null; onSearch() }

// 测试连接
const testLoading = ref(null)
const testResult = ref(null)
const onTest = async row => {
  testLoading.value = row.id
  testResult.value = null
  try {
    const res = await modelTest(row.id)
    testResult.value = res
    ElMessage[res.success ? 'success' : 'error'](`连接${res.success ? '成功' : '失败'}，耗时 ${res.durationMs} ms${res.message ? '：' + res.message : ''}`)
  } catch (e) {
    /* 后端提示已由 request.js 展示 */
  } finally {
    testLoading.value = null
  }
}

const formVisible = ref(false)
const saving = ref(false)
const empty = () => ({ id: null, provider: 'OPENAI_COMPATIBLE', modelName: '', baseUrl: '', apiKey: '', timeoutMs: 60000, temperature: 0.7, status: 'INACTIVE', remark: '' })
const form = reactive(empty())
const openForm = row => {
  Object.assign(form, row
    ? { id: row.id, provider: row.provider, modelName: row.modelName, baseUrl: row.baseUrl, apiKey: '', timeoutMs: row.timeoutMs, temperature: row.temperature, status: row.status, remark: row.remark }
    : empty())
  formVisible.value = true
}
const onSave = async () => {
  if (!form.provider) return ElMessage.warning('请选择 Provider')
  if (!form.modelName) return ElMessage.warning('请填写模型名')
  saving.value = true
  try {
    if (form.id) {
      await modelUpdate(form.id, {
        provider: form.provider, modelName: form.modelName, baseUrl: form.baseUrl,
        apiKey: form.apiKey || undefined, timeoutMs: form.timeoutMs, temperature: form.temperature,
        status: form.status, remark: form.remark
      })
    } else {
      await modelCreate({
        provider: form.provider, modelName: form.modelName, baseUrl: form.baseUrl || undefined,
        apiKey: form.apiKey || undefined, timeoutMs: form.timeoutMs, temperature: form.temperature,
        status: form.status, remark: form.remark || undefined
      })
    }
    ElMessage.success('已保存')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除「${providerLabel(row.provider)} / ${row.modelName}」？`, '提示', { type: 'warning' })
  await modelDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; }
.guide { margin-bottom: 12px; }
.guide-list { margin-top: 6px; font-size: 12.5px; line-height: 1.8; }
.guide-list code { background: #eef5fb; padding: 1px 5px; border-radius: 4px; color: #185fa5; }
.key { font-family: "SF Mono", Consolas, Menlo, monospace; }
.muted { color: #909399; }
.test-result { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 14px; background: #fff; border-radius: 6px; }
.test-model { font-weight: 600; }
.test-err { color: #c45656; font-weight: 600; }
</style>
