<template>
  <el-dialog
    :model-value="modelValue"
    :title="title || '明细下钻'"
    width="880px"
    top="8vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="drill-head">
      <el-tag size="small" type="info" effect="plain">{{ metricName }}</el-tag>
      <span class="drill-count">共 {{ rows.length }} 条</span>
      <span class="drill-tip">按指标穿透至明细单据（最近 200 条）</span>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe size="small" max-height="56vh" empty-text="暂无明细数据">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="col.type === 'money'" class="num">{{ money(row[col.prop]) }}</span>
          <span v-else-if="col.type === 'pct'">{{ pct(row[col.prop]) }}</span>
          <span v-else-if="col.type === 'date'">{{ fmtDate(row[col.prop]) }}</span>
          <el-tag v-else-if="col.type === 'status'" size="small" :type="statusMeta(row[col.prop])" effect="plain">{{ row[col.prop] || '-' }}</el-tag>
          <span v-else>{{ row[col.prop] == null ? '-' : row[col.prop] }}</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { cockpitDrill } from '../../../api/cockpit'
import { money, fmtDate, statusColor } from '../../../utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  metricCode: { type: String, default: '' },
  /** { orgId, projectId, month } 均可选 */
  params: { type: Object, default: () => ({}) }
})
defineEmits(['update:modelValue'])

const METRIC_NAME = {
  SIGN_AMOUNT: '签约额', RECEIPT_AMOUNT: '回款额', DYNAMIC_COST: '动态成本',
  TURNOVER_RATE: '去化率', DYNAMIC_PROFIT: '动态利润', CHANGE_RATE: '变更率',
  PLAN_ACHIEVE_RATE: '计划达成率', COST_EFFICIENCY: '费效比', BUDGET_EXEC_RATE: '预算执行率'
}
const metricName = computed(() => METRIC_NAME[props.metricCode] || props.title || props.metricCode || '明细')

/** 按指标类型 → 明细列（与后端 drill 各 mapper 返回字段对齐） */
const COLUMN_MAP = {
  SIGN_AMOUNT: [
    { prop: 'contractNo', label: '合同号', minWidth: 130 },
    { prop: 'projectName', label: '项目', minWidth: 120 },
    { prop: 'customerName', label: '客户', minWidth: 110 },
    { prop: 'contractPrice', label: '签约金额(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'signedAt', label: '签约日期', type: 'date', width: 110, align: 'center' },
    { prop: 'status', label: '状态', type: 'status', width: 90, align: 'center' }
  ],
  RECEIPT_AMOUNT: [
    { prop: 'receiptNo', label: '回款单号', minWidth: 130 },
    { prop: 'receivedAmount', label: '实收金额(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'payWay', label: '方式', width: 90, align: 'center' },
    { prop: 'receivedAt', label: '实收日期', type: 'date', width: 110, align: 'center' },
    { prop: 'contractNo', label: '合同号', minWidth: 120 },
    { prop: 'projectName', label: '项目', minWidth: 120 }
  ],
  DYNAMIC_COST: [
    { prop: 'contractCode', label: '合同编码', minWidth: 130 },
    { prop: 'name', label: '合同名称', minWidth: 150 },
    { prop: 'supplierName', label: '供应商', minWidth: 130 },
    { prop: 'signedAmount', label: '合同额(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'status', label: '状态', type: 'status', width: 90, align: 'center' }
  ],
  TURNOVER_RATE: [
    { prop: 'roomCode', label: '房号', minWidth: 110 },
    { prop: 'buildingId', label: '楼栋', width: 90, align: 'center' },
    { prop: 'area', label: '面积(㎡)', type: 'money', width: 100, align: 'right' },
    { prop: 'tablePrice', label: '表价(元)', type: 'money', width: 120, align: 'right' },
    { prop: 'status', label: '销控状态', type: 'status', width: 100, align: 'center' }
  ],
  DYNAMIC_PROFIT: [
    { prop: 'snapMonth', label: '月份', width: 90, align: 'center' },
    { prop: 'dynamicValue', label: '动态货值(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'dynamicCost', label: '动态成本(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'threeFee', label: '三费(元)', type: 'money', width: 110, align: 'right' },
    { prop: 'taxAmount', label: '税金(元)', type: 'money', width: 110, align: 'right' },
    { prop: 'dynamicProfit', label: '动态利润(元)', type: 'money', width: 130, align: 'right' }
  ],
  CHANGE_RATE: [
    { prop: 'changeCode', label: '变更编码', minWidth: 130 },
    { prop: 'title', label: '变更标题', minWidth: 160 },
    { prop: 'changeType', label: '类型', width: 100, align: 'center' },
    { prop: 'confirmedAmount', label: '确认金额(元)', type: 'money', width: 130, align: 'right' },
    { prop: 'status', label: '状态', type: 'status', width: 90, align: 'center' }
  ],
  PLAN_ACHIEVE_RATE: [
    { prop: 'taskCode', label: '任务编码', minWidth: 120 },
    { prop: 'taskName', label: '任务', minWidth: 150 },
    { prop: 'level', label: '层级', width: 90, align: 'center' },
    { prop: 'alertStatus', label: '预警', type: 'status', width: 90, align: 'center' },
    { prop: 'status', label: '状态', type: 'status', width: 90, align: 'center' },
    { prop: 'planEnd', label: '计划完成', type: 'date', width: 110, align: 'center' }
  ],
  COST_EFFICIENCY: [
    { prop: 'reimbNo', label: '报销单号', minWidth: 130 },
    { prop: 'userName', label: '报销人', minWidth: 100 },
    { prop: 'accountName', label: '科目', minWidth: 140 },
    { prop: 'amount', label: '金额(元)', type: 'money', width: 130, align: 'right' }
  ],
  BUDGET_EXEC_RATE: [
    { prop: 'deptId', label: '部门ID', width: 90, align: 'center' },
    { prop: 'accountName', label: '科目', minWidth: 140 },
    { prop: 'budget', label: '预算(元)', type: 'money', width: 120, align: 'right' },
    { prop: 'occupy', label: '占用(元)', type: 'money', width: 120, align: 'right' },
    { prop: 'writeoff', label: '核销(元)', type: 'money', width: 120, align: 'right' },
    { prop: 'balance', label: '余额(元)', type: 'money', width: 120, align: 'right' }
  ]
}

const columns = computed(() => COLUMN_MAP[props.metricCode] || [])

const loading = ref(false)
const rows = ref([])

const pct = v => (v == null || v === '' ? '-' : Number(v).toFixed(2) + '%')
const statusMeta = v => statusColor(v) || 'info'

const load = async () => {
  if (!props.metricCode) return
  loading.value = true
  try {
    const data = await cockpitDrill(props.metricCode, props.params)
    rows.value = data?.rows || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, v => {
  if (v) load()
})
</script>

<style scoped>
.drill-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.drill-count { font-size: 13px; color: #374151; font-weight: 600; }
.drill-tip { font-size: 12px; color: #909399; }
.num { font-variant-numeric: tabular-nums; }
</style>
