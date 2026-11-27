<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="budgetId" placeholder="选择年度预算" filterable style="width: 340px" @change="loadMonths">
            <el-option v-for="b in budgets" :key="b.id"
              :label="`${b.budgetCode} | ${budTypeLabel(b.budgetType)} | ${b.projectName || b.deptName || ''} | ${b.accountName}`"
              :value="b.id" />
          </el-select>
        </div>
        <div class="actions" v-if="current">
          <el-button type="primary" @click="openUpsert()">维护月度预算</el-button>
          <el-button type="primary" plain @click="openAdjust(null)">月度调整</el-button>
          <el-button type="primary" plain @click="openReallocate()">跨月调剂</el-button>
        </div>
      </div>
    </el-card>

    <el-row v-if="current" :gutter="12" class="stat-row">
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">年度预算（元）</div><div class="value">{{ money(current.annualAmount) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已分解（元）</div><div class="value">{{ money(sumAllocated) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">已执行（元）</div><div class="value">{{ money(sumActual) }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="label">未分解余额（元）</div><div class="value">{{ money(current.annualAmount - sumAllocated) }}</div></el-card></el-col>
    </el-row>

    <el-card v-if="current" shadow="never" class="table-card">
      <el-alert type="info" :closable="false" class="mb"
        title="管控规则：B1 年度控制月度（Σ月度预算 ≤ 年度预算）；月度预算控制资金计划（B3 成本线按合同科目、B4 费用线按费用科目）" />
      <el-table v-loading="loading" :data="rows" border stripe show-summary :summary-method="summary">
        <el-table-column prop="month" label="月份" width="80" align="center">
          <template #default="{ row }">{{ row.month }}月</template>
        </el-table-column>
        <el-table-column label="月度预算（元）" width="160" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.monthAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已执行（元）" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.actualAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额（元）" width="150" align="right">
          <template #default="{ row }">
            <span class="num" :style="Number(row.balance) < 0 ? 'color:#c45656;font-weight:600' : ''">{{ money(row.balance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.monthAmount == null && !(Number(row.actualAmount) > 0)" type="info" size="small">未分解</el-tag>
            <el-tag v-else-if="row.overBudget" type="danger" size="small">超执行</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120" align="center">
          <template #default="{ row }">
            <template v-if="row.monthAmount != null">
              <el-button link type="primary" size="small" @click="openUpsert(row)">调整</el-button>
              <el-button link type="primary" size="small" @click="openAdjust(row)">增减</el-button>
            </template>
            <el-button v-else link type="primary" size="small" @click="openUpsert(row)">编制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-empty v-else description="请选择年度预算" :image-size="80" />

    <!-- 月度预算编制/调整（B1） -->
    <el-dialog v-model="upsertVisible" :title="upsertMonth ? `${upsertMonth.month}月预算` : '月度预算编制'" width="460px">
      <el-alert type="info" :closable="false" class="mb"
        :title="`年度预算 ${money(current?.annualAmount)} 元，未分解余额 ${money(unallocated)} 元（B1 约束）`" />
      <el-form label-width="100px">
        <el-form-item label="月份" required>
          <el-select v-model="upsertForm.month" style="width: 100%">
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="月度预算额" required>
          <el-input-number v-model="upsertForm.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="upsertVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onUpsert">保存</el-button>
      </template>
    </el-dialog>

    <!-- 月度增减（B1 + 不低于已执行） -->
    <el-dialog v-model="adjustVisible" title="月度预算调整" width="460px">
      <el-alert type="info" :closable="false" class="mb"
        :title="adjustTarget ? `${adjustTarget.month}月当前预算 ${money(adjustTarget.monthAmount)} 元，已执行 ${money(adjustTarget.actualAmount)} 元` : '请选择要调整的月份'" />
      <el-form label-width="90px">
        <el-form-item label="月份" required v-if="adjustTarget">
          <el-input :model-value="adjustTarget.month + '月'" disabled style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整方向" required>
          <el-radio-group v-model="adjustForm.adjustType">
            <el-radio value="INCREASE">调增</el-radio>
            <el-radio value="DECREASE">调减</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整金额" required>
          <el-input-number v-model="adjustForm.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdjust">提交调整</el-button>
      </template>
    </el-dialog>

    <!-- 跨月调剂 -->
    <el-dialog v-model="reallocVisible" title="跨月调剂" width="480px">
      <el-alert type="info" :closable="false" class="mb"
        title="将源月预算调出、调入目标月（总分解额不变，仍受 B1 约束）" />
      <el-form label-width="90px">
        <el-form-item label="调出月" required>
          <el-select v-model="reallocForm.fromMonth" style="width: 100%">
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="调入月" required>
          <el-select v-model="reallocForm.toMonth" style="width: 100%">
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="调剂金额" required>
          <el-input-number v-model="reallocForm.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调剂原因" required>
          <el-input v-model="reallocForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reallocVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReallocate">提交调剂</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { money } from '../../../utils/format'
import {
  BUD_STATUS, budTypeLabel, statusMeta, annualPage,
  monthlyList, monthUpsert, monthAdjust, monthReallocate
} from '../../../api/budget'

const budgets = ref([])
const budgetId = ref(null)
const current = computed(() => budgets.value.find(b => b.id === budgetId.value) || null)
const loading = ref(false)
const saving = ref(false)
const rows = ref([])

const sumAllocated = computed(() => rows.value.reduce((s, r) => s + (Number(r.monthAmount) || 0), 0))
const sumActual = computed(() => rows.value.reduce((s, r) => s + (Number(r.actualAmount) || 0), 0))
const unallocated = computed(() => (current.value?.annualAmount || 0) - sumAllocated.value)

const loadBudgets = async () => {
  budgets.value = (await annualPage({ current: 1, size: 200, year: new Date().getFullYear(), status: 'APPROVED' }).catch(() => ({})))?.records || []
  if (budgets.value.length && !budgetId.value) budgetId.value = budgets.value[0].id
  loadMonths()
}

const loadMonths = async () => {
  if (!budgetId.value) { rows.value = []; return }
  loading.value = true
  try {
    rows.value = (await monthlyList(budgetId.value).catch(() => [])) || []
  } finally { loading.value = false }
}

const summary = () => [
  '合计',
  money(sumAllocated.value),
  money(sumActual.value),
  money(sumAllocated.value - sumActual.value),
  '', ''
]

// ===== 编制/调整某月（B1） =====
const upsertVisible = ref(false)
const upsertForm = reactive({ month: new Date().getMonth() + 1, amount: null })
const openUpsert = row => {
  Object.assign(upsertForm, { month: row ? row.month : upsertForm.month, amount: row ? row.monthAmount : null })
  upsertVisible.value = true
}
const onUpsert = async () => {
  if (!upsertForm.amount || Number(upsertForm.amount) <= 0) return ElMessage.warning('请填写月度预算额')
  saving.value = true
  try {
    await monthUpsert({ budgetId: budgetId.value, month: upsertForm.month, monthAmount: upsertForm.amount })
    ElMessage.success(upsertForm.amount ? '月度预算已保存' : '已清零')
    upsertVisible.value = false
    loadMonths()
  } finally { saving.value = false }
}

// ===== 月度增减 =====
const adjustVisible = ref(false)
const adjustTarget = ref(null)
const adjustForm = reactive({ adjustType: 'INCREASE', amount: null, reason: '' })
const openAdjust = row => {
  if (!row) {
    const m = rows.value.find(x => x.monthAmount != null)
    if (!m) return ElMessage.warning('请先编制至少一个月的预算')
    adjustTarget.value = m
  } else {
    adjustTarget.value = row
  }
  Object.assign(adjustForm, { adjustType: 'INCREASE', amount: null, reason: '' })
  adjustVisible.value = true
}
const onAdjust = async () => {
  if (!adjustTarget.value) return ElMessage.warning('请选择月份')
  if (!adjustForm.amount || Number(adjustForm.amount) <= 0) return ElMessage.warning('请填写调整金额')
  if (!adjustForm.reason) return ElMessage.warning('请填写调整原因')
  saving.value = true
  try {
    await monthAdjust(adjustTarget.value.id, { adjustType: adjustForm.adjustType, amount: adjustForm.amount, reason: adjustForm.reason })
    ElMessage.success('月度预算已调整，留痕已记录')
    adjustVisible.value = false
    loadMonths()
  } finally { saving.value = false }
}

// ===== 跨月调剂 =====
const reallocVisible = ref(false)
const reallocForm = reactive({ fromMonth: 1, toMonth: 2, amount: null, reason: '' })
const openReallocate = () => {
  Object.assign(reallocForm, { fromMonth: 1, toMonth: 2, amount: null, reason: '' })
  reallocVisible.value = true
}
const onReallocate = async () => {
  if (reallocForm.fromMonth === reallocForm.toMonth) return ElMessage.warning('调出月与调入月不能相同')
  if (!reallocForm.amount || Number(reallocForm.amount) <= 0) return ElMessage.warning('请填写调剂金额')
  if (!reallocForm.reason) return ElMessage.warning('请填写调剂原因')
  saving.value = true
  try {
    await monthReallocate(budgetId.value, { ...reallocForm })
    ElMessage.success('跨月调剂完成，留痕已记录')
    reallocVisible.value = false
    loadMonths()
  } finally { saving.value = false }
}

onMounted(loadBudgets)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.mb { margin-bottom: 12px; }
</style>
