<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="协议编号/名称" clearable style="width: 190px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="生效中" value="ACTIVE" />
              <el-option label="已过期" value="EXPIRED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新增框架协议</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="frameworkCode" label="协议编号" width="125" show-overflow-tooltip />
        <el-table-column prop="name" label="协议名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="采购品类" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || catName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtDate(row.validFrom) }} ~ {{ fmtDate(row.validTo) }}</template>
        </el-table-column>
        <el-table-column label="累计下单(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.orderTotalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="success" size="small" @click="openOrder(row)">集采下单</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <!-- 新增/编辑（含框架单价表子表） -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑框架协议' : '新增框架协议'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="协议名称" prop="name">
              <el-input v-model="form.data.name" placeholder="如：集团电梯战略集采协议(2026)" maxlength="120" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.data.supplierId" placeholder="选择供应商" filterable style="width: 100%" @change="onSupplierChange">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购品类">
              <el-cascader v-model="form.data.categoryId" :options="categories" :props="cascaderProps" placeholder="选择品类" clearable style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期">
              <el-date-picker
                v-model="form.range"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="~"
                start-placeholder="生效日"
                end-placeholder="失效日"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="block-title">
        框架单价表
        <el-button type="primary" size="small" icon="Plus" style="margin-left: 10px" @click="addPriceRow">添加品项</el-button>
      </div>
      <el-table :data="form.prices" border size="small" :max-height="260">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column label="品项名称" min-width="180">
          <template #default="{ row }"><el-input v-model="row.name" placeholder="如：商品混凝土C30" size="small" /></template>
        </el-table-column>
        <el-table-column label="单位" width="100">
          <template #default="{ row }"><el-input v-model="row.unit" placeholder="m³/t/台" size="small" maxlength="10" /></template>
        </el-table-column>
        <el-table-column label="框架单价(元)" width="160" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.price" :min="0" :precision="2" :controls="false" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="form.prices.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="90px" style="margin-top: 10px">
        <el-form-item label="备注">
          <el-input v-model="form.data.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 集采下单 -->
    <el-dialog v-model="order.visible" :title="`集采下单 - ${order.frameworkName}`" width="560px" destroy-on-close>
      <el-form ref="orderRef" :model="order.data" :rules="orderRules" label-width="90px">
        <el-form-item label="下单项目" prop="projectId">
          <el-select v-model="order.data.projectId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购内容" prop="content">
          <el-select v-model="order.data.content" placeholder="选择框架协议品项" style="width: 100%" @change="onOrderContentChange">
            <el-option v-for="p in order.prices" :key="p.name" :label="`${p.name}（${money(p.price)} 元/${p.unit || '件'}）`" :value="p.name" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="order.data.quantity" :min="0" :precision="3" :controls="false" style="width: 100%" @change="recalcOrderAmount" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下单日期">
              <el-date-picker v-model="order.data.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="金额(元)">
          <el-input-number v-model="order.data.amount" :min="0" :precision="2" :controls="false" style="width: 200px" />
          <span style="margin-left: 8px; color: #6b7280">按框架单价自动计算，可微调</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="order.visible = false">取消</el-button>
        <el-button type="primary" :loading="order.saving" @click="onOrderSave">下单</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="战略集采协议详情" size="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="协议编号">{{ detail.data.frameworkCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="协议名称" :span="2">{{ detail.data.name }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ detail.data.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="采购品类">{{ detail.data.categoryName || catName(detail.data.categoryId) }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ fmtDate(detail.data.validFrom) }} ~ {{ fmtDate(detail.data.validTo) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">框架单价表</div>
      <el-table :data="detail.prices" border size="small">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column prop="name" label="品项名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="框架单价(元)" width="130" align="right" class="num">
          <template #default="{ row }">{{ money(row.price) }}</template>
        </el-table-column>
      </el-table>

      <div class="block-title">下单记录（{{ detail.orders.length }} 笔）</div>
      <el-table :data="detail.orders" border size="small">
        <el-table-column prop="orderCode" label="订单编号" width="135" show-overflow-tooltip />
        <el-table-column label="项目" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="content" label="采购内容" min-width="130" show-overflow-tooltip />
        <el-table-column label="数量" width="90" align="right" class="num">
          <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="金额(元)" width="120" align="right" class="num">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="下单日期" width="100">
          <template #default="{ row }">{{ fmtDate(row.orderDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ORDERED'" link type="success" size="small" @click="onOrderStatus(row, 'DELIVERED')">确认到货</el-button>
            <el-button v-if="row.status === 'DELIVERED'" link type="info" size="small" @click="onOrderStatus(row, 'CLOSED')">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { money, fmtDate } from '../../../utils/format'
