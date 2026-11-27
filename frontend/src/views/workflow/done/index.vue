<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-input
            v-model="keyword"
            placeholder="搜索单据标题"
            clearable
            style="width: 240px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="action" placeholder="处理结果" clearable style="width: 140px" @change="search">
            <el-option label="已同意" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已转办" value="TRANSFERRED" />
          </el-select>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredList" stripe>
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
        <el-table-column prop="nodeName" label="审批节点" width="130" show-overflow-tooltip />
        <el-table-column label="处理结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusColor(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="审批意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="submitterName" label="提交人" width="90" />
        <el-table-column label="处理时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.actedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ fmtDuration(row.durationMinutes) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">流程</el-button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { donePage } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime, money } from '../../../utils/format'
import InstanceDrawer from '../components/InstanceDrawer.vue'

const bizLabel = t => BIZ_TYPE[t] || t || '-'
const statusLabel = s => ({ APPROVED: '已同意', REJECTED: '已驳回', TRANSFERRED: '已转办' }[s] || s)
const statusColor = s => ({ APPROVED: 'success', REJECTED: 'danger', TRANSFERRED: 'info' }[s] || 'info')

const fmtDuration = min => {
  if (min == null || min === '') return '-'
  if (min < 60) return `${min}分钟`
  if (min < 60 * 24) return `${Math.floor(min / 60)}小时${min % 60}分`
  return `${Math.floor(min / 1440)}天${Math.floor((min % 1440) / 60)}小时`
}

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20 })
const keyword = ref('')
const action = ref('')

const filteredList = computed(() =>
  list.value.filter(r => {
    if (action.value && r.status !== action.value) return false
    if (keyword.value && !(r.bizTitle || '').includes(keyword.value)) return false
    return true
  })
)

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await donePage(query)
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const detailDlg = ref(false)
const detailId = ref(null)
const openDetail = row => {
  detailId.value = row.instanceId
  detailDlg.value = true
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
</style>
