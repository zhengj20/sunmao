<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.userId" placeholder="借款人" clearable filterable style="width: 180px" @change="onSearch">
            <el-option v-for="u in employees" :key="u.userId" :label="u.name" :value="u.userId" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(LOAN_STATUS)" :key="v" :label="LOAN_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="借据号" clearable style="width: 160px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新建借款</el-button>
      </div>
    </el-card>

    <el-alert type="warning" :closable="false" show-icon class="rule-alert">
      <template #title>额度控制（R4）：同一借款人未冲销余额合计超上限、或存在逾期未冲销借款时，将阻断新借款。</template>
    </el-alert>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="loanNo" label="借据号" width="150" show-overflow-tooltip />
        <el-table-column label="借款人" width="120" align="center">
          <template #default="{ row }">{{ row.userName || '-' }}</template>
        </el-table-column>
        <el-table-column label="借款金额（元）" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column label="未冲销余额（元）" width="150" align="right">
          <template #default="{ row }">
            <span class="num" :style="Number(row.unwriteoffBalance) > 0 ? 'color:#c45656;font-weight:600' : ''">{{ money(row.unwriteoffBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="借款日" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.borrowDate) }}</template>
        </el-table-column>
        <el-table-column label="到期日" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'WRITEOFF'" link type="success" size="small" @click="openWriteoff(row)">冲销</el-button>
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

    <!-- 新建借款 -->
    <el-dialog v-model="formVisible" title="新建借款" width="520px">
      <el-form label-width="90px">
        <el-form-item label="借款人" required>
          <el-select v-model="form.userId" filterable style="width: 100%" @change="onUserChange">
            <el-option v-for="u in employees" :key="u.userId" :label="u.name" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="借款金额" required>
          <el-input-number v-model="form.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="借款日期">
              <el-date-picker v-model="form.borrowDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应冲销日期">
              <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <div class="hint">登记后立即发起借款审批。若该借款人存在逾期未冲销借款或额度超限，将被阻断（R4）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存并发起审批</el-button>
      </template>
    </el-dialog>

    <!-- 冲销 -->
    <el-dialog v-model="writeoffVisible" title="借款冲销/还款" width="440px">
      <el-form label-width="90px">
        <el-form-item label="借据号">
          <span>{{ writeoff.row?.loanNo }}</span>
        </el-form-item>
        <el-form-item label="未冲销余额">
          <span class="num">{{ money(writeoff.row?.unwriteoffBalance) }} 元</span>
        </el-form-item>
        <el-form-item label="本次冲销" required>
          <el-input-number v-model="writeoff.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
          <div class="hint">留空或等于余额表示全额冲销（清零后置已冲销）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeoffVisible = false">取消</el-button>
        <el-button type="primary" :loading="writeoff.saving" @click="onWriteoff">确认冲销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { money, fmtDate } from '../../../utils/format'
import {
  fetchEmployeeOptions, loanPage, loanCreate, loanWriteoff,
  LOAN_STATUS, statusMeta
} from '../../../api/expense'

const meta = s => statusMeta(s, LOAN_STATUS)
const employees = ref([])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ userId: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await loanPage({
      current: query.current, size: query.size,
      userId: query.userId || undefined, status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.userId = null; query.status = null; query.kw = ''; onSearch() }

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ userId: null, userName: null, amount: null, borrowDate: null, dueDate: null })
const onUserChange = id => { form.userName = employees.value.find(u => u.userId === id)?.name || null }
const openForm = async () => {
  Object.assign(form, { userId: query.userId, userName: null, amount: null, borrowDate: fmtDate(new Date()), dueDate: null })
  formVisible.value = true
  if (!employees.value.length) employees.value = await fetchEmployeeOptions()
}
const onSave = async () => {
  if (!form.userId) return ElMessage.warning('请选择借款人')
  if (!form.amount || Number(form.amount) <= 0) return ElMessage.warning('请填写借款金额')
  saving.value = true
  try {
    await loanCreate({
      userId: form.userId, userName: form.userName, amount: form.amount,
      borrowDate: form.borrowDate || undefined, dueDate: form.dueDate || undefined
    })
    ElMessage.success('借款已登记并发起审批')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

const writeoffVisible = ref(false)
const writeoff = reactive({ row: null, amount: null, saving: false })
const openWriteoff = row => {
  writeoff.row = row
  writeoff.amount = row.unwriteoffBalance || null
  writeoffVisible.value = true
}
const onWriteoff = async () => {
  writeoff.saving = true
  try {
    await loanWriteoff(writeoff.row.id, writeoff.amount)
    ElMessage.success('冲销成功')
    writeoffVisible.value = false
    loadList()
  } finally {
    writeoff.saving = false
  }
}

onMounted(async () => {
  employees.value = await fetchEmployeeOptions()
  loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.rule-alert { margin-bottom: 12px; }
.hint { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
