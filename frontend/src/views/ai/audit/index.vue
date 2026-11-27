<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.scene" placeholder="场景" clearable style="width: 150px" @change="onSearch">
            <el-option v-for="k in Object.keys(AUDIT_SCENES)" :key="k" :label="AUDIT_SCENES[k]" :value="k" />
          </el-select>
          <el-select v-model="query.aiMode" placeholder="模式" clearable style="width: 130px" @change="onSearch">
            <el-option label="规则 RULE" value="RULE" />
            <el-option label="大模型 LLM" value="LLM" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="k in Object.keys(AUDIT_STATUS)" :key="k" :label="AUDIT_STATUS[k][0]" :value="k" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="scene" label="场景" width="120" align="center">
          <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
        </el-table-column>
        <el-table-column label="模式" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="aiModeMeta(row.aiMode)[1]">{{ aiModeMeta(row.aiMode)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(row.status, AUDIT_STATUS)[1]">{{ statusMeta(row.status, AUDIT_STATUS)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="center">
          <template #default="{ row }">{{ row.durationMs ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="Token入/出" width="120" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.tokenIn ?? '-' }} / {{ row.tokenOut ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inputDigest" label="输入摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }"><span class="muted">{{ row.inputDigest || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="outputDigest" label="输出摘要" min-width="160" show-overflow-tooltip>
          <template #default="{ row }"><span class="muted">{{ row.outputDigest || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onDetail(row)">详情</el-button>
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

    <!-- 详情抽屉（敏感摘要折叠展示） -->
    <el-drawer v-model="drawerVisible" title="审计详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="场景">{{ sceneLabel(detail.scene) }}</el-descriptions-item>
          <el-descriptions-item label="模式">{{ aiModeMeta(detail.aiMode)[0] }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusMeta(detail.status, AUDIT_STATUS)[0] }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.durationMs ?? '-' }} ms</el-descriptions-item>
          <el-descriptions-item label="Token 入">{{ detail.tokenIn ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Token 出">{{ detail.tokenOut ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型配置">{{ detail.modelConfigId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间" :span="2">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-collapse v-model="activeCollapse" class="digest-collapse">
          <el-collapse-item title="输入摘要（脱敏）" name="in">
            <div class="digest-box">{{ detail.inputDigest || '-' }}</div>
          </el-collapse-item>
          <el-collapse-item title="输出摘要（脱敏）" name="out">
            <div class="digest-box">{{ detail.outputDigest || '-' }}</div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  auditPage, auditDetail,
  AUDIT_SCENES, AUDIT_STATUS, sceneLabel, aiModeMeta, statusMeta
} from '../../../api/ai'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ scene: null, aiMode: null, status: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await auditPage({
      current: query.current, size: query.size,
      scene: query.scene || undefined, aiMode: query.aiMode || undefined, status: query.status || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.scene = null; query.aiMode = null; query.status = null; onSearch() }

const drawerVisible = ref(false)
const detail = ref(null)
const activeCollapse = ref(['in'])
const onDetail = async row => {
  const d = await auditDetail(row.id)
  detail.value = d
  activeCollapse.value = ['in']
  drawerVisible.value = true
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.muted { color: #909399; }
.num { font-family: "SF Mono", Consolas, Menlo, monospace; }
.digest-collapse { margin-top: 14px; }
.digest-box { white-space: pre-wrap; word-break: break-word; line-height: 1.7; color: #1f2937; background: #f5f8fc; padding: 10px 12px; border-radius: 6px; max-height: 260px; overflow-y: auto; }
</style>
