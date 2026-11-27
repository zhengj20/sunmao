<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="功能模块">
            <el-input v-model="query.module" placeholder="如：MDM/COST/PROC" clearable style="width: 180px" :prefix-icon="Search" @clear="load" />
          </el-form-item>
          <el-form-item label="操作人ID">
            <el-input-number v-model="query.userId" :min="1" :controls="false" placeholder="用户ID" clearable style="width: 140px" @change="load" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border style="width: 100%">
        <el-table-column prop="id" label="日志ID" width="90" align="center" class="num" />
        <el-table-column prop="username" label="操作人" width="110" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" width="85" align="center" class="num" />
        <el-table-column prop="module" label="功能模块" width="130" show-overflow-tooltip />
        <el-table-column prop="action" label="操作动作" width="120" show-overflow-tooltip />
        <el-table-column prop="bizId" label="业务单据ID" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="操作详情" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP地址" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="150" align="center" />
      </el-table>
    </el-card>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { opLogPage } from '../../../api/sys'

const loading = ref(false)
const rows = ref([])
const query = reactive({ module: '', userId: null })
const page = reactive({ current: 1, size: 20, total: 0 })

const load = async () => {
  loading.value = true
  try {
    const data = await opLogPage({
      current: page.current, size: page.size,
      module: query.module || undefined,
      userId: query.userId || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.module = ''
  query.userId = null
  page.current = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.toolbar :deep(.el-form-item) { margin-bottom: 8px; }
</style>