import {
  fwPage, fwDetail, fwCreate, fwUpdate, fwDelete, fwOrderCreate, fwOrderStatus,
  fetchProjectOptions, fetchSupplierOptions, fetchPurchaseCategories,
  statusMeta
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', status: '' })

const projects = ref([])
const suppliers = ref([])
const categories = ref([])
const catMap = ref({})
const cascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }
const flattenTree = (nodes, out = {}) => {
  ;(nodes || []).forEach(n => { out[n.id] = n.name; if (n.children?.length) flattenTree(n.children, out) })
  return out
}
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'
const catName = id => catMap.value[id] || '-'

/** unitPriceTable JSON：{品名:{unit,price}} ↔ 编辑行数组 [{name,unit,price}] */
const parsePrices = table => {
  if (!table) return []
  if (Array.isArray(table)) return table.map(p => ({ name: p.name, unit: p.unit, price: p.price }))
  return Object.keys(table).map(name => ({ name, unit: table[name]?.unit || '', price: Number(table[name]?.price) || 0 }))
}
const buildPriceTable = rows => {
  const obj = {}
  rows.filter(r => r.name).forEach(r => { obj[r.name] = { unit: r.unit || '', price: r.price ?? 0 } })
  return obj
}

const load = async () => {
  loading.value = true
  try {
    const page = await fwPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, range: null, prices: [], data: {} })
const rules = {
  name: [{ required: true, message: '请输入协议名称', trigger: 'blur' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.range = row ? [row.validFrom, row.validTo].filter(Boolean) : null
  form.prices = row?.unitPriceTable ? parsePrices(row.unitPriceTable) : []
  if (!form.prices.length) form.prices = [{ name: '', unit: '', price: undefined }]
  form.data = {
    name: row?.name || '', supplierId: row?.supplierId ?? null, supplierName: row?.supplierName || '',
    categoryId: row?.categoryId ?? null, validFrom: row?.validFrom || null, validTo: row?.validTo || null,
    remark: row?.remark || ''
  }
  form.visible = true
}
const addPriceRow = () => form.prices.push({ name: '', unit: '', price: undefined })
const onSupplierChange = sid => {
  form.data.supplierName = suppliers.value.find(s => s.id === sid)?.name || ''
}
const onSave = async () => {
  await formRef.value.validate()
  if (form.range?.length === 2) {
    form.data.validFrom = form.range[0]
    form.data.validTo = form.range[1]
  }
  if (!form.prices.filter(p => p.name).length) return ElMessage.warning('请至少维护一条框架单价')
  form.saving = true
  try {
    const payload = { ...form.data, unitPriceTable: buildPriceTable(form.prices) }
    if (form.id) await fwUpdate({ id: form.id, ...payload })
    else await fwCreate(payload)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除协议 ${row.frameworkCode}？存在下单记录时将无法删除。`, '删除', { type: 'warning' })
  await fwDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 集采下单 ============
const orderRef = ref()
const order = reactive({ visible: false, saving: false, frameworkId: null, frameworkName: '', prices: [], data: {} })
const orderRules = {
  projectId: [{ required: true, message: '请选择下单项目', trigger: 'change' }],
  content: [{ required: true, message: '请选择采购内容', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}
const openOrder = row => {
  order.frameworkId = row.id
  order.frameworkName = row.name
  order.prices = parsePrices(row.unitPriceTable)
  order.data = {
    projectId: null, content: '', quantity: undefined, amount: undefined,
    orderDate: dayjs().format('YYYY-MM-DD')
  }
  order.visible = true
}
const recalcOrderAmount = () => {
  const p = order.prices.find(x => x.name === order.data.content)
  if (p && order.data.quantity) order.data.amount = Number((p.price * order.data.quantity).toFixed(2))
}
const onOrderContentChange = name => {
  const p = order.prices.find(x => x.name === name)
  if (p && order.data.quantity) order.data.amount = Number((p.price * order.data.quantity).toFixed(2))
}
const onOrderSave = async () => {
  await orderRef.value.validate()
  order.saving = true
  try {
    await fwOrderCreate({ ...order.data, frameworkId: order.frameworkId })
    ElMessage.success('下单成功，将按框架单价生成采购订单')
    order.visible = false
    load()
  } finally {
    order.saving = false
  }
}
const onOrderStatus = async (row, status) => {
  const label = status === 'DELIVERED' ? '确认到货' : '关闭订单'
  await ElMessageBox.confirm(`确认对订单 ${row.orderCode} 执行【${label}】？`, label, { type: 'warning' })
  await fwOrderStatus(row.id, status)
  ElMessage.success('操作成功')
  if (detail.visible && detail.data.id) openDetail(detail.data)
  load()
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {}, prices: [], orders: [] })
const openDetail = async row => {
  const d = await fwDetail(row.id)
  detail.data = d || row
  detail.prices = parsePrices(d?.unitPriceTable || row.unitPriceTable)
  detail.orders = d?.orders || []
  detail.visible = true
}

onMounted(async () => {
  load()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
  fetchSupplierOptions().then(rs => { suppliers.value = rs || [] }).catch(() => {})
  fetchPurchaseCategories().then(rs => {
    categories.value = rs || []
    catMap.value = flattenTree(rs)
  }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
