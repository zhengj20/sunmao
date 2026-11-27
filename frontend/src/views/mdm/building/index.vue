<template>
  <div class="page-container bld-page">
    <!-- 顶部工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 240px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" :loading="summaryLoading" @click="loadSummary">刷新</el-button>
          <el-button type="primary" plain @click="openBuilding()">＋ 新建楼栋</el-button>
        </div>
        <span class="hint">搭建整栋楼（层数/单元/房号）· 可视化楼栋看板 · 房间面积/表价/底价 · 删除/合并</span>
      </div>
      <div class="sales-links">
        <span class="sales-links-label">关联销售页面：</span>
        <el-button link type="primary" @click="goSales('/sales/room')">房源销控</el-button>
        <el-button link type="primary" @click="goSales('/sales/discount')">价格折扣（一房一价）</el-button>
        <el-button link type="primary" @click="goSales('/sales/overview')">销售总览</el-button>
        <el-button link type="primary" @click="goSales('/sales/cockpit')">销售驾驶舱</el-button>
      </div>
    </el-card>

    <el-empty v-if="!projectId" description="请选择项目" :image-size="80" />

    <template v-else>
      <!-- 楼栋列表 -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span class="card-title">楼栋列表（{{ (summaries || []).length }} 栋）</span>
            <div class="legend">
              <span><i :style="{ background: '#67c23a' }" />可售</span>
              <span><i :style="{ background: '#e6a23c' }" />锁定</span>
              <span><i :style="{ background: '#f56c6c' }" />认购</span>
              <span><i :style="{ background: '#c0392b' }" />签约</span>
              <span><i :style="{ background: '#c0c4cc' }" />未开售</span>
            </div>
          </div>
        </template>
        <div v-if="summaries.length" class="bld-cards">
          <div v-for="b in summaries" :key="b.buildingId" class="bld-card" :class="{ active: selectedId === b.buildingId }" @click="selectBuilding(b)">
            <div class="bld-card-top">
              <span class="bld-code">{{ b.buildingCode }}</span>
              <el-tag size="small" :type="bldTag(b.status)">{{ bldName(b.status) }}</el-tag>
            </div>
            <div class="bld-card-name">{{ b.buildingName || '—' }}</div>
            <div class="bld-stats">
              <div><span>{{ b.roomCount }} 套</span><b>{{ b.soldCount ?? 0 }} 已售</b></div>
              <div><span>{{ money(b.roomArea) }} ㎡</span><b>{{ wan(b.tableValue) }} 万</b></div>
            </div>
            <el-progress :percentage="b.roomCount ? Math.round(((b.soldCount || 0) / b.roomCount) * 100) : 0" :stroke-width="6" :show-text="false" color="#185fa5" />
          </div>
        </div>
        <el-empty v-else description="暂无楼栋，点击「新建楼栋」开始搭建" :image-size="80" />
      </el-card>

      <!-- 楼栋看板 -->
      <el-card v-if="selected" shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <div>
              <span class="card-title">{{ selected.buildingCode }} {{ selected.buildingName || '' }}</span>
              <span class="bld-sub">地上 {{ selected.floors }} 层 · {{ selected.undergroundFloors }} 地下 · {{ selected.units }} 单元 · 共 {{ rooms.length }} 间</span>
            </div>
            <div class="actions">
              <el-button size="small" @click="loadBoard">刷新</el-button>
              <el-button size="small" type="primary" plain :loading="gening" @click="openGen">批量生成房间</el-button>
              <el-button size="small" type="success" plain @click="onInitSale">初始化销控</el-button>
              <el-button size="small" type="danger" plain @click="onBldDelete">删除楼栋</el-button>
            </div>
          </div>
        </template>

        <!-- 可视化格子 -->
        <div v-if="grid.length" class="bgrid">
          <div v-for="row in grid" :key="row.floor" class="brow">
            <div class="brow-label">{{ row.label }}</div>
            <div class="brow-cells">
              <template v-for="(ug, ui) in row.units" :key="ug.unit">
                <div v-if="ui > 0" class="unit-sep" />
                <div v-for="r in ug.rooms" :key="r.roomId" class="rcell" :style="cellStyle(r)" @click="openRoom(r)">
                  <div class="rcell-no">{{ r.roomNo }}</div>
                  <div class="rcell-area">{{ money(r.area) }}㎡</div>
                  <div class="rcell-price">{{ r.tablePrice ? wan(r.tablePrice) + '万' : '未定价' }}</div>
                  <div class="rcell-st" :style="{ color: cellColor(r) }">{{ statusName(r) }}</div>
                </div>
              </template>
            </div>
          </div>
        </div>
        <el-empty v-else description="该楼栋暂无房间，点击「批量生成房间」" :image-size="70" />

        <!-- 房间明细表（含合并） -->
        <div class="room-table-head">
          <span class="card-title">房间明细（{{ rooms.length }}）</span>
          <el-button size="small" type="danger" plain :disabled="mergeSel.length < 2" @click="onMerge">合并所选（{{ mergeSel.length }}）</el-button>
        </div>
        <el-table v-if="rooms.length" :data="rooms" border size="small" max-height="420" @selection-change="rows => mergeSel = rows.map(r => r.roomId)">
          <el-table-column type="selection" width="44" />
          <el-table-column prop="roomCode" label="房号" min-width="150" show-overflow-tooltip />
          <el-table-column label="楼层" width="60" align="center"><template #default="{ row }">{{ row.floorNo < 0 ? 'B' + Math.abs(row.floorNo) : row.floorNo }}</template></el-table-column>
          <el-table-column prop="unitNo" label="单元" width="60" align="center" />
          <el-table-column prop="roomNo" label="房号" width="70" align="center" />
          <el-table-column prop="layout" label="户型" width="100" show-overflow-tooltip><template #default="{ row }">{{ row.layout || '-' }}</template></el-table-column>
          <el-table-column label="面积(㎡)" width="90" align="right"><template #default="{ row }">{{ money(row.area) }}</template></el-table-column>
          <el-table-column label="表价" width="110" align="right"><template #default="{ row }">{{ row.tablePrice ? wan(row.tablePrice) + '万' : '-' }}</template></el-table-column>
          <el-table-column label="底价" width="110" align="right"><template #default="{ row }">{{ row.floorPrice ? wan(row.floorPrice) + '万' : '-' }}</template></el-table-column>
          <el-table-column label="销控状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.saleCtrlStatus" size="small" :type="ROOM_STATUS[row.saleCtrlStatus]?.[1] || 'info'">{{ ROOM_STATUS[row.saleCtrlStatus]?.[0] || row.saleCtrlStatus }}</el-tag>
              <el-tag v-else size="small" type="info">未开售</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openRoom(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="onRoomDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 新建/编辑楼栋 -->
      <el-dialog v-model="bDlg.visible" :title="bDlg.form.id ? '编辑楼栋' : '新建楼栋'" width="560px" destroy-on-close>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px" title="先建楼栋（层数/单元），再「批量生成房间」搭建整栋楼" />
        <el-form :model="bDlg.form" label-width="100px">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="栋号" required><el-input v-model="bDlg.form.buildingCode" placeholder="如：2#" maxlength="32" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="楼栋名称"><el-input v-model="bDlg.form.name" placeholder="如：2号楼" maxlength="64" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="地上层数"><el-input-number v-model="bDlg.form.floors" :min="1" :max="99" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="地下层数"><el-input-number v-model="bDlg.form.undergroundFloors" :min="0" :max="9" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="单元数"><el-input-number v-model="bDlg.form.units" :min="1" :max="9" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="总面积(㎡)"><el-input-number v-model="bDlg.form.totalArea" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-select v-model="bDlg.form.status" style="width: 100%">
                  <el-option v-for="(label, value) in BLD_STATUS" :key="value" :label="label" :value="value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="bDlg.visible = false">取消</el-button>
          <el-button type="primary" :loading="bDlg.saving" @click="onBuildingSave">保存</el-button>
        </template>
      </el-dialog>

      <!-- 批量生成房间 -->
      <el-dialog v-model="genDlg.visible" title="批量生成房间（搭建整栋楼）" width="500px" destroy-on-close>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          :title="`楼栋 ${selected?.buildingCode || ''}：房号 = 项目-栋号-楼层+单元+序号，按 楼层 × 单元 × 每层户室 生成`" />
        <el-form :model="genDlg.form" label-width="110px">
          <el-form-item label="起始楼层"><el-input-number v-model="genDlg.form.startFloor" :min="-5" :max="99" controls-position="right" style="width: 100%" /></el-form-item>
          <el-form-item label="结束楼层"><el-input-number v-model="genDlg.form.endFloor" :min="-5" :max="99" controls-position="right" style="width: 100%" /></el-form-item>
          <el-form-item label="单元数"><el-input-number v-model="genDlg.form.units" :min="1" :max="9" controls-position="right" style="width: 100%" /></el-form-item>
          <el-form-item label="每层每单元户室数"><el-input-number v-model="genDlg.form.roomsPerFloor" :min="1" :max="20" controls-position="right" style="width: 100%" /></el-form-item>
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 4px"
            :title="`将生成 ${(genDlg.endFloor - genDlg.startFloor + 1) * (genDlg.units || 0) * (genDlg.roomsPerFloor || 0)} 间（已存在的房号自动跳过）`" />
        </el-form>
        <template #footer>
          <el-button @click="genDlg.visible = false">取消</el-button>
          <el-button type="primary" :loading="genDlg.saving" @click="onGenerate">生成</el-button>
        </template>
      </el-dialog>

      <!-- 房间编辑 -->
      <el-dialog v-model="rDlg.visible" :title="`编辑房间 · ${rDlg.form.roomCode || ''}`" width="560px" destroy-on-close>
        <div v-if="rDlg.form.saleCtrlStatus && rDlg.form.saleCtrlStatus !== 'SELLABLE'" class="rlock">该房间已被占用（{{ statusName(rDlg.form) }}），面积/户型不可修改，可调整价格</div>
        <el-form :model="rDlg.form" label-width="100px">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="户型"><el-input v-model="rDlg.form.layout" :disabled="rLocked" placeholder="如：三房两厅" maxlength="32" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="朝向"><el-input v-model="rDlg.form.orientation" :disabled="rLocked" placeholder="如：南北" maxlength="16" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="建筑面积(㎡)"><el-input-number v-model="rDlg.form.area" :min="0" :precision="2" :controls="false" :disabled="rLocked" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="套内面积(㎡)"><el-input-number v-model="rDlg.form.innerArea" :min="0" :precision="2" :controls="false" :disabled="rLocked" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="表价(元)"><el-input-number v-model="rDlg.form.tablePrice" :min="0" :precision="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="底价(元)"><el-input-number v-model="rDlg.form.floorPrice" :min="0" :precision="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="rDlg.visible = false">取消</el-button>
          <el-button type="primary" :loading="rDlg.saving" @click="onRoomSave">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, wan } from '../../../utils/format'
