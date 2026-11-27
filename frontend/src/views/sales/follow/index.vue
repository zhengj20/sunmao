<template>
  <div class="page-container follow">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.dealStatus" placeholder="成交状态" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="v in Object.keys(DEAL_STATUS)" :key="v" :label="DEAL_STATUS[v][0]" :value="v" />
          </el-select>
          <el-select v-model="query.intentLevel" placeholder="意向度" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(INTENT_LEVEL)" :key="v" :label="INTENT_LEVEL[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="客户姓名/手机号" clearable style="width: 170px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增跟进</el-button>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 逾期提醒 -->
      <el-alert
        v-if="overdue.length"
        :title="`有 ${overdue.length} 条跟进已到期/逾期，请优先联系客户`"
        type="warning" :closable="false" show-icon class="overdue-alert"
      >
        <div class="overdue-list">
          <el-tag v-for="f in overdue.slice(0, 8)" :key="f.id" size="small" type="warning" effect="light" class="od-tag">
            {{ f.customerName }}（{{ fmtDate(f.nextFollowDate) }}）
          </el-tag>
          <span v-if="overdue.length > 8" class="od-more">…等 {{ overdue.length }} 条</span>
        </div>
      </el-alert>

      <!-- 客户池 KPI -->
      <el-row :gutter="12" class="stat-row">
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">客户总数</div><div class="value">{{ pool.total ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">跟进中</div><div class="value" style="color:#185fa5">{{ pool.following ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">高意向</div><div class="value" style="color:#e6a23c">{{ pool.highIntent ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">已成交</div><div class="value" style="color:#67c23a">{{ pool.deal ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">已流失</div><div class="value">{{ pool.lost ?? 0 }}</div></el-card></el-col>
        <el-col :span="4"><el-card shadow="never" class="stat-card"><div class="label">逾期跟进</div><div class="value" :style="{ color: (pool.overdue ?? 0) > 0 ? '#f56c6c' : '#909399' }">{{ pool.overdue ?? 0 }}</div></el-card></el-col>
      </el-row>

      <!-- 跟进记录 -->
      <el-card shadow="never" class="table-card">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
          <el-table-column prop="followMethod" label="方式" width="80" align="center"><template #default="{ row }">{{ row.followMethod || '-' }}</template></el-table-column>
          <el-table-column prop="followContent" label="跟进内容" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.followContent || '-' }}</template></el-table-column>
          <el-table-column label="意向度" width="100" align="center">
            <template #default="{ row }"><el-tag :type="statusMeta(row.intentLevel, INTENT_LEVEL)[1]" size="small">{{ statusMeta(row.intentLevel, INTENT_LEVEL)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column label="下次跟进" width="140" align="center">
            <template #default="{ row }">
              <span :class="{ 'is-overdue': isOverdue(row) }">{{ fmtDate(row.nextFollowDate) }}</span>
              <el-tag v-if="isOverdue(row)" type="danger" size="small" effect="light" class="od-badge">逾期</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }"><el-tag :type="statusMeta(row.dealStatus, DEAL_STATUS)[1]" size="small">{{ statusMeta(row.dealStatus, DEAL_STATUS)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="followByName" label="跟进人" width="100" align="center"><template #default="{ row }">{{ row.followByName || '-' }}</template></el-table-column>
          <el-table-column label="跟进时间" width="150" align="center"><template #default="{ row }">{{ fmtTime(row.followAt) }}</template></el-table-column>
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
      <el-dialog v-model="form.visible" :title="form.id ? '编辑跟进' : '新增跟进'" width="600px">
        <el-form :model="form" label-width="100px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="客户" required><el-input v-model="form.customerName" placeholder="客户姓名/手机号" maxlength="60" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="跟进方式"><el-select v-model="form.followMethod" style="width: 100%"><el-option v-for="m in FOLLOW_METHODS" :key="m" :label="m" :value="m" /></el-select></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="意向度"><el-select v-model="form.intentLevel" style="width: 100%"><el-option v-for="v in Object.keys(INTENT_LEVEL)" :key="v" :label="INTENT_LEVEL[v][0]" :value="v" /></el-select></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="成交状态"><el-select v-model="form.dealStatus" style="width: 100%"><el-option v-for="v in Object.keys(DEAL_STATUS)" :key="v" :label="DEAL_STATUS[v][0]" :value="v" /></el-select></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="跟进内容"><el-input v-model="form.followContent" type="textarea" :rows="3" maxlength="500" placeholder="记录本次沟通要点、客户反馈、下一步计划" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="form.visible = false">取消</el-button>
          <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtDate, fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, followPage, followPool, followOverdue,
  followCreate, followUpdate, followDelete,
  FOLLOW_METHODS, INTENT_LEVEL, DEAL_STATUS, statusMeta
} from '../../../api/sales'

const today = new Date().toISOString().slice(0, 10)
const isOverdue = row => row.dealStatus === 'FOLLOWING' && row.nextFollowDate && row.nextFollowDate <= today

const projects = ref([])
const projectId = ref(null)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pool = ref({})
const overdue = ref([])
const query = reactive({ dealStatus: null, intentLevel: null, kw: '', current: 1, size: 20 })

const load = async () => {
  if (!projectId.value) { rows.value = []; return }
  loading.value = true
  try {
    const [page, p, od] = await Promise.all([
      followPage({
        current: query.current, size: query.size, projectId: projectId.value,
        dealStatus: query.dealStatus || undefined, intentLevel: query.intentLevel || undefined,
        kw: query.kw || undefined
      }),
      followPool(projectId.value).catch(() => ({})),
      followOverdue(projectId.value).catch(() => [])
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    pool.value = p || {}
    overdue.value = od || []
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; load() }

const form = reactive({
  visible: false, saving: false, id: null, customerName: '', followMethod: '电话',
  intentLevel: 'MID', dealStatus: 'FOLLOWING', nextFollowDate: null, followContent: ''
})
const openForm = row => {
  Object.assign(form, {
    visible: true, saving: false, id: row?.id || null,
    customerName: row?.customerName || '', followMethod: row?.followMethod || '电话',
    intentLevel: row?.intentLevel || 'MID', dealStatus: row?.dealStatus || 'FOLLOWING',
    nextFollowDate: row?.nextFollowDate || null, followContent: row?.followContent || ''
  })
}
const onSave = async () => {
  if (!form.customerName) return ElMessage.warning('请填写客户姓名/手机号')
  form.saving = true
  try {
    const payload = {
      id: form.id, projectId: projectId.value, customerName: form.customerName,
      followMethod: form.followMethod, intentLevel: form.intentLevel, dealStatus: form.dealStatus,
      nextFollowDate: form.nextFollowDate, followContent: form.followContent
    }
    if (form.id) { await followUpdate(payload); ElMessage.success('已更新') }
    else { await followCreate(payload); ElMessage.success('已创建') }
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除客户【${row.customerName}】的跟进记录？`, '提示', { type: 'warning' })
  await followDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    load()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.overdue-alert { margin-bottom: 12px; }
.overdue-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.od-tag { margin: 2px; }
.od-more { font-size: 12px; color: #909399; align-self: center; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #1f2937; font-variant-numeric: tabular-nums; }
.table-card { margin-bottom: 12px; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 12px; }
.is-overdue { color: #f56c6c; font-weight: 600; }
.od-badge { margin-left: 6px; }
</style>
