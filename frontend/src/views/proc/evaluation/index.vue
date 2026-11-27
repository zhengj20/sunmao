<template>
  <div class="page-container">
    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="标的名称/专家" clearable style="width: 170px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="招采立项">
            <el-select v-model="query.procProjectId" placeholder="全部立项" clearable filterable style="width: 210px">
              <el-option v-for="p in procProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="评分中" value="SCORING" />
              <el-option label="报告已出" value="REPORTED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
        <div>
          <el-button type="primary" icon="Plus" @click="openForm()">新建评标</el-button>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="招采立项(标的)" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ row.procProjectTitle || projLabel(row.procProjectId) }}</template>
        </el-table-column>
        <el-table-column label="评标办法" width="105" align="center">
          <template #default="{ row }">{{ evalMethodLabel(row.method) }}</template>
        </el-table-column>
        <el-table-column label="技术/商务权重" width="115" align="center">
          <template #default="{ row }">{{ row.techWeight ?? '-' }}% / {{ row.bizWeight ?? '-' }}%</template>
        </el-table-column>
        <el-table-column prop="expertNames" label="评标专家" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status)[1]" size="small">{{ statusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conclusion" label="评标结论" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'SCORING'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'SCORING'" link type="warning" size="small" @click="openScoring(row)">评分明细</el-button>
            <el-button v-if="row.status === 'SCORING'" link type="success" size="small" @click="openReport(row)">生成报告</el-button>
            <el-button v-if="row.status === 'SCORING'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="form.visible" :title="form.id ? '编辑评标' : '新建评标'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form.data" :rules="rules" label-width="100px">
        <el-form-item label="招采立项" prop="procProjectId">
          <el-select v-model="form.data.procProjectId" placeholder="请选择已开标的立项" filterable style="width: 100%">
            <el-option v-for="p in addableProjects" :key="p.id" :label="`${p.projectCode} ${p.title}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="评标办法" prop="method">
              <el-select v-model="form.data.method" style="width: 100%">
                <el-option v-for="m in EVAL_METHODS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技术权重%">
              <el-input-number v-model="form.data.techWeight" :min="0" :max="100" :precision="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商务权重%">
          <el-input-number :model-value="100 - (Number(form.data.techWeight) || 0)" disabled :controls="false" style="width: 180px" />
          <span style="margin-left: 8px; color: #6b7280">= 100 - 技术权重</span>
        </el-form-item>
        <el-form-item label="评标专家" prop="expertNames">
          <el-input v-model="form.data.expertNames" placeholder="多个专家用 / 分隔，如：钱工/孙工/周工" maxlength="200" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="专家抽取遵循回避规则：与投标供应商存在关联的专家自动回避" />
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评分明细（抽屉内可编辑子表） -->
    <el-drawer v-model="scoring.visible" title="评委评分明细" size="880px">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        :title="`评标办法：${evalMethodLabel(scoring.data.method)}｜技术权重 ${scoring.data.techWeight ?? '-'}% / 商务权重 ${100 - (Number(scoring.data.techWeight) || 0)}%`" />
      <div class="block-title" style="margin-top: 4px">
        评分记录
        <el-button type="primary" size="small" icon="Plus" style="margin-left: 10px" @click="addScoreRow">添加评分</el-button>
      </div>
      <el-table :data="scoring.details" border size="small" :max-height="300">
        <el-table-column type="index" label="#" width="45" align="center" />
        <el-table-column label="评委专家" width="130">
          <template #default="{ row }">
            <el-select v-model="row.expertName" size="small" placeholder="选择专家">
              <el-option v-for="e in experts" :key="e" :label="e" :value="e" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="投标供应商" min-width="170">
          <template #default="{ row }">
            <el-select v-model="row.bidId" size="small" filterable placeholder="选择回标" @change="bidId => onScoreBidChange(row, bidId)">
              <el-option v-for="b in scoringBids" :key="b.id" :label="b.supplierName" :value="b.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="技术分" width="110" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.techScore" :min="0" :max="100" :precision="1" :controls="false" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="商务分" width="110" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.bizScore" :min="0" :max="100" :precision="1" :controls="false" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="总分(加权)" width="95" align="right" class="num">
          <template #default="{ row }"><b>{{ weightedTotal(row) }}</b></template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="scoring.details.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin: 12px 0 4px; display: flex; gap: 10px">
        <el-button type="primary" :loading="scoring.saving" @click="onSaveDetails">保存全部评分</el-button>
      </div>

      <div class="block-title">汇总排名（按供应商平均总分）</div>
      <el-table :data="summaryRows" border size="small">
        <el-table-column type="index" label="排名" width="60" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="平均总分" width="110" align="right" class="num">
          <template #default="{ row }">{{ row.avgTotal }}</template>
        </el-table-column>
        <el-table-column label="评委数" width="80" align="center">
          <template #default="{ row }">{{ row.count }}</template>
        </el-table-column>
        <el-table-column label="商务报价(元)" width="135" align="right" class="num">
          <template #default="{ row }">{{ money(row.priceAmount) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 生成评标报告 -->
    <el-dialog v-model="report.visible" title="生成评标报告" width="560px" destroy-on-close>
      <el-alert type="warning" :closable="false" title="生成报告后评标流程定稿，不可再修改评分" style="margin-bottom: 12px" />
      <el-form ref="reportRef" :model="report.data" label-width="90px">
        <el-form-item label="评标结论">
          <el-input v-model="report.data.conclusion" type="textarea" :rows="4" placeholder="如：综合评分前三：之江建设、钱江建工、甬城市政" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="report.visible = false">取消</el-button>
        <el-button type="primary" :loading="report.saving" @click="onReportSave">生成报告</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="评标详情" size="700px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="招采立项(标的)" :span="2">{{ detail.data.procProjectTitle || projLabel(detail.data.procProjectId) }}</el-descriptions-item>
        <el-descriptions-item label="评标办法">{{ evalMethodLabel(detail.data.method) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.data.status)[1]" size="small">{{ statusMeta(detail.data.status)[0] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="技术权重">{{ detail.data.techWeight ?? '-' }}%</el-descriptions-item>
        <el-descriptions-item label="商务权重">{{ detail.data.bizWeight ?? '-' }}%</el-descriptions-item>
        <el-descriptions-item label="评标专家" :span="2">{{ detail.data.expertNames || '-' }}</el-descriptions-item>
        <el-descriptions-item label="评标结论" :span="2">{{ detail.data.conclusion || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">评分明细（{{ detail.details.length }} 条，留痕）</div>
      <el-table :data="detail.details" border size="small">
        <el-table-column prop="expertName" label="评委专家" width="100" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="techScore" label="技术分" width="80" align="right" class="num" />
        <el-table-column prop="bizScore" label="商务分" width="80" align="right" class="num" />
        <el-table-column prop="totalScore" label="总分" width="80" align="right" class="num" />
        <el-table-column prop="rankNo" label="名次" width="60" align="center" />
      </el-table>

      <div class="block-title">汇总排名</div>
      <el-table :data="detailSummary" border size="small">
        <el-table-column type="index" label="排名" width="60" align="center" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column label="平均总分" width="110" align="right" class="num">
          <template #default="{ row }">{{ row.avgTotal }}</template>
        </el-table-column>
        <el-table-column label="评委数" width="80" align="center">
          <template #default="{ row }">{{ row.count }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money } from '../../../utils/format'
import {
  evalPage, evalDetail, evalCreate, evalUpdate, evalSaveDetails, evalReport, evalDelete,
  bidPage, projPage, statusMeta, EVAL_METHODS, evalMethodLabel
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20, kw: '', procProjectId: null, status: '' })

const procProjects = ref([])
const projLabel = id => {
  const p = procProjects.value.find(p => p.id === id)
  return p ? `${p.projectCode} ${p.title}` : (id ? '#' + id : '-')
}
/** 可评标的立项：发标中/评标中 */
const addableProjects = computed(() => procProjects.value.filter(p => ['TENDERING', 'EVALUATING', 'APPROVED'].includes(p.status)))

const load = async () => {
  loading.value = true
  try {
    const page = await evalPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const reset = () => {
  query.current = 1; query.kw = ''; query.procProjectId = null; query.status = ''
  load()
}

// ============ 表单 ============
const formRef = ref()
const form = reactive({ visible: false, saving: false, id: null, data: {} })
const rules = {
  procProjectId: [{ required: true, message: '请选择招采立项', trigger: 'change' }],
  method: [{ required: true, message: '请选择评标办法', trigger: 'change' }],
  expertNames: [{ required: true, message: '请输入评标专家', trigger: 'blur' }]
}
const openForm = row => {
  form.id = row?.id || null
  form.data = {
    procProjectId: row?.procProjectId ?? null, method: row?.method || 'COMPREHENSIVE',
    techWeight: row?.techWeight ?? 60, expertNames: row?.expertNames || ''
  }
  form.visible = true
}
const onSave = async () => {
  await formRef.value.validate()
  form.saving = true
  try {
    const payload = { ...form.data, bizWeight: 100 - (Number(form.data.techWeight) || 0) }
    if (form.id) await evalUpdate({ id: form.id, ...payload })
    else await evalCreate(payload)
    ElMessage.success('保存成功')
    form.visible = false
    load()
  } finally {
    form.saving = false
  }
}
const onDelete = async row => {
  await ElMessageBox.confirm('确认删除该评标记录？', '删除', { type: 'warning' })
  await evalDelete(row.id)
  ElMessage.success('已删除')
  load()
}

// ============ 评分明细 ============
const scoring = reactive({ visible: false, saving: false, data: {}, details: [] })
const scoringBids = ref([])
const experts = computed(() => (scoring.data.expertNames || '').split(/[\/,，、]/).map(s => s.trim()).filter(Boolean))
const weightedTotal = row => {
  if (row.techScore === null || row.techScore === undefined || row.bizScore === null || row.bizScore === undefined) return '-'
  const tw = Number(scoring.data.techWeight) || 0
  const bw = Number(scoring.data.bizWeight) || (100 - tw)
  return (row.techScore * tw / 100 + row.bizScore * bw / 100).toFixed(2)
}
const buildSummary = details => {
  const map = {}
  details.forEach(d => {
    if (!d.supplierName) return
    const key = d.supplierName
    map[key] = map[key] || { supplierName: key, total: 0, count: 0 }
    if (d.totalScore !== null && d.totalScore !== undefined) {
      map[key].total += Number(d.totalScore) || 0
      map[key].count += 1
    }
  })
  const rows = Object.values(map).map(r => ({ ...r, avgTotal: r.count ? (r.total / r.count).toFixed(2) : '-' }))
  rows.sort((a, b) => (Number(b.avgTotal) || 0) - (Number(a.avgTotal) || 0))
  return rows
}
const summaryRows = computed(() => {
  const rows = scoring.details.map(d => ({ ...d, totalScore: Number(weightedTotal(d)) || d.totalScore }))
  const summary = buildSummary(rows)
  return summary.map(s => {
    const bid = scoringBids.value.find(b => b.supplierName === s.supplierName)
    return { ...s, priceAmount: bid?.priceAmount }
  })
})
const openScoring = async row => {
  scoring.data = await evalDetail(row.id) || row
  scoring.details = (scoring.data.details || []).map(d => ({
    id: d.id, bidId: d.bidId, supplierName: d.supplierName, expertName: d.expertName,
    techScore: d.techScore, bizScore: d.bizScore, totalScore: d.totalScore, rankNo: d.rankNo
  }))
  scoring.visible = true
  scoringBids.value = []
  const p = await bidPage({ current: 1, size: 100, procProjectId: scoring.data.procProjectId }).catch(() => null)
  scoringBids.value = p?.records || []
  if (!scoringBids.value.length) ElMessage.warning('该立项暂无回标记录，请先在回标管理登记')
}
const addScoreRow = () => {
  scoring.details.push({ bidId: null, supplierName: '', expertName: experts.value[0] || '', techScore: undefined, bizScore: undefined })
}
const onScoreBidChange = (row, bidId) => {
  const b = scoringBids.value.find(x => x.id === bidId)
  row.supplierName = b?.supplierName || ''
}
const onSaveDetails = async () => {
  if (!scoring.details.length) return ElMessage.warning('请至少添加一条评分')
  for (const d of scoring.details) {
    if (!d.expertName) return ElMessage.warning('评分行存在未选择专家')
    if (!d.bidId || !d.supplierName) return ElMessage.warning('评分行存在未选择回标供应商')
    if (d.techScore === undefined || d.techScore === null || d.bizScore === undefined || d.bizScore === null) {
      return ElMessage.warning('评分行存在未打分项')
    }
  }
  // 总分加权 + 按专家分组排名（留痕）
  const rows = scoring.details.map(d => ({
    ...d, totalScore: Number(weightedTotal(d))
  }))
  ;[...new Set(rows.map(r => r.expertName))].forEach(expert => {
    rows.filter(r => r.expertName === expert).sort((a, b) => b.totalScore - a.totalScore)
      .forEach((r, i) => { r.rankNo = i + 1 })
  })
  scoring.saving = true
  try {
    await evalSaveDetails(scoring.data.id, rows.map(({ id, ...rest }) => rest))
    ElMessage.success('评分已保存（留痕）')
    scoring.details = rows.map(r => ({ ...r }))
    load()
  } finally {
    scoring.saving = false
  }
}

// ============ 生成报告 ============
const reportRef = ref()
const report = reactive({ visible: false, saving: false, id: null, data: {} })
const openReport = row => {
  report.id = row.id
  report.data = { conclusion: row.conclusion || '' }
  report.visible = true
}
const onReportSave = async () => {
  if (!report.data.conclusion) return ElMessage.warning('请填写评标结论')
  report.saving = true
  try {
    await evalReport(report.id, report.data)
    ElMessage.success('评标报告已生成')
    report.visible = false
    load()
  } finally {
    report.saving = false
  }
}

// ============ 详情 ============
const detail = reactive({ visible: false, data: {}, details: [] })
const detailSummary = computed(() => buildSummary(detail.details))
const openDetail = async row => {
  const d = await evalDetail(row.id)
  detail.data = d || row
  detail.details = d?.details || []
  detail.visible = true
}

onMounted(async () => {
  load()
  projPage({ current: 1, size: 200 }).then(p => { procProjects.value = p?.records || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
