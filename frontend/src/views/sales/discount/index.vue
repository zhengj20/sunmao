<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 200px" @change="loadPrice">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-tabs v-model="tab" class="inline-tabs">
            <el-tab-pane label="一房一价" name="price" />
            <el-tab-pane label="折扣方案" name="discount" />
          </el-tabs>
        </div>
        <div>
          <el-button v-if="tab === 'price'" @click="loadPrice">刷新价目表</el-button>
          <el-button v-else type="primary" @click="openDiscountForm()">新增折扣方案</el-button>
        </div>
      </div>
    </el-card>

    <!-- 一房一价 -->
    <el-card v-show="tab === 'price'" shadow="never" class="table-card">
      <el-table v-loading="priceLoading" :data="priceRows" border stripe>
        <el-table-column prop="roomCode" label="房号" width="130" show-overflow-tooltip />
        <el-table-column label="楼栋" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.buildingName || (row.buildingId ? '#' + row.buildingId : '-') }}</template>
        </el-table-column>
        <el-table-column label="面积(㎡)" width="110" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.area) }}</span></template>
        </el-table-column>
        <el-table-column label="表价(元)" width="160" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.tablePrice) }}</span></template>
        </el-table-column>
        <el-table-column label="底价(元)" width="160" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.floorPrice) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="roomMeta(row.status)[1]" size="small">{{ roomMeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPriceForm(row)">调价</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 折扣方案 -->
    <el-card v-show="tab === 'discount'" shadow="never" class="table-card">
      <div class="discount-filter">
        <el-select v-model="dQuery.level" placeholder="级别" clearable style="width: 150px" @change="onSearchDiscount">
          <el-option v-for="v in Object.keys(DISCOUNT_LEVEL)" :key="v" :label="DISCOUNT_LEVEL[v][0]" :value="v" />
        </el-select>
        <el-select v-model="dQuery.status" placeholder="状态" clearable style="width: 130px" @change="onSearchDiscount">
          <el-option v-for="v in Object.keys(DISCOUNT_STATUS)" :key="v" :label="DISCOUNT_STATUS[v][0]" :value="v" />
        </el-select>
        <el-button type="primary" @click="onSearchDiscount">查询</el-button>
      </div>
      <el-table v-loading="dLoading" :data="dRows" border stripe>
        <el-table-column prop="discountNo" label="方案编号" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="方案名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scope" label="适用范围" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.scope || '-' }}</template>
        </el-table-column>
        <el-table-column label="级别" width="110" align="center">
          <template #default="{ row }"><el-tag :type="statusMeta(row.level, DISCOUNT_LEVEL)[1]" size="small">{{ statusMeta(row.level, DISCOUNT_LEVEL)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="基准折扣率" width="110" align="right">
          <template #default="{ row }">{{ pct(row.baseRate) }}</template>
        </el-table-column>
        <el-table-column label="可下探下限" width="110" align="right">
          <template #default="{ row }">{{ pct(row.maxRate) }}</template>
        </el-table-column>
        <el-table-column label="生效期" width="200" align="center">
          <template #default="{ row }">{{ fmtDate(row.effectiveFrom) }} ~ {{ fmtDate(row.effectiveTo) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusMeta(row.status, DISCOUNT_STATUS)[1]" size="small">{{ statusMeta(row.status, DISCOUNT_STATUS)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDiscountForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteDiscount(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="dQuery.current"
          v-model:page-size="dQuery.size"
          :total="dTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadDiscount"
          @size-change="onSearchDiscount"
        />
      </div>
    </el-card>

    <!-- 调价弹窗 -->
    <el-dialog v-model="priceForm.visible" title="一房一价调整" width="520px">
      <el-descriptions :column="1" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="房号">{{ priceForm.row?.roomCode }}</el-descriptions-item>
        <el-descriptions-item label="面积(㎡)"><span class="num">{{ money(priceForm.row?.area) }}</span></el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="表价(元)" required>
          <el-input-number v-model="priceForm.tablePrice" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="底价(元)">
          <el-input-number v-model="priceForm.floorPrice" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="priceForm.saving" @click="onSavePrice">保存</el-button>
      </template>
    </el-dialog>

    <!-- 折扣方案弹窗 -->
    <el-dialog v-model="dForm.visible" :title="dForm.id ? '编辑折扣方案' : '新增折扣方案'" width="620px">
      <el-form :model="dForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="方案名称" required><el-input v-model="dForm.name" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="级别" required>
              <el-select v-model="dForm.level" style="width: 100%">
                <el-option v-for="v in Object.keys(DISCOUNT_LEVEL)" :key="v" :label="DISCOUNT_LEVEL[v][0]" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基准折扣率">
              <el-input-number v-model="dForm.baseRate" :controls="false" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
              <div class="hint">0~1 区间，如 0.95 表示 95 折</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可下探下限" required>
              <el-input-number v-model="dForm.maxRate" :controls="false" :min="0" :max="1" :step="0.01" :precision="4" style="width: 100%" />
              <div class="hint">不得低于基准折扣率</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效起"><el-date-picker v-model="dForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效止"><el-date-picker v-model="dForm.effectiveTo" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="适用范围"><el-input v-model="dForm.scope" type="textarea" :rows="2" maxlength="200" placeholder="如：A区高层住宅" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dForm.visible = false">取消</el-button>
        <el-button type="primary" :loading="dForm.saving" @click="onSaveDiscount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  fetchProjectOptions, roomPage, updateRoomPrice,
  discountPage, discountCreate, discountUpdate, discountDelete,
  ROOM_STATUS, DISCOUNT_LEVEL, DISCOUNT_STATUS, statusMeta
} from '../../../api/sales'

