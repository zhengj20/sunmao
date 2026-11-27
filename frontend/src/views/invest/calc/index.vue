<template>
  <div class="page-container">
    <!-- 汇总指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">总货值（万元）</div>
          <div class="value num">{{ wan(summary.saleValue) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">总成本（万元）</div>
          <div class="value num">{{ wan(summary.totalCost) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">净利润（万元）</div>
          <div class="value num" style="color:#67c23a">{{ wan(summary.netProfit) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">平均净利率</div>
          <div class="value num">{{ summary.profitRate }}%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 180px" @change="search">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
          <el-select v-model="query.stage" placeholder="测算阶段" clearable style="width: 140px" @change="search">
            <el-option label="拿地测算" value="LAND" />
            <el-option label="调整版" value="UPDATE" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已锁定" value="LOCKED" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建测算版本</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="versionCode" label="版本编码" width="130" show-overflow-tooltip />
        <el-table-column label="项目" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.stage === 'LAND' ? '拿地测算' : '调整版' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总货值(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.saleValue) }}</span></template>
        </el-table-column>
        <el-table-column label="总成本(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.totalCost) }}</span></template>
        </el-table-column>
        <el-table-column label="净利润(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.netProfit) }}</span></template>
        </el-table-column>
        <el-table-column label="净利率" width="85" align="right">
          <template #default="{ row }">{{ row.profitRate != null ? row.profitRate + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="IRR" width="80" align="right">
          <template #default="{ row }">{{ row.irr != null ? row.irr + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="回收期(年)" width="95" align="right">
          <template #default="{ row }">{{ row.paybackYears ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.locked === 1 ? 'success' : 'info'">
              {{ row.locked === 1 ? '已锁定' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button link @click="openPostEval(row)">后评估</el-button>
            <el-button v-if="row.locked !== 1" type="warning" link @click="onLock(row)">锁定</el-button>
            <el-button v-if="row.locked !== 1" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.locked !== 1" type="danger" link @click="onDelete(row)">删除</el-button>
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

    <!-- 新建/编辑 -->
    <el-dialog v-model="formDlg" :title="form.id ? '编辑测算版本' : '新建测算版本'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%" :disabled="!!form.id">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="p.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="测算阶段" required>
          <el-radio-group v-model="form.stage">
            <el-radio value="LAND">拿地测算</el-radio>
            <el-radio value="UPDATE">调整版</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="总货值(元)" required>
              <el-input-number v-model="form.saleValue" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总成本(元)" required>
              <el-input-number v-model="form.totalCost" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="三费(元)">
              <el-input-number v-model="form.threeFee" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税金(元)">
              <el-input-number v-model="form.taxAmount" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="净利润(元)">
              <el-input-number v-model="form.netProfit" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="IRR(%)">
              <el-input-number v-model="form.irr" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回收期(年)">
              <el-input-number v-model="form.paybackYears" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="决策会记录">
          <el-input v-model="form.decisionNotes" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <div class="form-tip">净利率由系统按 净利润 ÷ 总货值 自动计算；锁定版本后作为后评估基准，不可再修改</div>
      </el-form>
      <template #footer>
        <el-button @click="formDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailDlg" title="测算版本详情" size="520px">
      <div v-if="detail" class="recalc-bar">
        <el-button type="primary" size="small" :loading="recalcLoading" @click="doRecalc">重新计算 IRR / 回收期</el-button>
      </div>
      <div v-if="recalcHint" class="recalc-hint">{{ recalcHint }}</div>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="版本编码">{{ detail.versionCode }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ projectName(detail.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="阶段">{{ detail.stage === 'LAND' ? '拿地测算' : '调整版' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detail.locked === 1 ? 'success' : 'info'">{{ detail.locked === 1 ? '已锁定' : '草稿' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总货值(元)"><span class="num">{{ money(detail.saleValue) }}</span></el-descriptions-item>
        <el-descriptions-item label="总成本(元)"><span class="num">{{ money(detail.totalCost) }}</span></el-descriptions-item>
        <el-descriptions-item label="三费(元)"><span class="num">{{ money(detail.threeFee) }}</span></el-descriptions-item>
        <el-descriptions-item label="税金(元)"><span class="num">{{ money(detail.taxAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="净利润(元)"><span class="num">{{ money(detail.netProfit) }}</span></el-descriptions-item>
        <el-descriptions-item label="净利率">{{ detail.profitRate != null ? detail.profitRate + '%' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="IRR">{{ detail.irr != null ? detail.irr + '%' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="回收期(年)">{{ detail.paybackYears != null ? detail.paybackYears + ' 年' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="决策会记录" :span="2">{{ detail.decisionNotes || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="detailItems.length">
        <div class="sec-title">测算明细</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item v-for="it in detailItems" :key="it.k" :label="it.k">{{ it.v }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 后评估 -->
    <el-dialog v-model="evalDlg" title="投资后评估（拿地测算 vs 当前动态）" width="640px" destroy-on-close>
      <template v-if="evalData">
        <el-alert
          type="info"
          :closable="false"
          :title="`基准版本：${evalData.baseline?.versionCode || '-'}（${evalData.baseline?.stage === 'LAND' ? '拿地测算' : '调整版'}）`"
          style="margin-bottom: 14px"
        />
        <el-table :data="evalRows" border size="small">
          <el-table-column prop="name" label="指标" width="140" />
          <el-table-column label="拿地基线" align="right">
            <template #default="{ row }"><span class="num">{{ row.base }}</span></template>
          </el-table-column>
          <el-table-column label="当前动态" align="right">
            <template #default="{ row }"><span class="num">{{ row.cur }}</span></template>
          </el-table-column>
          <el-table-column label="差异" align="right" width="170">
            <template #default="{ row }">
              <span v-if="row.diff !== '-'" class="num" :class="row.diffNum > 0 ? 'up' : row.diffNum < 0 ? 'down' : ''">
                {{ row.diff }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="eval-note">差异 = 当前动态 − 拿地基线；货值差异含量价因素，成本差异反映动态成本变化</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  calcCreate, calcDelete, calcLock, calcPage, calcPostEval, calcUpdate, recalcCalc
} from '../../../api/invest'
import { projectOptions } from '../../../api/invest'
import { fmtTime, money, wan } from '../../../utils/format'

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || id || '-'

const loading = ref(false)
const allRows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, projectId: null, stage: '', status: '' })

const list = computed(() => allRows.value.slice((query.current - 1) * query.size, query.current * query.size))

// 汇总（按当前过滤条件全量计算）
const summary = computed(() => {
  let sv = 0, tc = 0, np = 0
  allRows.value.forEach(r => {
    sv += Number(r.saleValue || 0)
    tc += Number(r.totalCost || 0)
    np += Number(r.netProfit || 0)
  })
  const rate = sv > 0 ? ((np / sv) * 100).toFixed(2) : '0.00'
  return { saleValue: sv, totalCost: tc, netProfit: np, profitRate: rate }
})

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await calcPage({
      current: 1, size: 500,
      projectId: query.projectId || undefined,
      stage: query.stage || undefined,
      status: query.status || undefined
    })
    allRows.value = data.records || []
    total.value = allRows.value.length
  } finally {
    loading.value = false
  }
}

// ============ 新建/编辑 ============
const formDlg = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, projectId: null, stage: 'LAND', saleValue: null, totalCost: null,
  threeFee: null, taxAmount: null, netProfit: null, irr: null, paybackYears: null, decisionNotes: ''
})
const form = reactive(emptyForm())

const openCreate = () => {
  Object.assign(form, emptyForm())
  formDlg.value = true
}
const openEdit = row => {
  Object.assign(form, emptyForm(), row)
  formDlg.value = true
}
const save = async () => {
  if (!form.projectId) return ElMessage.warning('请选择项目')
  if (form.saleValue == null || form.totalCost == null) return ElMessage.warning('请填写总货值与总成本')
  saving.value = true
  try {
    if (form.id) {
      await calcUpdate(form.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await calcCreate({ ...form })
      ElMessage.success('已创建')
    }
    formDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

const onLock = async row => {
  try {
    await ElMessageBox.confirm(
      `锁定版本「${row.versionCode}」后将成为后评估基准且不可修改，确定锁定吗？`,
      '锁定确认',
      { type: 'warning', confirmButtonText: '锁定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await calcLock(row.id)
  ElMessage.success('已锁定')
  load()
}

const onDelete = async row => {
  try {
    await ElMessageBox.confirm(`确定删除测算版本「${row.versionCode}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await calcDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 详情 ============
const detailDlg = ref(false)
const detail = ref(null)
const detailItems = computed(() => {
  if (!detail.value?.detailJson) return []
  try {
    const o = JSON.parse(detail.value.detailJson)
    return Object.entries(o).map(([k, v]) => ({
      k: String(k),
      v: typeof v === 'object' ? JSON.stringify(v) : String(v ?? '-')
    }))
  } catch {
    return []
  }
})
const recalcLoading = ref(false)
const recalcHint = ref('')
const openDetail = row => {
  detail.value = row
  recalcHint.value = ''
  detailDlg.value = true
}

// ============ 重新计算 IRR / 回收期 ============
const doRecalc = async () => {
  recalcLoading.value = true
  recalcHint.value = ''
  try {
    const res = await recalcCalc(detail.value.id)
    const idx = allRows.value.findIndex(r => r.id === detail.value.id)
    if (idx >= 0) {
      allRows.value[idx] = { ...allRows.value[idx], irr: res.irr, paybackYears: res.paybackYears }
      detail.value = { ...detail.value, irr: res.irr, paybackYears: res.paybackYears }
    }
    recalcHint.value = res.hint || (res.irr != null || res.paybackYears != null ? '已重新计算' : '')
    ElMessage.success('已重新计算')
  } finally {
    recalcLoading.value = false
  }
}

// ============ 后评估 ============
const evalDlg = ref(false)
const evalData = ref(null)
const evalRows = computed(() => {
  const d = evalData.value
  if (!d) return []
  const b = d.baseline || {}
  const c = d.current || {}
  const diff = d.diff || {}
  const mk = (name, base, cur, dv, unit = '') => ({
    name,
    base: base != null ? money(base) + unit : '-',
    cur: cur != null ? money(cur) + unit : '-',
    diff: dv != null ? (dv > 0 ? '+' : '') + money(dv) : '-',
    diffNum: dv != null ? Number(dv) : 0
  })
  return [
    mk('总货值(元)', b.saleValue, c.dynamicValue, diff.valueDiff),
    mk('总成本(元)', b.totalCost, c.dynamicCost, diff.costDiff),
    mk('净利润(元)', b.netProfit, c.dynamicProfit, diff.profitDiff),
    {
      name: '净利率',
      base: b.profitRate != null ? b.profitRate + '%' : '-',
      cur: c.profitRate != null ? c.profitRate + '%' : '-',
      diff: '-', diffNum: 0
    }
  ]
})
const openPostEval = async row => {
  evalData.value = await calcPostEval(row.id)
  evalDlg.value = true
}

const route = useRoute()

onMounted(async () => {
  projects.value = await projectOptions()
  if (route.query.projectId) query.projectId = Number(route.query.projectId)
  load()
})
</script>

<style scoped>
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 22px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.form-tip { font-size: 12px; color: #9ca3af; padding-left: 12px; margin-bottom: 4px; }
.sec-title {
  font-size: 13.5px; font-weight: 700; color: #0c447c;
  margin: 16px 0 12px; padding-left: 8px; border-left: 3px solid #185fa5;
}
.up { color: #f56c6c; }
.down { color: #67c23a; }
.eval-note { font-size: 12px; color: #9ca3af; margin-top: 10px; }
.recalc-bar { margin-bottom: 12px; }
.recalc-hint { font-size: 12px; color: #e6a23c; margin-bottom: 12px; }
</style>
