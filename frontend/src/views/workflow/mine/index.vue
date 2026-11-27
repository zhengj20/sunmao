<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="status" placeholder="流程状态" clearable style="width: 140px" @change="search">
            <el-option label="审批中" value="RUNNING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="bizTitle" label="单据标题" min-width="240" show-overflow-tooltip />
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
        <el-table-column prop="templateCode" label="流程模板" width="170" show-overflow-tooltip />
        <el-table-column label="当前节点" width="100">
          <template #default="{ row }">{{ row.status === 'RUNNING' ? (row.currentNodeId || '-') : '—' }}</template>
        </el-table-column>
        <el-table-column label="流程状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusColor(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">流程</el-button>
            <el-button v-if="row.status === 'RUNNING'" type="warning" link @click="onWithdraw(row)">撤回</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { minePage, withdrawInstance } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime, money } from '../../../utils/format'
import InstanceDrawer from '../components/InstanceDrawer.vue'

const bizLabel = t => BIZ_TYPE[t] || t || '-'
const statusLabel = s => ({ RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }[s] || s)
const statusColor = s => ({ RUNNING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[s] || 'info')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20 })
const status = ref('')

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await minePage(query)
    const rows = data.records || []
    list.value = status.value ? rows.filter(r => r.status === status.value) : rows
    total.value = status.value ? list.value.length : Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const onWithdraw = async row => {
  try {
    await ElMessageBox.confirm(
      `确定撤回「${row.bizTitle}」吗？撤回后流程终止，单据退回草稿状态。`,
      '撤回确认',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await withdrawInstance({ bizType: row.bizType, bizId: row.bizId })
  ElMessage.success('已撤回')
  load()
}

const detailDlg = ref(false)
const detailId = ref(null)
const openDetail = row => {
  detailId.value = row.id
  detailDlg.value = true
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
</style>