const roomMeta = s => statusMeta(s, ROOM_STATUS)
const pct = v => (v === null || v === undefined || v === '' ? '-' : (Number(v) * 100).toFixed(1) + '%')

const tab = ref('price')
const projects = ref([])
const projectId = ref(null)

// 一房一价
const priceLoading = ref(false)
const priceRows = ref([])
const priceForm = reactive({ visible: false, saving: false, row: null, tablePrice: null, floorPrice: null })

const loadPrice = async () => {
  if (!projectId.value) { priceRows.value = []; return }
  priceLoading.value = true
  try {
    const page = await roomPage({ current: 1, size: 500, projectId: projectId.value })
    priceRows.value = page?.records || []
  } finally {
    priceLoading.value = false
  }
}
const openPriceForm = row => {
  priceForm.row = row
  priceForm.tablePrice = row.tablePrice
  priceForm.floorPrice = row.floorPrice
  priceForm.visible = true
}
const onSavePrice = async () => {
  if (priceForm.tablePrice == null) return ElMessage.warning('请填写表价')
  if (priceForm.floorPrice != null && Number(priceForm.floorPrice) > Number(priceForm.tablePrice))
    return ElMessage.warning('底价不得高于表价')
  priceForm.saving = true
  try {
    await updateRoomPrice(priceForm.row.roomId, { tablePrice: priceForm.tablePrice, floorPrice: priceForm.floorPrice })
    ElMessage.success('价目已更新')
    priceForm.visible = false
    loadPrice()
  } finally {
    priceForm.saving = false
  }
}

// 折扣方案
const dLoading = ref(false)
const dRows = ref([])
const dTotal = ref(0)
const dQuery = reactive({ level: null, status: null, current: 1, size: 20 })
const dForm = reactive({
  visible: false, saving: false, id: null, name: '', level: 'AGENT',
  baseRate: null, maxRate: null, effectiveFrom: null, effectiveTo: null, scope: ''
})

const loadDiscount = async () => {
  dLoading.value = true
  try {
    const page = await discountPage({
      current: dQuery.current, size: dQuery.size,
      level: dQuery.level || undefined, status: dQuery.status || undefined
    })
    dRows.value = page?.records || []
    dTotal.value = Number(page?.total) || 0
  } finally {
    dLoading.value = false
  }
}
const onSearchDiscount = () => { dQuery.current = 1; loadDiscount() }
const openDiscountForm = row => {
  Object.assign(dForm, {
    visible: true, saving: false, id: row?.id || null,
    name: row?.name || '', level: row?.level || 'AGENT',
    baseRate: row?.baseRate ?? null, maxRate: row?.maxRate ?? null,
    effectiveFrom: row?.effectiveFrom || null, effectiveTo: row?.effectiveTo || null,
    scope: row?.scope || ''
  })
}
const onSaveDiscount = async () => {
  if (!dForm.name) return ElMessage.warning('请填写方案名称')
  if (dForm.maxRate == null) return ElMessage.warning('请填写可下探下限')
  dForm.saving = true
  try {
    const payload = {
      id: dForm.id, name: dForm.name, level: dForm.level,
      baseRate: dForm.baseRate, maxRate: dForm.maxRate,
      effectiveFrom: dForm.effectiveFrom, effectiveTo: dForm.effectiveTo, scope: dForm.scope
    }
    if (dForm.id) {
      await discountUpdate(payload)
      ElMessage.success('方案已更新')
    } else {
      await discountCreate(payload)
      ElMessage.success('方案已创建')
    }
    dForm.visible = false
    loadDiscount()
  } finally {
    dForm.saving = false
  }
}
const onDeleteDiscount = async row => {
  await ElMessageBox.confirm(`确认删除折扣方案【${row.discountNo || row.name}】？`, '提示', { type: 'warning' })
  await discountDelete(row.id)
  ElMessage.success('已删除')
  loadDiscount()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    loadPrice()
  }
  loadDiscount()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.inline-tabs { --el-tabs-header-height: 40px; }
.inline-tabs :deep(.el-tabs__item) { font-size: 14px; }
.discount-filter { display: flex; gap: 10px; margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; line-height: 1.4; }
</style>
