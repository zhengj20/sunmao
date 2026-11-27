<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 220px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="planId" placeholder="选择合约规划" style="width: 300px" @change="loadDetail">
            <el-option v-for="p in plans" :key="p.id" :label="p.planCode + '（' + (p.name || '') + '）'" :value="p.id" />
          </el-select>
          <el-tag v-if="currentPlan" :type="statusColor(currentPlan.status)" size="small">
            {{ PLAN_STATUS[currentPlan.status] || currentPlan.status }}
          </el-tag>
        </div>
        <div class="actions">
          <el-button v-if="editable" type="success" @click="onSubmit">提交审批</el-button>
          <el-button v-if="currentPlan && currentPlan.status === 'APPROVED'" type="warning" @click="onSubmitAdjustment">提交调整</el-button>
          <el-button @click="openEdit" :disabled="!currentPlan">编辑规划</el-button>
          <el-button type="danger" plain :disabled="!currentPlan" @click="onDelete">删除</el-button>
          <el-button type="primary" @click="createVisible = true">新建规划</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">规划总额（元）</div>
          <div class="value">{{ money(stat.total) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">已签约占用（元）</div>
          <div class="value">{{ money(stat.contracted) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">在途占用（元）</div>
          <div class="value">{{ money(stat.pending) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">待签约余额（元）</div>
          <div class="value">{{ money(stat.total - stat.contracted - stat.pending) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <div class="table-head">
        <span class="title">规划明细（末级科目可编辑规划金额；已签约/在途由合同签订与审批自动回写）</span>
        <el-button v-if="editable" type="primary" size="small" :loading="saving" @click="onSaveItems">保存明细</el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="key"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        max-height="560"
      >
        <el-table-column prop="code" label="科目编码" width="110" />
        <el-table-column prop="name" label="科目名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'parent-name': !row.leaf }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规划金额（元）" width="160" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="row.leaf && editable"
              v-model="row.plannedAmount"
              :controls="false"
              :min="0"
              size="small"
              style="width: 130px"
            />
            <span v-else class="num" :class="{ 'parent-amount': !row.leaf }">{{ money(row.plannedAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已签约（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.contractedAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在途（元）" width="120" align="right">
          <template #default="{ row }">
            <span class="num">{{ money(row.pendingAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="余额（元）" width="130" align="right">
          <template #default="{ row }">
            <span class="num" :style="rowBalance(row) < 0 ? 'color:#c45656;font-weight:600' : ''">{{ money(rowBalance(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="签约占比" width="150">
          <template #default="{ row }">
            <el-progress
              v-if="Number(row.plannedAmount) > 0"
              :percentage="occupyPct(row)"
              :stroke-width="12"
              :color="progressColor(row)"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="范围描述" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.scope || '-' }}</template>
        </el-table-column>
        <el-table-column label="拟签时间" width="100" align="center">
          <template #default="{ row }">{{ fmtDate(row.expectedSignDate) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑规划 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑合约规划' : '新建合约规划'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规划名称" required>
          <el-input v-model="form.name" maxlength="60" placeholder="如：星汇湾一期合约规划" />
        </el-form-item>
        <el-form-item label="依据目标版本" required>
          <el-select v-model="form.targetVersionId" style="width: 100%" placeholder="选择已生效的目标成本版本">
            <el-option
              v-for="v in targetVersions"
              :key="v.id"
              :label="v.versionCode + '（' + dictLabel('target_stage', v.stage) + '）'"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePlan">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, statusColor, dictLabel } from '../../../utils/format'
import {
  fetchProjectOptions, fetchAccountTree, fetchPlans, fetchPlanDetail,
  createPlan, updatePlan, deletePlan, savePlanItems, fetchTargetVersions,
  submitPlan, submitPlanAdjustment
} from '../../../api/cost'

const PLAN_STATUS = { DRAFT: '草稿', APPROVING: '审批中', APPROVED: '已生效' }

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const projectId = ref(null)
const plans = ref([])
const planId = ref(null)
const accounts = ref([])
const items = ref([])
const targetVersions = ref([])
const formVisible = ref(false)
const form = reactive({ id: null, name: '', targetVersionId: null, remark: '' })

const currentPlan = computed(() => plans.value.find(p => p.id === planId.value) || null)
const editable = computed(() => currentPlan.value?.status === 'DRAFT')

const stat = computed(() => ({
  total: treeData.value.reduce((s, r) => s + (Number(r.plannedAmount) || 0), 0),
  contracted: treeData.value.reduce((s, r) => s + (Number(r.contractedAmount) || 0), 0),
  pending: treeData.value.reduce((s, r) => s + (Number(r.pendingAmount) || 0), 0)
}))

const buildTree = () => {
  const itemMap = new Map(items.value.map(i => [i.accountCode, i]))
  const walk = node => {
    const it = itemMap.get(node.code)
    const row = {
      key: 'a' + node.id, accountId: node.id, code: node.code, name: node.name, leaf: !!node.leaf,
      plannedAmount: it?.plannedAmount ?? null, contractedAmount: it?.contractedAmount ?? null,
      pendingAmount: it?.pendingAmount ?? null, scope: it?.scope || '',
      expectedSignDate: it?.expectedSignDate || null, children: []
    }
    ;(node.children || []).forEach(c => row.children.push(walk(c)))
    if (row.children.length) {
      sumRow(row)
    }
    return row
  }
  return (accounts.value || []).map(walk)
}
const sumRow = row => {
  row.plannedAmount = sumChildren(row, 'plannedAmount')
  row.contractedAmount = sumChildren(row, 'contractedAmount')
  row.pendingAmount = sumChildren(row, 'pendingAmount')
}
const sumChildren = (row, field) => {
  const v = row.children.reduce((s, c) => s + (Number(c[field]) || 0), 0)
  return v || null
}

const treeData = ref([])

const rowBalance = row => (Number(row.plannedAmount) || 0) - (Number(row.contractedAmount) || 0) - (Number(row.pendingAmount) || 0)
const occupyPct = row => {
  const planned = Number(row.plannedAmount) || 0
  if (!planned) return 0
  return Math.min(100, Math.round(((Number(row.contractedAmount) || 0) / planned) * 100))
}
const progressColor = row => (rowBalance(row) < 0 ? '#c45656' : '#185fa5')

const loadProjects = async () => {
  projects.value = await fetchProjectOptions()
  if (projects.value.length) {
    projectId.value = projects.value[0].id
    await onProjectChange()
  }
}

const onProjectChange = async () => {
  plans.value = []
  planId.value = null
  items.value = []
  const [page, tree, versions] = await Promise.all([
    fetchPlans({ projectId: projectId.value, current: 1, size: 100 }),
    fetchAccountTree({ projectId: projectId.value }),
    fetchTargetVersions({ projectId: projectId.value })
  ])
  plans.value = page?.records || []
  accounts.value = tree || []
  targetVersions.value = (versions || []).filter(v => v.status === 'APPROVED' || v.status === 'LOCKED')
  if (plans.value.length) planId.value = plans.value[0].id
  await loadDetail()
}

const loadDetail = async () => {
  if (!planId.value) { items.value = []; treeData.value = buildTree(); return }
  loading.value = true
  try {
    const detail = await fetchPlanDetail(planId.value)
    items.value = detail?.items || []
  } finally {
    loading.value = false
  }
  treeData.value = buildTree()
}

const openEdit = () => {
  const p = currentPlan.value
  if (!p) return
  Object.assign(form, { id: p.id, name: p.name || '', targetVersionId: p.targetVersionId, remark: p.remark || '' })
  formVisible.value = true
}

const onSavePlan = async () => {
  if (!form.name) return ElMessage.warning('请填写规划名称')
  if (!form.targetVersionId) return ElMessage.warning('请选择依据的目标成本版本')
  saving.value = true
  try {
    if (form.id) {
      await updatePlan({ id: form.id, projectId: projectId.value, ...form })
      ElMessage.success('规划已更新')
    } else {
      await createPlan({ projectId: projectId.value, ...form })
      ElMessage.success('规划已创建')
    }
    formVisible.value = false
    const page = await fetchPlans({ projectId: projectId.value, current: 1, size: 100 })
    plans.value = page?.records || []
    if (!planId.value && plans.value.length) planId.value = plans.value[0].id
    await loadDetail()
  } finally {
    saving.value = false
  }
}

const onDelete = async () => {
  await ElMessageBox.confirm(`确认删除规划【${currentPlan.value.planCode}】及其明细？`, '提示', { type: 'warning' })
  await deletePlan(planId.value)
  ElMessage.success('已删除')
  planId.value = null
  const page = await fetchPlans({ projectId: projectId.value, current: 1, size: 100 })
  plans.value = page?.records || []
  if (plans.value.length) planId.value = plans.value[0].id
  await loadDetail()
}

const collectLeafs = rows => rows.flatMap(r => r.children?.length ? collectLeafs(r.children) : [r])
const onSaveItems = async () => {
  const leafs = collectLeafs(treeData.value).filter(r => r.plannedAmount != null)
  if (!leafs.length) return ElMessage.warning('暂无可保存的明细')
  saving.value = true
  try {
    await savePlanItems(planId.value, leafs.map(r => ({
      accountId: r.accountId, accountCode: r.code, accountName: r.name,
      plannedAmount: r.plannedAmount, scope: r.scope, expectedSignDate: r.expectedSignDate
    })))
    ElMessage.success('明细已保存')
    await loadDetail()
  } finally {
    saving.value = false
  }
}

const onSubmit = async () => {
  await ElMessageBox.confirm('提交审批后合约规划将进入审批流程，确认提交？', '提示', { type: 'warning' })
  await submitPlan(planId.value)
  ElMessage.success('已提交审批')
  const page = await fetchPlans({ projectId: projectId.value, current: 1, size: 100 })
  plans.value = page?.records || []
  await loadDetail()
}

const onSubmitAdjustment = async () => {
  await ElMessageBox.confirm('提交调整审批后合约规划修改将进入审批流程，确认提交？', '提示', { type: 'warning' })
  await submitPlanAdjustment(planId.value)
  ElMessage.success('已提交调整审批')
  const page = await fetchPlans({ projectId: projectId.value, current: 1, size: 100 })
  plans.value = page?.records || []
  await loadDetail()
}

onMounted(loadProjects)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; }
.stat-row { margin-bottom: 12px; }
.table-head { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid #ebeef5; }
.table-head .title { font-size: 13px; color: #6b7280; }
.parent-name { font-weight: 600; color: #0c447c; }
.parent-amount { font-weight: 600; color: #0c447c; }
</style>
