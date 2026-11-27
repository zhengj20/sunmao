<template>
  <div class="page-container cockpit-metric">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="维度">
            <el-select v-model="query.dimension" placeholder="全部" clearable filterable style="width: 130px" @change="load">
              <el-option label="集团" value="集团" />
              <el-option label="区域" value="区域" />
              <el-option label="项目" value="项目" />
              <el-option label="月度" value="月度" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="load">
              <el-option label="当前" value="ACTIVE" />
              <el-option label="历史" value="HISTORY" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="query.kw" placeholder="编码/名称" clearable style="width: 180px" :prefix-icon="Search" @clear="load" @keyup.enter="load" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增指标</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe style="width: 100%">
        <el-table-column prop="metricCode" label="编码" width="150" show-overflow-tooltip />
        <el-table-column prop="metricName" label="名称" width="130" show-overflow-tooltip />
        <el-table-column prop="formula" label="口径公式" min-width="240" show-overflow-tooltip />
        <el-table-column prop="sourceTable" label="来源表" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceTable || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dimension" label="维度(含周期)" width="120" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.dimension || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="100" align="center">
          <template #default="{ row }">{{ row.owner || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">{{ row.status === 'ACTIVE' ? '当前' : '历史' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" width="120" align="center">
          <template #default="{ row }">{{ fmtDate(row.effectiveDate) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="70" align="center">
          <template #default="{ row }">{{ row.versionNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="dlg.visible" :title="dlg.form.id ? '编辑指标' : '新增指标'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="dlg.form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="指标编码" prop="metricCode">
              <el-input v-model="dlg.form.metricCode" placeholder="如 SIGN_AMOUNT" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标名称" prop="metricName">
              <el-input v-model="dlg.form.metricName" placeholder="如 签约额" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="口径公式" prop="formula">
              <el-input v-model="dlg.form.formula" type="textarea" :rows="2" placeholder="口径公式文本" maxlength="500" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源表" prop="sourceTable">
              <el-input v-model="dlg.form.sourceTable" placeholder="主数据源表" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维度" prop="dimension">
              <el-input v-model="dlg.form.dimension" placeholder="如 集团/项目/月度" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="owner">
              <el-input v-model="dlg.form.owner" placeholder="负责人" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="dlg.form.status" style="width: 100%">
                <el-option label="当前 ACTIVE" value="ACTIVE" />
                <el-option label="历史 HISTORY" value="HISTORY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="dlg.form.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { metricPage, metricDetail, metricCreate, metricUpdate, metricDelete } from '../../../api/cockpit'
import { fmtDate } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const query = reactive({ dimension: '', status: '', kw: '' })
const page = reactive({ current: 1, size: 20, total: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = await metricPage({
      current: page.current, size: page.size,
      dimension: query.dimension || undefined,
      status: query.status || undefined,
      kw: query.kw || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.dimension = ''
  query.status = ''
  query.kw = ''
  page.current = 1
  load()
}

// ================= 弹窗 =================
const formRef = ref()
const dlg = reactive({ visible: false, saving: false, form: {} })
const rules = {
  metricCode: [{ required: true, message: '请填写指标编码', trigger: 'blur' }],
  metricName: [{ required: true, message: '请填写指标名称', trigger: 'blur' }]
}

const openDialog = async row => {
  if (row) {
    const d = await metricDetail(row.id)
    dlg.form = {
      id: d.id, metricCode: d.metricCode, metricName: d.metricName, formula: d.formula,
      sourceTable: d.sourceTable, dimension: d.dimension, owner: d.owner,
      status: d.status || 'ACTIVE', effectiveDate: d.effectiveDate, versionNo: d.versionNo
    }
  } else {
    dlg.form = {
      id: null, metricCode: '', metricName: '', formula: '',
      sourceTable: '', dimension: '集团', owner: '', status: 'ACTIVE', effectiveDate: ''
    }
  }
  dlg.visible = true
}

const onSave = async () => {
  await formRef.value.validate()
  dlg.saving = true
  try {
    const { id, versionNo, ...form } = dlg.form
    if (form.effectiveDate === '' ) form.effectiveDate = null
    if (id) {
      await metricUpdate(id, form)
      ElMessage.success('修改成功')
    } else {
      await metricCreate(form)
      ElMessage.success('新增成功')
    }
    dlg.visible = false
    load()
  } finally {
    dlg.saving = false
  }
}

const onDelete = row => {
  ElMessageBox.confirm(`确定删除指标「${row.metricName || row.metricCode}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await metricDelete(row.id)
      ElMessage.success('删除成功')
      load()
    }).catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
.pagination-bar { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