import {
  projectOptions, buildingSummary, buildingRooms, roomMerge,
  buildingCreate, buildingUpdate, buildingDelete, roomGenerate, roomUpdate, roomDelete
} from '../../../api/mdm'
import { roomInit, updateRoomPrice, ROOM_STATUS } from '../../../api/sales'

const BLD_STATUS = { PLANNED: '规划', CONSTRUCTING: '在建', COMPLETED: '竣工' }
const BLD_TAG = { PLANNED: 'info', CONSTRUCTING: 'warning', COMPLETED: 'success' }
const bldName = s => BLD_STATUS[s] || s
const bldTag = s => BLD_TAG[s] || 'info'
const CELL_COLOR = { SELLABLE: '#67c23a', LOCKED: '#e6a23c', SMALL_DEPOSIT: '#f2b04f', BIG_DEPOSIT: '#ee8e4a', SUBSCRIBED: '#f56c6c', CONTRACTED: '#c0392b', FILED: '#8e2418' }
const cellColor = r => (r.saleCtrlStatus ? CELL_COLOR[r.saleCtrlStatus] || '#c0c4cc' : '#c0c4cc')
const cellStyle = r => { const c = cellColor(r); return { background: c + '18', borderColor: c + '55' } }
const statusName = r => (r.saleCtrlStatus ? (ROOM_STATUS[r.saleCtrlStatus]?.[0] || r.saleCtrlStatus) : '未开售')

