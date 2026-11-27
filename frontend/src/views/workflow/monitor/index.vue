<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">在途实例</div>
          <div class="value num">{{ stats.inflightCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">超时任务（48h）</div>
          <div class="value num" style="color:#f56c6c">{{ stats.timeoutCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">本月完成</div>
          <div class="value num" style="color:#67c23a">{{ stats.monthFinishedCount ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">平均审批时长</div>
          <div class="value num">{{ fmtDuration(stats.avgDurationMinutes) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="query.status" placeholder="实例状态" clearable style="width: 140px" @change="search">
            <el-option label="进行中" value="RUNNING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已终止" value="WITHDRAWN" />
          </el-select>
          <el-select v-model="query.bizType" placeholder="单据类型" clearable filterable style="width: 160px" @change="search">
            <el-option v-for="(label, key) in BIZ_TYPE" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="bizTitle" label="单据标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单据金额(元)" width="140" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.bizAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="submitterName" label="发起人" width="90" />
        <el-table-column label="发起时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusColor(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="停留时长" width="130">
          <template #default="{ row }">
            <span :class="{ 'over-due': row.status === 'RUNNING' && row.stayMinutes > 2880 }">
              {{ fmtDuration(row.stayMinutes) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">追踪</el-button>
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

    <InstanceDrawer v-model="detailDlg" :instance-id="detailId" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { instancePage, instanceStats } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime, money } from '../../../utils/format'
import InstanceDrawer from '../components/InstanceDrawer.vue'

const bizLabel = t => BIZ_TYPE[t] || t || '-'
const statusLabel = s => ({ RUNNING: '进行中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已终止' }[s] || s)
const statusColor = s => ({ RUNNING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[s] || 'info')

const fmtDuration = min => {
  if (min == null || min === '') return '-'
  if (min < 60) return `${min}分钟`
  if (min < 60 * 24) return `${Math.floor(min / 60)}小时${min % 60}分`
  return `${Math.floor(min / 1440)}天${Math.floor((min % 1440) / 60)}小时`
}

const stats = ref({})
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, status: '', bizType: '' })

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await instancePage({
      ...query,
      status: query.status || undefined,
      bizType: query.bizType || undefined
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  stats.value = await instanceStats()
}

const loadAll = () => {
  loadStats()
  load()
}

const detailDlg = ref(false)
const detailId = ref(null)
const openDetail = row => {
  detailId.value = row.id
  detailDlg.value = true
}

onMounted(loadAll)
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.over-due { color: #f56c6c; font-weight: 600; }
</style>
