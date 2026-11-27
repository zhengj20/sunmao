<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.kw" placeholder="发票号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-input v-model="query.supplier" placeholder="供应商" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-select v-model="query.source" placeholder="来源" clearable style="width: 120px" @change="onSearch">
            <el-option v-for="s in INV_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-select v-model="query.verifyStatus" placeholder="验真状态" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="v in Object.keys(VERIFY_STATUS)" :key="v" :label="VERIFY_STATUS[v][0]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div class="actions">
          <el-button @click="ocrVisible = true">OCR 识别</el-button>
          <el-button type="primary" @click="openForm()">新增发票</el-button>
        </div>
      </div>
    </el-card>

    <el-alert type="warning" :closable="false" show-icon class="rule-alert">
      <template #title>发票查重（R2）：同一发票（税号+发票号）在平台内仅可登记一次，重复将被阻断。</template>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="invNo" label="发票号" width="160" show-overflow-tooltip />
        <el-table-column prop="taxNo" label="发票代码/税号" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taxNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplier || '-' }}</template>
        </el-table-column>
        <el-table-column label="价税合计（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="税额（元）" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.taxAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="验真状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="meta(row.verifyStatus)[1]" size="small">{{ meta(row.verifyStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">{{ invSourceLabel(row.source) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.verifyStatus !== 'PASS'" link type="primary" size="small" @click="onVerify(row)">验真</el-button>
            <el-button v-if="row.verifyStatus === 'FAIL'" link type="warning" size="small" @click="onVerify(row)">重验</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList" @size-change="onSearch"
      />
    </div>

    <!-- 新增发票 -->
    <el-dialog v-model="formVisible" title="新增发票（登记到发票池）" width="560px">
      <el-form label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="发票号" required>
              <el-input v-model="form.invNo" maxlength="64" placeholder="如 FP2026090002" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票代码/税号" required>
              <el-input v-model="form.taxNo" maxlength="64" placeholder="纳税人识别号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="供应商">
          <el-input v-model="form.supplier" maxlength="128" placeholder="开票方名称" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="价税合计" required>
              <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税额">
              <el-input-number v-model="form.taxAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option v-for="s in INV_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <div class="hint">保存后初始为「未验真」，可点击「验真」。重复发票将被阻断（R2）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- OCR 识别（占位，待接 AI） -->
    <el-dialog v-model="ocrVisible" title="OCR 发票识别" width="480px">
      <el-alert type="info" :closable="false" show-icon class="ocr-tip">
        <template #title>当前为占位实现，待接入 ai-service（识别发票号/税号/金额/税额并回写）。</template>
      </el-alert>
      <el-form label-width="100px" class="ocr-form">
        <el-form-item label="发票号"><el-input v-model="ocr.invNo" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="ocr.taxNo" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="ocr.amount" :controls="false" :min="0" style="width: 100%" /></el-form-item>
      </el-form>
      <div v-if="ocr.result" class="ocr-result">
        <el-tag :type="ocr.result.recognized ? 'success' : 'info'" size="small">
          {{ ocr.result.recognized ? '已识别' : '待接 AI' }}
        </el-tag>
        <span class="ocr-msg">{{ ocr.result.message }}</span>
      </div>
      <template #footer>
        <el-button @click="ocrVisible = false">关闭</el-button>
        <el-button type="primary" :loading="ocr.loading" @click="onOcr">开始识别</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  invoicePage, invoiceCreate, invoiceDelete, invoiceVerify, invoiceOcr,
  VERIFY_STATUS, INV_SOURCES, invSourceLabel, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, VERIFY_STATUS)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ kw: '', supplier: '', source: null, verifyStatus: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await invoicePage({
      current: query.current, size: query.size,
      kw: query.kw || undefined, supplier: query.supplier || undefined,
      source: query.source || undefined, verifyStatus: query.verifyStatus || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.kw = ''; query.supplier = ''; query.source = null; query.verifyStatus = null; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ invNo: '', taxNo: '', supplier: '', amount: null, taxAmount: null, source: 'REIMBURSE' })
const openForm = () => {
  Object.assign(form, { invNo: '', taxNo: '', supplier: '', amount: null, taxAmount: null, source: 'REIMBURSE' })
  formVisible.value = true
}
const onSave = async () => {
  if (!form.invNo) return ElMessage.warning('请填写发票号')
  if (!form.taxNo) return ElMessage.warning('请填写发票代码/税号')
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写价税合计')
  saving.value = true
  try {
    await invoiceCreate({
      invNo: form.invNo, taxNo: form.taxNo, supplier: form.supplier || undefined,
      amount: form.amount, taxAmount: form.taxAmount || undefined, source: form.source
    })
    ElMessage.success('发票已登记到发票池')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除发票 ${row.invNo}？`, '提示', { type: 'warning' })
  await invoiceDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

// 验真（占位，待接 ai-service）：返回更新后的发票 verifyStatus
const onVerify = async row => {
  try {
    const inv = await invoiceVerify(row.id)
    ElMessage.success(`验真完成：${inv?.verifyStatus === 'PASS' ? '通过' : '未通过'}`)
    const idx = rows.value.findIndex(r => r.id === row.id)
    if (idx >= 0) rows.value[idx] = { ...rows.value[idx], verifyStatus: inv?.verifyStatus }
    loadList()
  } catch (e) { /* 后端提示已由 request.js 展示 */ }
}

// OCR 识别（占位，待接 ai-service）
const ocrVisible = ref(false)
const ocr = reactive({ invNo: '', taxNo: '', amount: null, loading: false, result: null })
const onOcr = async () => {
  ocr.loading = true
  ocr.result = null
  try {
    ocr.result = await invoiceOcr({ invNo: ocr.invNo, taxNo: ocr.taxNo, amount: ocr.amount })
  } finally {
    ocr.loading = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; }
.rule-alert { margin-bottom: 12px; }
.ocr-tip { margin-bottom: 12px; }
.ocr-form { margin-top: 4px; }
.ocr-result { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.ocr-msg { font-size: 12px; color: #909399; }
.hint { font-size: 12px; color: #909399; }
</style>