const router = useRouter()
const goSales = path => router.push(path)

const projects = ref([])
const projectId = ref(null)
const summaries = ref([])
const summaryLoading = ref(false)
const selected = ref(null)
const selectedId = ref(null)
const rooms = ref([])
const mergeSel = ref([])

const loadSummary = async () => {
  if (!projectId.value) return
  summaryLoading.value = true
  try {
    summaries.value = (await buildingSummary(projectId.value)) || []
    if (!selectedId.value && summaries.value.length) selectBuilding(summaries.value[0])
  } finally {
    summaryLoading.value = false
  }
}
const onProjectChange = () => { selectedId.value = null; selected.value = null; rooms.value = []; loadSummary() }
const selectBuilding = async b => {
  selectedId.value = b.buildingId
  selected.value = b
  mergeSel.value = []
  await loadBoard()
}

const grid = computed(() => {
  const floors = {}
  rooms.value.forEach(r => {
    const f = r.floorNo
    if (!floors[f]) floors[f] = {}
    const u = r.unitNo
    if (!floors[f][u]) floors[f][u] = []
    floors[f][u].push(r)
  })
  return Object.keys(floors).map(Number).sort((a, b) => b - a).map(f => ({
    floor: f,
    label: f < 0 ? 'B' + Math.abs(f) : f + 'F',
    units: Object.keys(floors[f]).map(Number).sort((a, b) => a - b).map(u => ({ unit: u, rooms: floors[f][u] }))
  }))
})

