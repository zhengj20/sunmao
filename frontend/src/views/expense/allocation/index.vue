<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="分摊月" clearable style="width: 150px" @change="onSearch" />
          <el-select v-model="query.deptId" placeholder="部门" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openCalc()">分摊计算</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="allocNo" label="分摊单号" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.allocNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="month" label="分摊月" width="110" align="center">
          <template #default="{ row }">{{ row.month || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ruleText" label="分摊规则" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ruleText || '-' }}</template>
        </el-table-column>
        <el-table-column label="分摊部门" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column label="分摊项目" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ projName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="分摊金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 分摊计算 -->
    <el-dialog v-model="calcVisible" title="月度分摊计算（总额均摊）" width="520px">
      <el-form label-width="110px">
        <el-form-item label="分摊月" required>
          <el-date-picker v-model="calc.month" type="month" value-format="YYYY-MM" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分摊总额" required>
          <el-input-number v-model="calc.total" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分摊目标部门" required>
          <el-select v-model="calc.deptIds" multiple filterable style="width: 100%" placeholder="选择多个部门均摊">
            <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
          <div class="hint">总额按所选部门数量均摊，尾差归末位部门。已选 {{ calc.deptIds.length }} 个目标。</div>
        </el-form-item>
        <el-form-item v-if="calc.total > 0 && calc.deptIds.length" label="单目标金额">
          <span class="num">{{ money(perTarget) }} 元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="calcVisible = false">取消</el-button>
        <el-button type="primary" :loading="calc.loading" @click="onCalc">计算并生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  fetchOrgOptions, fetchProjectOptions,
  allocationPage, allocationDelete, allocationCalculate
} from '../../../api/expense'

const orgs = ref([])
const projects = ref([])
const deptName = id => orgs.value.find(o => o.id === id)?.name || (id ? '部门#' + id : '-')
const projName = id => projects.value.find(p => p.id === id)?.name || (id ? '项目#' + id : '-')

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ month: null, deptId: null, projectId: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await allocationPage({
      current: query.current, size: query.size,
      month: query.month || undefined, deptId: query.deptId || undefined, projectId: query.projectId || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.month = null; query.deptId = null; query.projectId = null; onSearch() }
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除分摊记录 ${row.allocNo || row.id}？`, '提示', { type: 'warning' })
  await allocationDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

const calcVisible = ref(false)
const calc = reactive({ month: new Date().toISOString().slice(0, 7), total: null, deptIds: [], loading: false })
const perTarget = computed(() => calc.deptIds.length ? (Number(calc.total) / calc.deptIds.length) : 0)
const openCalc = () => {
  Object.assign(calc, { month: query.month || new Date().toISOString().slice(0, 7), total: null, deptIds: [], loading: false })
  calcVisible.value = true
}
const onCalc = async () => {
  if (!calc.month) return ElMessage.warning('请选择分摊月')
  if (!calc.total || Number(calc.total) <= 0) return ElMessage.warning('请填写分摊总额')
  if (!calc.deptIds.length) return ElMessage.warning('请选择分摊目标部门')
  calc.loading = true
  try {
    const n = await allocationCalculate({ month: calc.month, total: calc.total, deptIds: calc.deptIds })
    ElMessage.success(`分摊计算完成，已生成 ${n} 条分摊记录`)
    calcVisible.value = false
    query.month = calc.month
    loadList()
  } finally {
    calc.loading = false
  }
}

onMounted(async () => {
  orgs.value = await fetchOrgOptions()
  projects.value = await fetchProjectOptions().catch(() => [])
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
