<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.level" placeholder="职级" clearable style="width: 110px" @change="onSearch">
            <el-option v-for="v in STD_LEVELS" :key="v" :label="v" :value="v" />
          </el-select>
          <el-select v-model="query.cityLevel" placeholder="城市等级" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in STD_CITY_LEVELS" :key="v" :label="v" :value="v" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="t in STD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增标准</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="level" label="职级" width="120" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.level }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="cityLevel" label="城市等级" width="140" align="center">
          <template #default="{ row }">{{ row.cityLevel }}</template>
        </el-table-column>
        <el-table-column label="费用类型" min-width="160" align="center">
          <template #default="{ row }">{{ stdTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="限额标准（元）" width="160" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.limitAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="更新时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
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

    <!-- 新增 / 编辑标准 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑标准' : '新增费控标准'" width="480px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="职级" required>
              <el-select v-model="form.level" style="width: 100%">
                <el-option v-for="v in STD_LEVELS" :key="v" :label="v" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市等级" required>
              <el-select v-model="form.cityLevel" style="width: 100%">
                <el-option v-for="v in STD_CITY_LEVELS" :key="v" :label="v" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="费用类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in STD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="限额标准" required>
          <el-input-number v-model="form.limitAmount" :controls="false" :min="0" :precision="2" style="width: 100%" />
          <div class="hint">单位：元（金额上限，超标触发 R3 特批）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  standardPage, standardCreate, standardUpdate, standardDelete,
  STD_LEVELS, STD_CITY_LEVELS, STD_TYPES, stdTypeLabel
} from '../../../api/expense'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ level: null, cityLevel: null, type: null, current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await standardPage({
      current: query.current, size: query.size,
      level: query.level || undefined, cityLevel: query.cityLevel || undefined, type: query.type || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.level = null; query.cityLevel = null; query.type = null; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, level: 'E7', cityLevel: '一线', type: 'TRAVEL', limitAmount: null })
const openForm = row => {
  if (row) Object.assign(form, { id: row.id, level: row.level, cityLevel: row.cityLevel, type: row.type, limitAmount: row.limitAmount })
  else Object.assign(form, { id: null, level: 'E7', cityLevel: '一线', type: 'TRAVEL', limitAmount: null })
  formVisible.value = true
}
const onSave = async () => {
  if (!form.level) return ElMessage.warning('请选择职级')
  if (!form.cityLevel) return ElMessage.warning('请选择城市等级')
  if (!form.type) return ElMessage.warning('请选择费用类型')
  if (!form.limitAmount || Number(form.limitAmount) < 0) return ElMessage.warning('请填写限额标准')
  saving.value = true
  try {
    const payload = { level: form.level, cityLevel: form.cityLevel, type: form.type, limitAmount: form.limitAmount }
    if (form.id) { await standardUpdate({ id: form.id, ...payload }); ElMessage.success('标准已更新') }
    else { await standardCreate(payload); ElMessage.success('标准已新增') }
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除标准【${row.level} / ${row.cityLevel} / ${stdTypeLabel(row.type)}】？`, '提示', { type: 'warning' })
  await standardDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