const loadBoard = async () => {
  if (!selectedId.value) return
  rooms.value = (await buildingRooms(selectedId.value).catch(() => [])) || []
}

// ================= 楼栋 =================
const bDlg = reactive({ visible: false, saving: false, form: {} })
const openBuilding = row => {
  const b = row || (summaries.value.find(x => x.buildingId === selectedId.value) || {})
  bDlg.form = row
    ? { id: row.buildingId, buildingCode: row.buildingCode, name: row.buildingName, floors: row.floors, undergroundFloors: row.undergroundFloors, units: row.units, totalArea: row.totalArea, status: row.status }
    : { buildingCode: '', name: '', floors: 18, undergroundFloors: 0, units: 1, totalArea: null, status: 'PLANNED' }
  bDlg.visible = true
}
const onBuildingSave = async () => {
  const f = bDlg.form
  if (!f.buildingCode) return ElMessage.warning('请填写栋号')
  bDlg.saving = true
  try {
    if (f.id) { await buildingUpdate(projectId.value, f.id, { buildingCode: f.buildingCode, name: f.name, floors: f.floors, undergroundFloors: f.undergroundFloors, units: f.units, totalArea: f.totalArea, status: f.status }); ElMessage.success('已更新') }
    else { await buildingCreate(projectId.value, { buildingCode: f.buildingCode, name: f.name, floors: f.floors, undergroundFloors: f.undergroundFloors, units: f.units, totalArea: f.totalArea, status: f.status }); ElMessage.success('已创建') }
    bDlg.visible = false
    await loadSummary()
    if (selectedId.value) await loadBoard()
  } finally {
    bDlg.saving = false
  }
}
const onBldDelete = async () => {
  if (!selected.value) return
  await ElMessageBox.confirm(`确认删除楼栋【${selected.value.buildingCode}】？其下所有房间将一并删除。`, '危险操作', { type: 'warning' })
  await buildingDelete(projectId.value, selectedId.value)
  ElMessage.success('已删除')
  selectedId.value = null; selected.value = null; rooms.value = []
  await loadSummary()
}

// ================= 生成房间 =================
const genDlg = reactive({ visible: false, saving: false, form: { startFloor: 1, endFloor: 18, units: 1, roomsPerFloor: 4 } })
const openGen = () => {
  genDlg.form = { startFloor: 1, endFloor: selected.value?.floors || 18, units: selected.value?.units || 1, roomsPerFloor: 4 }
  genDlg.visible = true
}
const onGenerate = async () => {
  const f = genDlg.form
  if (f.startFloor > f.endFloor) return ElMessage.warning('起始楼层不能大于结束楼层')
  genDlg.saving = true
  try {
    const r = await roomGenerate({ buildingId: selectedId.value, startFloor: f.startFloor, endFloor: f.endFloor, units: f.units, roomsPerFloor: f.roomsPerFloor })
    ElMessage.success(`生成完成：新增 ${r?.generated ?? 0} 间，跳过 ${r?.skipped ?? 0} 间`)
    genDlg.visible = false
    await loadSummary(); await loadBoard()
  } finally {
    genDlg.saving = false
  }
}

// ================= 销控初始化 =================
const onInitSale = async () => {
  const n = await roomInit(projectId.value)
  ElMessage.success(`已初始化销控，新增 ${n} 间（表价/底价已按默认单价生成，可逐间调整）`)
  await loadBoard(); await loadSummary()
}

// ================= 房间编辑 =================
const rDlg = reactive({ visible: false, saving: false, form: {} })
const rLocked = computed(() => {
  const s = rDlg.form.saleCtrlStatus
  return s !== undefined && s !== null && s !== 'SELLABLE'
})
const openRoom = r => {
  rDlg.form = {
    roomId: r.roomId, roomCode: r.roomCode, saleCtrlStatus: r.saleCtrlStatus,
    layout: r.layout || '', orientation: r.orientation || '',
    area: r.area, innerArea: r.innerArea, tablePrice: r.tablePrice, floorPrice: r.floorPrice
  }
  rDlg.visible = true
}
const onRoomSave = async () => {
  const f = rDlg.form
  rDlg.saving = true
  try {
    if (!rLocked.value) {
      await roomUpdate({ id: f.roomId, layout: f.layout, orientation: f.orientation, area: f.area, innerArea: f.innerArea })
    }
    if (f.tablePrice != null) {
      await updateRoomPrice(f.roomId, { tablePrice: f.tablePrice, floorPrice: f.floorPrice })
    }
    ElMessage.success('已保存')
    rDlg.visible = false
    await loadBoard(); await loadSummary()
  } finally {
    rDlg.saving = false
  }
}
const onRoomDelete = async row => {
  await ElMessageBox.confirm(`确认删除房间【${row.roomCode}】？`, '提示', { type: 'warning' })
  await roomDelete(row.roomId)
  ElMessage.success('已删除')
  await loadBoard(); await loadSummary()
}

