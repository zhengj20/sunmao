<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable clearable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="query.building" placeholder="楼栋" clearable style="width: 130px" @keyup.enter="onSearch" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(IMAGE_STATUS)" :key="v" :label="IMAGE_STATUS[v][0]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm">新增确认单</el-button>
      </div>
    </el-card>

    <el-alert type="info" :closable="false" class="r4-alert"
      title="形象进度确认单用于成本进度款校验(R4)：进度款申请须关联已确认单，且申请部位%不得高于已确认%。" />

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe size="small" empty-text="暂无形象进度确认单">
        <el-table-column prop="building" label="楼栋" width="120" align="center">
          <template #default="{ row }">{{ row.building || '-' }}</template>
        </el-table-column>
        <el-table-column prop="partName" label="部位" width="110" align="center">
          <template #default="{ row }">{{ row.partName || '-' }}</template>
        </el-table-column>
        <el-table-column label="已确认%" width="160" align="center">
          <template #default="{ row }"><el-progress :percentage="Number(row.confirmedPct) || 0" :stroke-width="14" /></template>
        </el-table-column>
        <el-table-column label="确认日期" width="130" align="center">
          <template #default="{ row }">{{ fmtDate(row.confirmDate) }}</template>
        </el-table-column>
        <el-table-column prop="imageIds" label="影像附件" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.imageIds || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="onConfirm(row)">确认</el-button>
            <span v-else class="hint">已确认</span>
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

    <!-- 新增确认单 -->
    <el-dialog v-model="formVisible" title="新增形象进度确认单" width="520px">
      <el-form label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="楼栋" required>
              <el-input v-model="form.building" placeholder="如 1# / 2#" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部位" required>
              <el-select v-model="form.partName" style="width: 100%">
                <el-option v-for="s in IMAGE_PARTS" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="已确认%" required>
          <el-slider v-model="form.confirmedPct" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="确认日期">
          <el-date-picker v-model="form.confirmDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="影像附件">
          <el-input v-model="form.imageIds" placeholder="影像文件ID，多个逗号分隔（可选）" />
        </el-form-item>
        <el-form-item label="提示"><div class="hint">确认后状态置 CONFIRMED 并记录确认日期，供成本进度款 R4 校验带出已确认%。</div></el-form-item>
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
import { fmtDate, fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions,
  imagePage, imageCreate, imageConfirm,
  IMAGE_STATUS, IMAGE_PARTS, statusMeta
} from '../../../api/plan'

const meta = s => statusMeta(s, IMAGE_STATUS)

const projects = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ projectId: null, building: '', status: null, current: 1, size: 20 })
const saving = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const page = await imagePage({
      current: query.current, size: query.size,
      projectId: query.projectId || undefined, building: query.building || undefined, status: query.status || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.building = ''; query.status = null; onSearch() }

const formVisible = ref(false)
const form = reactive({ projectId: null, building: '', partName: '基础', confirmedPct: 0, confirmDate: null, imageIds: '' })
const openForm = () => {
  if (!query.projectId) return ElMessage.warning('请先选择项目')
  Object.assign(form, { projectId: query.projectId, building: query.building, partName: '基础', confirmedPct: 0, confirmDate: null, imageIds: '' })
  formVisible.value = true
}
const onSave = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (!form.building) return ElMessage.warning('请填写楼栋')
  if (!form.partName) return ElMessage.warning('请选择部位')
  saving.value = true
  try {
    await imageCreate({
      projectId: form.projectId, building: form.building, partName: form.partName,
      confirmedPct: Number(form.confirmedPct), confirmDate: form.confirmDate || undefined,
      imageIds: form.imageIds || undefined, status: 'PENDING'
    })
    ElMessage.success('确认单已创建（待确认）')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}
const onConfirm = async row => {
  await ElMessageBox.confirm(`确认形象进度：${row.building} / ${row.partName} / ${row.confirmedPct}%？`, '提示', { type: 'success' })
  await imageConfirm(row.id)
  ElMessage.success('已确认，可供成本进度款 R4 校验')
  loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    loadList()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.r4-alert { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; }
</style>
