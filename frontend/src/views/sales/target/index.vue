<template>
  <div class="page-container target">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.periodType" placeholder="周期类型" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(TARGET_PERIOD)" :key="v" :label="TARGET_PERIOD[v]" :value="v" />
          </el-select>
          <el-select v-model="query.metric" placeholder="指标口径" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(TARGET_METRIC)" :key="v" :label="TARGET_METRIC[v]" :value="v" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增目标</el-button>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <el-card v-else shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="周期类型" width="100" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ TARGET_PERIOD[row.periodType] || row.periodType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="period" label="周期" width="120" align="center" />
        <el-table-column label="指标口径" width="100" align="center">
          <template #default="{ row }">{{ TARGET_METRIC[row.metric] || row.metric }}</template>
        </el-table-column>
        <el-table-column label="目标值" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ fmtMetric(row.metric, row.targetValue) }}</span></template>
        </el-table-column>
        <el-table-column label="实际完成" width="150" align="right">
          <template #default="{ row }"><span class="num c-green">{{ fmtMetric(row.metric, row.actualValue) }}</span></template>
        </el-table-column>
        <el-table-column label="达成率" min-width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.min(Number(row.achievementRate) || 0, 100)"
              :stroke-width="14"
              :color="(Number(row.achievementRate) || 0) >= 100 ? '#67c23a' : (Number(row.achievementRate) || 0) >= 60 ? '#185fa5' : '#e6a23c'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="agentName" label="顾问" width="110" align="center"><template #default="{ row }">{{ row.agentName || '整体' }}</template></el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '生效' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="load" @size-change="onSearch"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑销售目标' : '新增销售目标'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="周期类型" required>
              <el-select v-model="form.periodType" style="width: 100%">
                <el-option v-for="v in Object.keys(TARGET_PERIOD)" :key="v" :label="TARGET_PERIOD[v]" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="周期" required><el-input v-model="form.period" :placeholder="periodHint(form.periodType)" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标口径" required>
              <el-select v-model="form.metric" style="width: 100%">
                <el-option v-for="v in Object.keys(TARGET_METRIC)" :key="v" :label="TARGET_METRIC[v]" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标值" required><el-input-number v-model="form.targetValue" :controls="false" :min="0" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="生效" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="顾问(可选)">
              <el-select v-model="form.agentId" placeholder="项目整体" clearable filterable style="width: 100%">
                <el-option v-for="u in advisors" :key="u.id" :label="u.realName || u.name" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchProjectOptions, targetPage, targetCreate, targetUpdate, targetDelete,
  TARGET_PERIOD, TARGET_METRIC
} from '../../../api/sales'
import request from '../../../api/request'

const fmtMetric = (metric, v) => {
  if (v === null || v === undefined || v === '') return '-'
  if (metric === 'AMOUNT') return wan(v) + ' 万'
  if (metric === 'AREA') return money(v) + ' ㎡'
  if (metric === 'COUNT') return Number(v) + ' 套'
  return money(v)
}
const wan = v => money(Number(v) / 10000)

const projects = ref([])
const advisors = ref([])
const projectId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ periodType: null, metric: null, current: 1, size: 20 })

const periodHint = type =>
  type === 'QUARTER' ? '如 2026-Q3' : type === 'YEAR' ? '如 2026' : '如 2026-09'

const load = async () => {
  if (!projectId.value) { rows.value = []; return }
  loading.value = true
  try {
    const page = await targetPage({
      current: query.current, size: query.size, projectId: projectId.value,
      periodType: query.periodType || undefined, metric: query.metric || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; load() }

const form = reactive({
  visible: false, saving: false, id: null, periodType: 'MONTH', period: '', metric: 'AMOUNT',
  targetValue: null, status: 'ACTIVE', agentId: null
})
const openForm = row => {
  Object.assign(form, {
    visible: true, saving: false, id: row?.id || null,
    periodType: row?.periodType || 'MONTH', period: row?.period || '', metric: row?.metric || 'AMOUNT',
    targetValue: row?.targetValue ?? null, status: row?.status || 'ACTIVE', agentId: row?.agentId || null
  })
}
const onSave = async () => {
  if (!form.period) return ElMessage.warning('请填写周期')
  if (form.targetValue == null) return ElMessage.warning('请填写目标值')
  form.saving = true
  try {
    const payload = {
      id: form.id, projectId: projectId.value, periodType: form.periodType, period: form.period,
      metric: form.metric, targetValue: form.targetValue, status: form.status, agentId: form.agentId
    }
    if (form.id) { await targetUpdate(payload); ElMessage.success('已更新') }
    else { await targetCreate(payload); ElMessage.success('已创建') }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除目标【${row.period} · ${TARGET_METRIC[row.metric] || ''}】？`, '提示', { type: 'warning' })
  await targetDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  const users = await request.get('/sys/users/page', { params: { current: 1, size: 200 } }).catch(() => null)
  advisors.value = users?.records || []
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { margin-bottom: 12px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 12px; }
.num { font-variant-numeric: tabular-nums; }
.c-green { color: #67c23a; }
</style>
