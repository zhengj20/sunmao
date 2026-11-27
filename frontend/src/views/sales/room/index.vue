<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="选择项目" filterable style="width: 200px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.buildingId" placeholder="楼栋" clearable filterable style="width: 150px" @change="onSearch">
            <el-option v-for="b in buildings" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(ROOM_STATUS)" :key="v" :label="ROOM_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input-number v-model="query.minArea" :controls="false" :min="0" placeholder="最小面积" style="width: 120px" />
          <span class="sep">~</span>
          <el-input-number v-model="query.maxArea" :controls="false" :min="0" placeholder="最大面积" style="width: 120px" />
          <el-input v-model="query.kw" placeholder="房号" clearable style="width: 130px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <div class="actions">
          <el-button :disabled="!selected.length" @click="onInit">初始化销控</el-button>
          <el-button type="warning" :disabled="!selected.length" :loading="batchLoading" @click="onBatchLock">批量锁定</el-button>
          <el-button type="success" :disabled="!selected.length" :loading="batchLoading" @click="onBatchUnlock">批量放开</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="4">
        <el-card shadow="never" class="stat-card"><div class="label">总套数</div><div class="value">{{ stats.total }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card"><div class="label">可售</div><div class="value" style="color:#67c23a">{{ stats.sellable }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card"><div class="label">已认购</div><div class="value" style="color:#2a74bd">{{ stats.subscribed }}</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card"><div class="label">已签约</div><div class="value" style="color:#f56c6c">{{ stats.contracted }}</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card"><div class="label">去化率</div><div class="value">{{ stats.turnover }}%</div></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        :row-key="row => row.roomId"
        @selection-change="s => (selected = s)"
      >
        <el-table-column type="selection" width="45" :selectable="r => r.status === 'SELLABLE' || r.status === 'LOCKED'" />
        <el-table-column prop="roomCode" label="房号" width="120" show-overflow-tooltip />
        <el-table-column label="楼栋" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.buildingName || (row.buildingId ? '#' + row.buildingId : '-') }}</template>
        </el-table-column>
        <el-table-column label="面积(㎡)" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.area) }}</span></template>
        </el-table-column>
        <el-table-column label="表价(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.tablePrice) }}</span></template>
        </el-table-column>
        <el-table-column label="底价(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.floorPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="roomMeta(row.status)[1]" size="small">{{ roomMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="锁定人" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lockBy ? '用户#' + row.lockBy : '-' }}</template>
        </el-table-column>
        <el-table-column label="关联单据" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SELLABLE'" link type="warning" size="small" @click="onLock(row)">锁定</el-button>
            <el-button v-if="row.status === 'LOCKED'" link type="success" size="small" @click="onUnlock(row)">放开</el-button>
            <el-button link type="primary" size="small" @click="onDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <el-dialog v-model="detailVisible" title="房源详情" width="560px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="房号">{{ detail.roomCode }}</el-descriptions-item>
        <el-descriptions-item label="楼栋">{{ detail.buildingName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="户型">{{ detail.layout || '-' }}</el-descriptions-item>
        <el-descriptions-item label="面积(㎡)"><span class="num">{{ money(detail.area) }}</span></el-descriptions-item>
        <el-descriptions-item label="表价(元)"><span class="num">{{ money(detail.tablePrice) }}</span></el-descriptions-item>
        <el-descriptions-item label="底价(元)"><span class="num">{{ money(detail.floorPrice) }}</span></el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="roomMeta(detail.status)[1]" size="small">{{ roomMeta(detail.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联单据">{{ detail.bizNo || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchProjectOptions, roomPage, roomInit, roomLock, roomUnlock, roomDetail,
  ROOM_STATUS, statusMeta
} from '../../../api/sales'

const roomMeta = s => statusMeta(s, ROOM_STATUS)

const projects = ref([])
const buildings = ref([])
const loading = ref(false)
const batchLoading = ref(false)
const rows = ref([])
const total = ref(0)
const selected = ref([])
const query = reactive({
  projectId: null, buildingId: null, status: null, minArea: null, maxArea: null, kw: '', current: 1, size: 20
})
const stats = reactive({ total: 0, sellable: 0, subscribed: 0, contracted: 0, turnover: 0 })

const detailVisible = ref(false)
const detail = ref(null)

const computeStats = records => {
  const all = records || []
  const cnt = st => all.filter(r => r.status === st).length
  const total = all.length
  const subscribed = cnt('SUBSCRIBED')
  const contracted = cnt('CONTRACTED') + cnt('FILED')
  const sold = subscribed + contracted
  stats.total = total
  stats.sellable = cnt('SELLABLE')
  stats.subscribed = subscribed
  stats.contracted = contracted
  stats.turnover = total ? ((sold / total) * 100).toFixed(1) : '0.0'
}

const loadList = async () => {
  loading.value = true
  try {
    const page = await roomPage({
      current: query.current, size: query.size, projectId: query.projectId,
      buildingId: query.buildingId, status: query.status,
      minArea: query.minArea || undefined, maxArea: query.maxArea || undefined,
      kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}

const loadMeta = async () => {
  if (!query.projectId) { buildings.value = []; computeStats([]); return }
  const page = await roomPage({ current: 1, size: 1000, projectId: query.projectId }).catch(() => null)
  const all = page?.records || []
  computeStats(all)
  const map = {}
  all.forEach(r => { if (r.buildingId) map[r.buildingId] = r.buildingName || ('#' + r.buildingId) })
  buildings.value = Object.keys(map).map(k => ({ value: Number(k), label: map[k] }))
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.buildingId = null; query.status = null; query.minArea = null; query.maxArea = null; query.kw = ''
  onSearch()
}
const onProjectChange = () => {
  query.buildingId = null; query.current = 1
  Promise.all([loadList(), loadMeta()])
}

const onLock = async row => {
  await roomLock(row.roomId)
  ElMessage.success(`房源 ${row.roomCode} 已锁定`)
  await Promise.all([loadList(), loadMeta()])
}
const onUnlock = async row => {
  await roomUnlock(row.roomId)
  ElMessage.success(`房源 ${row.roomCode} 已放开`)
  await Promise.all([loadList(), loadMeta()])
}
const onBatchLock = async () => {
  const targets = selected.value.filter(r => r.status === 'SELLABLE')
  if (!targets.length) return ElMessage.warning('所选房源中无可锁定（仅可售）房源')
  await ElMessageBox.confirm(`确认锁定 ${targets.length} 套可售房源？（默认24小时到期）`, '批量锁定', { type: 'warning' })
  await batchOp(targets, roomLock)
}
const onBatchUnlock = async () => {
  const targets = selected.value.filter(r => r.status === 'LOCKED')
  if (!targets.length) return ElMessage.warning('所选房源中无已锁定房源可放开')
  await ElMessageBox.confirm(`确认放开 ${targets.length} 套已锁定房源？`, '批量放开', { type: 'warning' })
  await batchOp(targets, roomUnlock)
}
const batchOp = async (targets, fn) => {
  batchLoading.value = true
  let ok = 0, fail = 0
  for (const r of targets) {
    try { await fn(r.roomId); ok++ } catch { fail++ }
  }
  batchLoading.value = false
  ElMessage[fail ? 'warning' : 'success'](`批量操作完成：成功 ${ok}，失败 ${fail}`)
  selected.value = []
  await Promise.all([loadList(), loadMeta()])
}
const onInit = async () => {
  if (!query.projectId) return ElMessage.warning('请先选择项目')
  await ElMessageBox.confirm('确认从 MDM 房间初始化本项目销控（已纳入的房间会跳过）？', '初始化销控', { type: 'warning' })
  const n = await roomInit(query.projectId)
  ElMessage.success(`初始化完成，新增 ${n || 0} 套房源`)
  await Promise.all([loadList(), loadMeta()])
}
const onDetail = async row => {
  detail.value = await roomDetail(row.roomId)
  detailVisible.value = true
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    query.projectId = projects.value[0].id
    await Promise.all([loadList(), loadMeta()])
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; }
.sep { color: #909399; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #1f2937; }
</style>
