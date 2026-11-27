<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">工具</h2>
        <p class="page-sub">技能 = 提示词模板 + 绑定工具 + 取数域 — 执行任务时自动调用绑定工具并把结果注入提示词（内置工具不可删改）</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">＋ 新增 HTTP 工具</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="rows" size="small" stripe>
        <el-table-column prop="name" label="名称" width="160">
          <template #default="{ row }"><b>{{ row.name }}</b></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'BUILTIN' ? 'primary' : 'success'" effect="light">
              {{ row.type === 'BUILTIN' ? '内置' : 'HTTP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono muted">{{ row.url || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <template v-if="row.type === 'BUILTIN'">
              <el-button link type="primary" size="small" @click="onTest(row)">测试调用</el-button>
              <span class="muted small">内置 · 不可删</span>
            </template>
            <template v-else>
              <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
              <el-button link size="small" @click="onTest(row)">测试</el-button>
              <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="暂无工具" :image-size="60" />
    </el-card>

    <el-alert type="info" :closable="false" show-icon class="tip">
      使用方式：在「技能市场」编辑技能 → 勾选「绑定工具」→ 如需参数（如计算器表达式）填写「工具参数 JSON」，例如
      <code>{"calc":"(100+200)*0.5"}</code>；执行任务时会看到「工具调用」步骤，结果自动进入提示词。
    </el-alert>

    <!-- 新增/编辑 HTTP 工具 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑工具 · ' + form.name : '新增 HTTP 工具'" width="600px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="工具名称" required>
          <el-input v-model="form.name" placeholder="如：公司资讯检索" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" placeholder="一句话说明该工具做什么" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="方法">
              <el-select v-model="form.method" style="width:100%">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="URL">
              <el-input v-model="form.url" placeholder="https://..." />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="请求头 JSON">
          <el-input v-model="form.headers" type="textarea" :rows="2" placeholder='{"Authorization":"Bearer xxx"}' />
        </el-form-item>
        <el-form-item label="请求体（POST）">
          <el-input v-model="form.body" type="textarea" :rows="2" placeholder='{"query":"..."}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试调用结果 -->
    <el-dialog v-model="testDlg" title="工具测试调用" width="640px">
      <div v-if="testRow" style="margin-bottom:10px" class="muted small">
        {{ testRow.name }}（{{ testRow.type === 'BUILTIN' ? '内置' : testRow.url }}）
      </div>
      <el-input v-if="testRow && testRow.toolCode === 'calc'" v-model="testCalcExpr" placeholder="输入表达式，如 (100+200)*0.5" style="margin-bottom:10px" />
      <pre class="test-out">{{ testOut || '（点击「运行」调用）' }}</pre>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { toolList, toolSave, toolDelete, toolTest } from '../../../api/digital'

const rows = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try { rows.value = (await toolList()) || [] } catch (e) { rows.value = [] }
  finally { loading.value = false }
}

const dlg = ref(false)
const saving = ref(false)
const form = ref({})
const openForm = (t) => {
  form.value = t ? { ...t } : { name: '', description: '', method: 'GET', url: '', headers: '{}', body: '' }
  dlg.value = true
}
const onSave = async () => {
  if (!form.value.name?.trim()) return ElMessage.warning('请输入工具名称')
  if (form.value.url && !/^https?:\/\//.test(form.value.url.trim())) return ElMessage.warning('URL 需以 http(s):// 开头')
  saving.value = true
  try {
    await toolSave({
      id: form.value.id || null,
      name: form.value.name.trim(),
      description: form.value.description || '',
      method: form.value.method || 'GET',
      url: (form.value.url || '').trim(),
      headers: (form.value.headers || '').trim() || '{}',
      body: form.value.body || ''
    })
    ElMessage.success('工具已保存')
    dlg.value = false
    load()
  } finally { saving.value = false }
}
const onDelete = async (t) => {
  await ElMessageBox.confirm(`确认删除工具「${t.name}」？`, '提示', { type: 'warning' })
  await toolDelete(t.id)
  ElMessage.success('已删除')
  load()
}

// 测试调用
const testDlg = ref(false)
const testRow = ref(null)
const testOut = ref('')
const testCalcExpr = ref('(100+200)*0.5')
const testing = ref(false)
const onTest = (t) => {
  testRow.value = t
  testOut.value = ''
  testDlg.value = true
}
const runTest = async () => {
  testing.value = true
  testOut.value = '调用中…'
  try {
    const args = testRow.value.toolCode === 'calc' ? { calc: testCalcExpr.value } : {}
    const r = await toolTest(testRow.value.id, args)
    testOut.value = r?.ok ? r.output : '调用失败：' + (r?.message || '未知错误')
  } catch (e) {
    testOut.value = '调用失败：' + e.message
  } finally { testing.value = false }
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.page-title { font-size: 20px; font-weight: 700; color: #0c447c; margin: 0; }
.page-sub { font-size: 13px; color: #6b7280; margin: 4px 0 0; }
.muted { color: #6b7280; }
.small { font-size: 12px; }
.mono { font-family: Consolas, monospace; font-size: 12px; }
.tip { margin-top: 14px; }
.test-out {
  margin: 0; white-space: pre-wrap; word-break: break-all; max-height: 320px; overflow: auto;
  background: #0f172a; color: #d7e3f4; border-radius: 8px; padding: 12px; font-size: 12.5px;
}
</style>
