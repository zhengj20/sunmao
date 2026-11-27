<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="集团指标库（全部）" clearable filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.indicatorType" placeholder="指标类型" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="(label, key) in INDICATOR_TYPE" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="query.productCategory" placeholder="业态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
          <el-input v-model="query.kw" placeholder="指标名称/科目编码" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增指标</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="label">指标总数</div>
          <div class="value">{{ summary.count ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="label">单方成本指标（元/㎡）</div>
          <div class="value">{{ summary.costCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="label">含量指标（kg/㎡ 等）</div>
          <div class="value">{{ summary.contentCount ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="accountCode" label="科目编码" width="100" align="center" />
        <el-table-column label="科目名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ accountNameOf(row.accountCode) }}</template>
        </el-table-column>
        <el-table-column prop="productCategory" label="业态" width="100" align="center">
          <template #default="{ row }">{{ row.productCategory || '-' }}</template>
        </el-table-column>
        <el-table-column label="指标类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.indicatorType === 'COST' ? '' : 'success'" effect="plain">
              {{ INDICATOR_TYPE[row.indicatorType] || row.indicatorType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="indicatorName" label="指标名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="90" align="center" />
        <el-table-column label="指标值" width="120" align="right">
          <template #default="{ row }">
            <span class="num" style="font-weight: 600">{{ row.value != null ? Number(row.value).toLocaleString('zh-CN') : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.source || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="140" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 新增/编辑指标 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑指标' : '新增指标'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="科目" required>
              <el-select v-model="form.accountCode" filterable style="width: 100%">
                <el-option v-for="a in accountLeafs" :key="a.code" :label="a.code + ' ' + a.name" :value="a.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业态" required>
              <el-select v-model="form.productCategory" allow-create filterable default-first-option style="width: 100%">
                <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标类型" required>
              <el-radio-group v-model="form.indicatorType">
                <el-radio v-for="(label, key) in INDICATOR_TYPE" :key="key" :value="key">{{ label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标名称" required>
              <el-input v-model="form.indicatorName" maxlength="50" placeholder="如：钢筋含量" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" required>
              <el-select v-model="form.unit" allow-create filterable default-first-option style="width: 100%">
                <el-option v-for="u in UNITS" :key="u" :label="u" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指标值" required>
              <el-input-number v-model="form.value" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="来源">
              <el-input v-model="form.source" maxlength="60" placeholder="如：星汇湾一期/基准版" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, fetchAccountTree,
  fetchIndicators, fetchIndicatorSummary, createIndicator, updateIndicator, deleteIndicator
} from '../../../api/cost'

const INDICATOR_TYPE = { COST: '单方成本', CONTENT: '含量指标' }
const CATEGORIES = ['高层住宅', '多层住宅', '洋房', '别墅', '商铺', '车库', '办公', '公寓']
const UNITS = ['元/㎡', 'kg/㎡', 'm³/㎡', '㎡/户', '元/个', 'kVA/㎡']

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const accountLeafs = ref([])
const rows = ref([])
const total = ref(0)
const summary = ref({})
const query = reactive({ projectId: null, indicatorType: null, productCategory: null, kw: '', current: 1, size: 20 })

const formVisible = ref(false)
const form = reactive({ id: null, accountCode: null, productCategory: '', indicatorType: 'COST', indicatorName: '', unit: '元/㎡', value: null, source: '' })

const accountNameOf = code => {
  const acc = accountLeafs.value.find(a => a.code === code)
  return acc?.name || (code ? '（集团科目模板）' : '-')
}

const flattenLeafs = (list, out = []) => {
  ;(list || []).forEach(n => {
    if (n.children?.length) flattenLeafs(n.children, out)
    else out.push(n)
  })
  return out
}

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchIndicators(query),
      fetchIndicatorSummary({ projectId: query.projectId })
    ])
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
    summary.value = sum || {}
  } finally {
    loading.value = false
  }
}

const onSearch = () => { query.current = 1; loadList() }
const onReset = () => {
  query.projectId = null
  query.indicatorType = null
  query.productCategory = null
  query.kw = ''
  onSearch()
}

const openForm = row => {
  if (row) {
    Object.assign(form, {
      id: row.id, accountCode: row.accountCode, productCategory: row.productCategory || '',
      indicatorType: row.indicatorType || 'COST', indicatorName: row.indicatorName || '',
      unit: row.unit || '元/㎡', value: row.value, source: row.source || ''
    })
  } else {
    Object.assign(form, { id: null, accountCode: null, productCategory: '', indicatorType: 'COST', indicatorName: '', unit: '元/㎡', value: null, source: '' })
  }
  formVisible.value = true
}

const onSave = async () => {
  if (!form.accountCode) return ElMessage.warning('请选择科目')
  if (!form.productCategory) return ElMessage.warning('请填写业态')
  if (!form.indicatorName) return ElMessage.warning('请填写指标名称')
  if (!form.unit) return ElMessage.warning('请填写单位')
  if (form.value == null) return ElMessage.warning('请填写指标值')
  saving.value = true
  try {
    if (form.id) {
      await updateIndicator({ ...form, projectId: query.projectId })
      ElMessage.success('指标已更新')
    } else {
      await createIndicator({ ...form, projectId: query.projectId })
      ElMessage.success('指标已创建')
    }
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除指标【${row.indicatorName}】？`, '提示', { type: 'warning' })
  await deleteIndicator(row.id)
  ElMessage.success('已删除')
  await loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  accountLeafs.value = flattenLeafs(await fetchAccountTree({}))
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
</style>