// ================= 合并 =================
const onMerge = async () => {
  const ids = mergeSel.value
  if (ids.length < 2) return ElMessage.warning('请至少勾选 2 个房间')
  await ElMessageBox.confirm(
    `将 ${ids.length} 个房间合并为 1 套（面积/表价/底价累加，保留房号最小者）。仅可售房间可合并。`,
    '合并确认', { type: 'warning' })
  const r = await roomMerge(ids)
  ElMessage.success(`已合并为 ${r.mergedInto}，面积 ${money(r.area)}㎡，表价 ${wan(r.tablePrice)}万，移除 ${r.removed} 间`)
  mergeSel.value = []
  await loadBoard(); await loadSummary()
}

onMounted(async () => {
  projects.value = await projectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    loadSummary()
  }
})
</script>

<style scoped>
.bld-page { max-width: 1560px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; }
.sales-links { display: flex; align-items: center; gap: 4px; margin-top: 10px; padding-top: 10px; border-top: 1px solid #eef0f3; flex-wrap: wrap; }
.sales-links-label { font-size: 12px; color: #909399; margin-right: 4px; }
.section-card { margin-bottom: 12px; }
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-title { font-weight: 700; color: #0c447c; font-size: 13.5px; }
.bld-sub { font-size: 12px; color: #909399; margin-left: 8px; }
.legend { font-size: 12px; color: #6b7280; display: flex; gap: 10px; flex-wrap: wrap; }
.legend i { width: 10px; height: 10px; border-radius: 3px; display: inline-block; margin-right: 4px; vertical-align: -1px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
/* 楼栋卡片 */
.bld-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.bld-card { border: 1px solid #eef0f3; border-radius: 10px; padding: 14px; background: #fcfdfe; cursor: pointer; transition: all .15s; }
.bld-card:hover { border-color: #b7c6d8; }
.bld-card.active { border-color: #185fa5; background: #f2f7ff; box-shadow: 0 0 0 2px rgba(24,95,165,.12); }
.bld-card-top { display: flex; justify-content: space-between; align-items: center; }
.bld-code { font-size: 17px; font-weight: 800; color: #0c447c; }
.bld-card-name { font-size: 13px; color: #6b7280; margin: 4px 0 10px; }
.bld-stats { display: flex; justify-content: space-between; font-size: 12px; color: #6b7280; margin-bottom: 8px; flex-wrap: wrap; gap: 4px; }
.bld-stats b { color: #1f2937; margin-left: 4px; }
/* 可视化格子 */
.bgrid { display: flex; flex-direction: column; gap: 6px; padding: 4px 0 12px; }
.brow { display: grid; grid-template-columns: 56px 1fr; align-items: center; gap: 8px; }
.brow-label { text-align: center; font-size: 12px; font-weight: 700; color: #0c447c; background: #f5f8fc; border-radius: 6px; padding: 4px 0; }
.brow-cells { display: flex; flex-wrap: wrap; gap: 6px; align-items: stretch; }
.unit-sep { width: 1px; align-self: stretch; background: #e3e8f0; margin: 2px 4px; }
.rcell { min-width: 74px; border: 1px solid; border-radius: 7px; padding: 6px 8px; cursor: pointer; text-align: center; line-height: 1.35; transition: transform .1s; }
.rcell:hover { transform: translateY(-2px); }
.rcell-no { font-size: 13px; font-weight: 700; color: #1f2937; }
.rcell-area { font-size: 11px; color: #6b7280; }
.rcell-price { font-size: 11px; color: #4b5563; }
.rcell-st { font-size: 11px; font-weight: 600; }
/* 房间表 */
.room-table-head { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 10px; }
.rlock { font-size: 12px; color: #e6a23c; background: #fdf6ec; border-radius: 6px; padding: 8px 10px; margin-bottom: 12px; }
</style>
