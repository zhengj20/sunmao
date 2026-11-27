<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="供应商名称" clearable style="width: 170px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="招采立项">
            <el-select v-model="query.procProjectId" placeholder="全部立项" clearable filterable style="width: 210px">
              <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="回标状态">
            <el-select v-model="query.bidStatus" placeholder="全部" clearable style="width: 110px">
              <el-option label="已回标" value="SUBMITTED" />
              <el-option label="已开标" value="OPENED" />
              <el-option label="中标" value="WINNER" />
              <el-option label="未中标" value="LOSER" />
              <el-option label="废标" value="INVALID" />
            </el-select>
          </el-form-item>
          <el-form-item label="符合性">
            <el-select v-model="query.compliance" placeholder="全部" clearable style="width: 100px">
              <el-option label="符合" value="PASSED" />
              <el-option label="不符合" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">回标登记</el-button>
          <el-button type="success" icon="DataAnalysis" @click="openCompare">回标一览对比</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="招采立项(标的)" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.procProjectTitle || projLabel(row.procProjectId) }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="190" show-overflow-tooltip />
        <el-table-column label="回标时间" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ fmtTime(row.bidTime) }}</template>
        </el-table-column>
        <el-table-column label="商务报价(元)" width="135" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.priceAmount) }}</template>
        </el-table-column>
        <el-table-column label="技术得分" width="85" align="right" class="num">
          <template #default="{ row }">{{ row.techScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="符合性" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.complianceResult" :type="statusMeta(row.complianceResult)[1]" size="small">{{ statusMeta(row.complianceResult)[0] }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="bidIp" label="回标IP" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'ip-warn': sameIpSet.has(row.bidIp) }">{{ row.bidIp || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不平衡报价" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.unbalancedFlag === 1" type="danger" size="small">AI标记</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="回标状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.bidStatus)[1]" size="small">{{ statusMeta(row.bidStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="['SUBMITTED', 'OPENED'].includes(row.bidStatus)" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="['SUBMITTED', 'OPENED'].includes(row.bidStatus)" link type="danger" size="small" @click="onInvalid(row)">废标</el-button>
            <el-button v-if="row.bidStatus === 'INVALID'" link type="success" size="small" @click="onRestore(row)">恢复</el-button>
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

    <!-- 回标登记 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑回标' : '回标登记'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-form-item label="招采立项" prop="procProjectId">
          <el-select v-model="form.data.procProjectId" placeholder="请选择立项" filterable style="width: 100%" @change="onFormProjectChange">
            <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联发标">
          <el-select v-model="form.data.tenderId" placeholder="选择标段（可空）" clearable style="width: 100%">
            <el-option v-for="t in formTenders" :key="t.id" :label="`${t.tenderCode}（${t.docVersion || '-'}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.data.supplierId" placeholder="选择供应商" filterable style="width: 100%">
                <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回标时间" prop="bidTime">
              <el-date-picker v-model="form.data.bidTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="回标时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商务报价" prop="priceAmount">
              <el-input-number v-model="form.data.priceAmount" :min="0" :precision="2" :controls="false" placeholder="元" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技术得分">
              <el-input-number v-model="form.data.techScore" :min="0" :max="100" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="符合性">
              <el-radio-group v-model="form.data.complianceResult">
                <el-radio value="PASSED">符合</el-radio>
                <el-radio value="FAILED">不符合</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回标IP">
              <el-input v-model="form.data.bidIp" placeholder="用于围串标预警" maxlength="45" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="偏差说明">
          <el-input v-model="form.data.deviationNotes" type="textarea" :rows="2" placeholder="清标偏差分析（漏项/偏离条款等）" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 回标一览对比 -->
    <el-drawer v-model="compare.visible" title="回标报价一览对比" size="860px">
      <div style="display: flex; gap: 10px; margin-bottom: 12px">
        <el-select v-model="compare.procProjectId" placeholder="选择招采立项" filterable style="width: 340px" @change="loadCompare">
          <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
        </el-select>
        <el-tag v-if="compare.rows.length" type="info" size="large">共 {{ compare.rows.length }} 家回标</el-tag>
      </div>
      <el-table :data="compareRows" v-loading="compare.loading" border size="small">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip fixed="left" />
        <el-table-column label="商务报价(元)" width="140" align="right" class="num">
          <template #default="{ row }">
            <span :style="row.priceAmount === compareMin ? 'color:#67c23a;font-weight:700' : ''">{{ money(row.priceAmount) }}</span>
            <el-tag v-if="row.priceAmount === compareMin" type="success" size="small" style="margin-left:4px">最低价</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="技术得分" width="85" align="right" class="num">
          <template #default="{ row }">{{ row.techScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="符合性" width="75" align="center">
          <template #default="{ row }">{{ row.complianceResult ? statusMeta(row.complianceResult)[0] : '-' }}</template>
        </el-table-column>
        <el-table-column label="回标IP" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="compareSameIp.has(row.bidIp)" content="同IP回标预警：存在围串标嫌疑" placement="top">
              <el-tag type="danger" size="small" effect="dark">{{ row.bidIp || '-' }}</el-tag>
            </el-tooltip>
            <span v-else>{{ row.bidIp || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不平衡报价" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.unbalancedFlag === 1" type="danger" size="small">AI标记</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="回标状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.bidStatus)[1]" size="small">{{ statusMeta(row.bidStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviationNotes" label="偏差说明" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-descriptions v-if="compare.rows.length" :column="3" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="最低报价(元)"><span class="num">{{ money(compareMin) }}</span></el-descriptions-item>
        <el-descriptions-item label="平均报价(元)"><span class="num">{{ money(compareAvg) }}</span></el-descriptions-item>
        <el-descriptions-item label="围串标预警">
          <el-tag :type="compareSameIp.size ? 'danger' : 'success'" size="small">
            {{ compareSameIp.size ? `${compareSameIp.size} 个IP多家回标` : '未发现同IP回标' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="compareSameIp.size"
        type="error"
        :closable="false"
        style="margin-top: 10px"
        :title="`围串标预警：以下IP被多家供应商用于回标，请核查关联关系 —— ${[...compareSameIp].join('、')}`"
      />
    </el-drawer>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="回标详情" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="招采立项(标的)" :span="2">{{ detail.data.procProjectTitle || projLabel(detail.data.procProjectId) }}</el-descriptions-item>
        <el-descriptions-item label="供应商" :span="2">{{ detail.data.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="回标时间">{{ fmtTime(detail.data.bidTime) }}</el-descriptions-item>
        <el-descriptions-item label="回标状态">
          <el-tag :type="statusMeta(detail.data.bidStatus)[1]" size="small">{{ statusMeta(detail.data.bidStatus)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商务报价(元)"><span class="num">{{ money(detail.data.priceAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="技术得分"><span class="num">{{ detail.data.techScore ?? '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="符合性">
          <el-tag v-if="detail.data.complianceResult" :type="statusMeta(detail.data.complianceResult)[1]" size="small">{{ statusMeta(detail.data.complianceResult)[0] }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="回标IP">
          <span :class="{ 'ip-warn': detailSameIp }">{{ detail.data.bidIp || '-' }}</span>
          <el-tag v-if="detailSameIp" type="danger" size="small" style="margin-left: 4px">同IP预警</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="不平衡报价">{{ detail.data.unbalancedFlag === 1 ? 'AI已标记' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="偏差说明" :span="2">{{ detail.data.deviationNotes || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">同标段回标一览（{{ detailBids.length }} 家）</div>
      <el-table :data="detailBids" border size="small">
        <el-table-column prop="supplierName" label="供应商" min-width="170" show-overflow-tooltip />
        <el-table-column label="商务报价(元)" width="125" align="right" class="num">
          <template #default="{ row }">{{ money(row.priceAmount) }}</template>
        </el-table-column>
        <el-table-column label="符合性" width="70" align="center">
          <template #default="{ row }">{{ row.complianceResult ? statusMeta(row.complianceResult)[0] : '-' }}</template>
        </el-table-column>
        <el-table-column label="回标状态" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.bidStatus)[1]" size="small">{{ statusMeta(row.bidStatus)[0] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtTime } from '../../../utils/format'
import {
  bidPage, bidDetail, bidCreate, bidUpdate, bidInvalid, bidRestore,
  tenderPage, projPage, fetchSupplierOptions, statusMeta
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', procProjectId: null, bidStatus: '', compliance: '' })

const procProjects = ref([])
const suppliers = ref([])
const projLabel = id => {
  const p = procProjects.value.find(p => p.id === id)
  return p ? `${p.projectCode} ${p.title}` : (id ? '#' + id : '-')
}

/** 当前列表内同IP集合（多家共用同一IP） */
const sameIpSet = computed(() => buildSameIp(list.value))
const buildSameIp = rows => {
  const cnt = {}
  rows.forEach(r => { if (r.bidIp) cnt[r.bidIp] = (cnt[r.bidIp] || 0) + 1 })
  return new Set(Object.keys(cnt).filter(ip => cnt[ip] > 1))
}

const load = async () => {
  loading.value = true
  try {
    const page = await bidPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.procProjectId = null; query.bidStatus = ''; query.compliance = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const formTenders = ref([])
const rules = {
  procProjectId: [{ required: true, message: '请选择招采立项', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  bidTime: [{ required: true, message: '请选择回标时间', trigger: 'change' }],
  priceAmount: [{ required: true, message: '请输入商务报价', trigger: 'blur' }]
}
const openForm = async row => {
  form.id = row?.id || null
  form.data = {
    procProjectId: row?.procProjectId ?? null, tenderId: row?.tenderId ?? null,
    supplierId: row?.supplierId ?? null, supplierName: row?.supplierName || '',
    bidTime: row?.bidTime || null, priceAmount: row?.priceAmount ?? undefined,
    techScore: row?.techScore ?? undefined, complianceResult: row?.complianceResult || 'PASSED',
    bidIp: row?.bidIp || '', deviationNotes: row?.deviationNotes || ''
  }
  formTenders.value = []
  if (form.data.procProjectId) {
    const p = await tenderPage({ current: 1, size: 50, procProjectId: form.data.procProjectId }).catch(() => null)
    formTenders.value = p?.records || []
  }
  form.visible = true
}
const onFormProjectChange = async () => {
  form.data.tenderId = null
  formTenders.value = []
  if (!form.data.procProjectId) return
  const p = await tenderPage({ current: 1, size: 50, procProjectId: form.data.procProjectId }).catch(() => null)
  formTenders.value = p?.records || []
}
const onSave = async () => {
  await formRef.value.validate()
  const s = suppliers.value.find(x => x.id === form.data.supplierId)
  form.saving = true
  try {
    const payload = { ...form.data, supplierName: s?.name || form.data.supplierName || '' }
    if (form.id) await bidUpdate({ id: form.id, ...payload })
    else await bidCreate(payload)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onInvalid = async row => {
  await ElMessageBox.confirm(`确认对 ${row.supplierName} 做废标处理？`, '废标', { type: 'warning' })
  await bidInvalid(row.id)
  ElMessage.success('已废标')
  load()
}
const onRestore = async row => {
  await ElMessageBox.confirm(`确认恢复 ${row.supplierName} 的回标？`, '恢复', { type: 'warning' })
  await bidRestore(row.id)
  ElMessage.success('已恢复')
  load()
}

// ============ 一览对比 ============
const compare = reactive({ visible: false, loading: false, procProjectId: null, rows: [] })
const compareRows = computed(() => [...compare.rows].sort((a, b) => (a.priceAmount || 0) - (b.priceAmount || 0)))
const compareMin = computed(() => (compare.rows.length ? Math.min(...compare.rows.map(r => Number(r.priceAmount) || Infinity)) : null))
const compareAvg = computed(() => {
  const arr = compare.rows.map(r => Number(r.priceAmount)).filter(v => !Number.isNaN(v) && v > 0)
  return arr.length ? arr.reduce((s, v) => s + v, 0) / arr.length : null
})
const compareSameIp = computed(() => buildSameIp(compare.rows))
const openCompare = () => {
  compare.procProjectId = query.procProjectId || null
  compare.rows = []
  compare.visible = true
  if (compare.procProjectId) loadCompare()
}
const loadCompare = async () => {
  if (!compare.procProjectId) { compare.rows = []; return }
  compare.loading = true
  try {
    const p = await bidPage({ current: 1, size: 100, procProjectId: compare.procProjectId })
    compare.rows = p?.records || []
  } finally {
    compare.loading = false
  }
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {} })
const detailBids = ref([])
const detailSameIp = computed(() => detailBids.value.length > 1 && buildSameIp(detailBids.value).has(detail.data.bidIp))
const openDetail = async row => {
  detail.data = await bidDetail(row.id) || row
  detail.visible = true
  detailBids.value = []
  bidPage({ current: 1, size: 100, procProjectId: row.procProjectId }).then(p => { detailBids.value = p?.records || [] }).catch(() => {})
}

onMounted(async () => {
  load()
  projPage({ current: 1, size: 200 }).then(p => { procProjects.value = p?.records || [] }).catch(() => {})
  fetchSupplierOptions().then(rs => { suppliers.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
.ip-warn { color: #f56c6c; font-weight: 700; }
</style>
