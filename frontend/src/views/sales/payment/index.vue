<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 200px" @change="loadOverdue">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-tabs v-model="tab" class="inline-tabs">
            <el-tab-pane label="应收计划" name="plan" />
            <el-tab-pane label="实收记录" name="receipt" />
            <el-tab-pane label="来账对账" name="incoming" />
            <el-tab-pane label="逾期清单" name="overdue" />
          </el-tabs>
        </div>
        <el-button @click="reload">刷新</el-button>
      </div>
    </el-card>

    <el-row :gutter="12" class="stat-row">
      <el-col :span="8">
        <el-card shadow="never" class="stat-card"><div class="label">应收总额(元)</div><div class="value">{{ money(stats.planTotal) }}</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card"><div class="label">已收总额(元)</div><div class="value" style="color:#67c23a">{{ money(stats.receivedTotal) }}</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card"><div class="label">回款率</div><div class="value" style="color:#185fa5">{{ stats.rate }}%</div></el-card>
      </el-col>
    </el-row>

    <!-- 应收计划 -->
    <el-card v-show="tab === 'plan'" shadow="never" class="table-card">
      <div class="filter-line">
        <el-select v-model="planQuery.status" placeholder="状态" clearable style="width: 130px" @change="onSearchPlan">
          <el-option v-for="v in Object.keys(PLAN_STATUS)" :key="v" :label="PLAN_STATUS[v][0]" :value="v" />
        </el-select>
        <el-button type="primary" @click="onSearchPlan">查询</el-button>
      </div>
      <el-table v-loading="planLoading" :data="planRows" border stripe>
        <el-table-column prop="contractNo" label="合同号" width="150" show-overflow-tooltip />
        <el-table-column prop="seq" label="期次" width="70" align="center" />
        <el-table-column prop="termDesc" label="期次说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="应收额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.planAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已收(元)" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.receivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额(元)" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(remainOf(row)) }}</span></template>
        </el-table-column>
        <el-table-column label="到期日" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="planMeta(row.status)[1]" size="small">{{ planMeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'RECEIVED'" link type="primary" size="small" @click="openReceive(row)">实收核销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="planQuery.current"
          v-model:page-size="planQuery.size"
          :total="planTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadPlan"
          @size-change="onSearchPlan"
        />
      </div>
    </el-card>

    <!-- 实收记录 -->
    <el-card v-show="tab === 'receipt'" shadow="never" class="table-card">
      <el-table v-loading="receiptLoading" :data="receiptRows" border stripe>
        <el-table-column prop="receiptNo" label="实收单号" width="150" show-overflow-tooltip />
        <el-table-column prop="contractNo" label="合同号" width="150" show-overflow-tooltip />
        <el-table-column label="实收金额(元)" width="150" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.receivedAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="收款方式" width="110" align="center">
          <template #default="{ row }">{{ payWayLabel(row.payWay) }}</template>
        </el-table-column>
        <el-table-column prop="ticketNo" label="票据号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ticketNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="实收时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.receivedAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="receiptQuery.current"
          v-model:page-size="receiptQuery.size"
          :total="receiptTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadReceipt"
          @size-change="onSearchReceipt"
        />
      </div>
    </el-card>

    <!-- 来账对账（银企直联/手机短信） -->
    <el-card v-show="tab === 'incoming'" shadow="never" class="table-card">
      <div class="filter-line">
        <el-select v-model="incQuery.matchStatus" placeholder="匹配状态" clearable style="width: 140px" @change="onSearchIncoming">
          <el-option v-for="v in Object.keys(INCOMING_STATUS)" :key="v" :label="INCOMING_STATUS[v][0]" :value="v" />
        </el-select>
        <el-select v-model="incQuery.source" placeholder="来源" clearable style="width: 140px" @change="onSearchIncoming">
          <el-option v-for="v in Object.keys(INCOMING_SOURCE)" :key="v" :label="INCOMING_SOURCE[v][0]" :value="v" />
        </el-select>
        <el-button type="primary" @click="onSearchIncoming">查询</el-button>
        <div class="inc-actions">
          <el-button type="success" plain :loading="incSim.loading" @click="onSimulate('BANK')">模拟银企直联来账</el-button>
          <el-button type="primary" plain :loading="incSim.loading" @click="onSimulate('SMS')">模拟短信收款</el-button>
          <el-button type="primary" plain @click="openRegister">登记来账</el-button>
        </div>
      </div>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">待匹配(笔/元)</div><div class="value" style="color:#e6a23c">{{ incStats.pendingCount || 0 }} / {{ money(incStats.pendingAmt) }}</div></el-card></el-col>
        <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">已匹配(笔/元)</div><div class="value" style="color:#67c23a">{{ incStats.matchedCount || 0 }} / {{ money(incStats.matchedAmt) }}</div></el-card></el-col>
        <el-col :span="8"><el-card shadow="never" class="stat-card"><div class="label">说明</div><div class="value" style="font-size:13px;color:#909399;font-weight:400;line-height:1.4">银企直联/短信来账可智能或手动匹配到合同·房间，匹配后自动核销生成实收。</div></el-card></el-col>
      </el-row>

      <el-table v-loading="incLoading" :data="incRows" border stripe>
        <el-table-column prop="incomingNo" label="来账单号" width="150" show-overflow-tooltip />
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }"><el-tag :type="statusMeta(row.source, INCOMING_SOURCE)[1]" size="small">{{ statusMeta(row.source, INCOMING_SOURCE)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="payerName" label="付款人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.payerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="来账金额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="bankSerial" label="银行/短信流水号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bankSerial || '-' }}</template>
        </el-table-column>
        <el-table-column label="匹配合同" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="匹配房号" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.roomCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag :type="statusMeta(row.matchStatus, INCOMING_STATUS)[1]" size="small">{{ statusMeta(row.matchStatus, INCOMING_STATUS)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="到账时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.receivedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.matchStatus === 'PENDING'">
              <el-button link type="primary" size="small" @click="openMatch(row)">智能匹配</el-button>
              <el-button link type="info" size="small" @click="openReject(row)">排除</el-button>
            </template>
            <el-button v-else-if="row.matchStatus === 'MATCHED'" link type="success" size="small" disabled>已核销</el-button>
            <el-button v-else link type="info" size="small" disabled>已排除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="incQuery.current"
          v-model:page-size="incQuery.size"
          :total="incTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadIncoming"
          @size-change="onSearchIncoming"
        />
      </div>
    </el-card>

    <!-- 逾期清单 -->
    <el-card v-show="tab === 'overdue'" shadow="never" class="table-card">
      <el-table v-loading="overdueLoading" :data="overdueRows" border stripe>
        <el-table-column prop="contractNo" label="合同号" width="150" show-overflow-tooltip />
        <el-table-column prop="seq" label="期次" width="70" align="center" />
        <el-table-column prop="termDesc" label="期次说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="应收额(元)" width="140" align="right">
          <template #default="{ row }"><span class="num">{{ money(row.planAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="余额(元)" width="130" align="right">
          <template #default="{ row }"><span class="num">{{ money(remainOf(row)) }}</span></template>
        </el-table-column>
        <el-table-column label="到期日" width="110" align="center">
          <template #default="{ row }">{{ fmtDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }"><el-tag type="danger" size="small">{{ planMeta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openReceive(row)">实收核销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 实收核销 -->
    <el-dialog v-model="receive.visible" title="实收登记" width="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="合同号">{{ receive.plan.contractNo || ('#' + receive.plan.contractId) }}</el-descriptions-item>
        <el-descriptions-item label="期次">{{ receive.plan.seq }} {{ receive.plan.termDesc || '' }}</el-descriptions-item>
        <el-descriptions-item label="应收额"><span class="num">{{ money(receive.plan.planAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="本期待收余额"><span class="num">{{ money(remainOf(receive.plan)) }}</span></el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="实收金额(元)" required>
          <el-input-number v-model="receive.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式" required>
          <el-select v-model="receive.method" style="width: 100%">
            <el-option v-for="m in PAY_WAYS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="票据号">
          <el-input v-model="receive.ticketNo" maxlength="60" placeholder="支票/转账凭证号" />
        </el-form-item>
        <el-form-item label="实收日期">
          <el-date-picker v-model="receive.receivedDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receive.visible = false">取消</el-button>
        <el-button type="primary" :loading="receive.saving" @click="onReceive">确认实收</el-button>
      </template>
    </el-dialog>

    <!-- 来账智能匹配 -->
    <el-dialog v-model="match.visible" title="来账匹配" width="640px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="来账金额"><span class="num" style="font-weight:600">{{ money(match.row.amount) }}</span></el-descriptions-item>
        <el-descriptions-item label="付款人">{{ match.row.payerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ match.row.source === 'SMS' ? '手机短信' : '银企直联' }}</el-descriptions-item>
        <el-descriptions-item label="流水号">{{ match.row.bankSerial || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div v-loading="match.loading" style="min-height:60px">
        <div class="suggest-title">智能匹配建议</div>
        <div v-if="!match.loading && !match.candidates.length" class="suggest-empty">未找到付款人/房号匹配的合同，请在下方手动选择合同。</div>
        <el-table v-else :data="match.candidates" border size="small" max-height="240">
          <el-table-column label="" width="50" align="center">
            <template #default="{ row }">
              <el-radio v-model="match.chosen" :value="row.contractId">{{ '' }}</el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="contractNo" label="合同号" min-width="150" show-overflow-tooltip />
          <el-table-column prop="roomCode" label="房号" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.roomCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerName || '-' }}</template>
          </el-table-column>
          <el-table-column label="未收余额(元)" width="130" align="right">
            <template #default="{ row }"><span class="num">{{ money(row.outstanding) }}</span></template>
          </el-table-column>
        </el-table>
        <div class="manual-line">
          <span>手动指定合同：</span>
          <el-select v-model="match.manualContract" filterable placeholder="选择合同" style="width: 260px" @change="onManualContract">
            <el-option v-for="c in contracts" :key="c.id" :label="`${c.contractNo || ('#' + c.id)} ${c.roomCode || ''}`.trim()" :value="c.id" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="match.visible = false">取消</el-button>
        <el-button type="primary" :loading="match.saving" @click="onConfirmMatch">确认匹配并核销</el-button>
      </template>
    </el-dialog>

    <!-- 登记来账 -->
    <el-dialog v-model="reg.visible" title="登记来账（银企直联/短信）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="来源" required>
          <el-radio-group v-model="reg.source">
            <el-radio-button value="BANK">银企直联</el-radio-button>
            <el-radio-button value="SMS">手机短信</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="付款人" required>
          <el-input v-model="reg.payerName" placeholder="客户姓名/手机号（用于匹配）" />
        </el-form-item>
        <el-form-item label="来账金额(元)" required>
          <el-input-number v-model="reg.amount" :controls="false" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="流水号">
          <el-input v-model="reg.bankSerial" maxlength="60" placeholder="银行流水号/短信流水号" />
        </el-form-item>
        <el-form-item label="付款账号">
          <el-input v-model="reg.payerAccount" maxlength="40" />
        </el-form-item>
        <el-form-item label="到账时间">
          <el-date-picker v-model="reg.receivedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reg.visible = false">取消</el-button>
        <el-button type="primary" :loading="reg.saving" @click="onRegister">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { money, fmtDate, fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, planPage, receiptPage, overduePlans, receive as receiveApi,
  contractPage,
  incomingPage, incomingStats, incomingCreate, incomingSimulate, incomingSuggest, incomingMatch, incomingReject,
  PLAN_STATUS, PAY_WAYS, payWayLabel, statusMeta, INCOMING_SOURCE, INCOMING_STATUS
} from '../../../api/sales'

const planMeta = s => statusMeta(s, PLAN_STATUS)
const remainOf = r => (Number(r.planAmount) || 0) - (Number(r.receivedAmount) || 0)

const projects = ref([])
const projectId = ref(null)
const tab = ref('plan')

const stats = reactive({ planTotal: 0, receivedTotal: 0, rate: '0.0' })

// 应收计划
const planLoading = ref(false)
const planRows = ref([])
const planTotal = ref(0)
const planQuery = reactive({ status: null, current: 1, size: 20 })
const loadPlan = async () => {
  planLoading.value = true
  try {
    const page = await planPage({ current: planQuery.current, size: planQuery.size, status: planQuery.status || undefined })
    planRows.value = page?.records || []
    planTotal.value = Number(page?.total) || 0
  } finally {
    planLoading.value = false
  }
}
const onSearchPlan = () => { planQuery.current = 1; loadPlan() }

// 实收记录
const receiptLoading = ref(false)
const receiptRows = ref([])
const receiptTotal = ref(0)
const receiptQuery = reactive({ current: 1, size: 20 })
const loadReceipt = async () => {
  receiptLoading.value = true
  try {
    const page = await receiptPage({ current: receiptQuery.current, size: receiptQuery.size })
    receiptRows.value = page?.records || []
    receiptTotal.value = Number(page?.total) || 0
  } finally {
    receiptLoading.value = false
  }
}
const onSearchReceipt = () => { receiptQuery.current = 1; loadReceipt() }

// 逾期
const overdueLoading = ref(false)
const overdueRows = ref([])
const loadOverdue = async () => {
  overdueLoading.value = true
  try { overdueRows.value = (await overduePlans(projectId.value)) || [] } finally { overdueLoading.value = false }
}

// ==================== 来账对账（银企直联/短信） ====================
const incLoading = ref(false)
const incRows = ref([])
const incTotal = ref(0)
const incQuery = reactive({ matchStatus: null, source: null, current: 1, size: 20 })
const incStats = reactive({ pendingCount: 0, pendingAmt: 0, matchedCount: 0, matchedAmt: 0 })
const incSim = reactive({ loading: false })
const contracts = ref([])
const loadIncoming = async () => {
  incLoading.value = true
  try {
    const page = await incomingPage({ current: incQuery.current, size: incQuery.size, projectId: projectId.value || undefined, matchStatus: incQuery.matchStatus || undefined, source: incQuery.source || undefined })
    incRows.value = page?.records || []
    incTotal.value = Number(page?.total) || 0
  } finally { incLoading.value = false }
}
const loadIncStats = async () => {
  const s = await incomingStats(projectId.value || undefined).catch(() => null)
  if (s) Object.assign(incStats, s)
}
const onSearchIncoming = () => { incQuery.current = 1; loadIncoming() }
const reloadIncoming = () => { loadIncoming(); loadIncStats() }

const loadContracts = async () => {
  const page = await contractPage({ current: 1, size: 200, projectId: projectId.value || undefined }).catch(() => null)
  contracts.value = page?.records || []
}

// 模拟银企直联/短信推送
const onSimulate = async source => {
  if (!projectId.value) return ElMessage.warning('请选择项目')
  incSim.loading = true
  try {
    const n = await incomingSimulate(projectId.value, source, 5)
    ElMessage.success(`已模拟接收 ${n} 笔${source === 'SMS' ? '短信' : '银企直联'}来账`)
    reloadIncoming()
  } finally { incSim.loading = false }
}

// 登记来账
const reg = reactive({ visible: false, saving: false, source: 'BANK', payerName: '', amount: null, bankSerial: '', payerAccount: '', receivedAt: null })
const openRegister = () => { Object.assign(reg, { source: 'BANK', payerName: '', amount: null, bankSerial: '', payerAccount: '', receivedAt: null }); reg.visible = true }
const onRegister = async () => {
  if (!projectId.value) return ElMessage.warning('请选择项目')
  if (!reg.payerName) return ElMessage.warning('请填写付款人')
  if (!reg.amount) return ElMessage.warning('请填写来账金额')
  reg.saving = true
  try {
    await incomingCreate({ projectId: projectId.value, source: reg.source, payerName: reg.payerName, amount: reg.amount, bankSerial: reg.bankSerial || undefined, payerAccount: reg.payerAccount || undefined, receivedAt: reg.receivedAt || undefined })
    reg.visible = false
    ElMessage.success('来账已登记，可智能匹配到合同·房间')
    reloadIncoming()
  } finally { reg.saving = false }
}

// 智能匹配
const match = reactive({ visible: false, loading: false, saving: false, row: null, candidates: [], chosen: null, manualContract: null })
const openMatch = async row => {
  match.row = row
  match.candidates = []
  match.chosen = null
  match.manualContract = null
  match.visible = true
  match.loading = true
  try {
    const r = await incomingSuggest(row.id)
    match.candidates = r?.candidates || []
    match.chosen = (r?.best && r.best.contractId) || null
    if (!contracts.value.length) loadContracts()
  } finally { match.loading = false }
}
const onManualContract = v => { match.chosen = v }
const onConfirmMatch = async () => {
  if (!match.chosen) return ElMessage.warning('请选择要匹配的合同')
  match.saving = true
  try {
    await incomingMatch(match.row.id, match.chosen)
    match.visible = false
    ElMessage.success('匹配成功，已核销并生成实收')
    reload()
  } finally { match.saving = false }
}
const openReject = async row => {
  await ElMessageBox.confirm('确认排除该来账（标记为非本司/无法匹配）？', '排除来账', { confirmButtonText: '排除', cancelButtonText: '取消', type: 'warning' })
  await incomingReject(row.id)
  ElMessage.success('已排除')
  reloadIncoming()
}

// 统计（应收/已收/回款率）
const loadStats = async () => {
  const page = await planPage({ current: 1, size: 1000 }).catch(() => null)
  const all = page?.records || []
  const planTotal = all.reduce((s, r) => s + (Number(r.planAmount) || 0), 0)
  const receivedTotal = all.reduce((s, r) => s + (Number(r.receivedAmount) || 0), 0)
  stats.planTotal = planTotal
  stats.receivedTotal = receivedTotal
  stats.rate = planTotal ? ((receivedTotal / planTotal) * 100).toFixed(1) : '0.0'
}

// 实收核销
const receive = reactive({ visible: false, saving: false, plan: null, amount: null, method: 'TRANSFER', ticketNo: '', receivedDate: null })
const openReceive = row => {
  receive.plan = row
  receive.amount = remainOf(row)
  receive.method = 'TRANSFER'
  receive.ticketNo = ''
  receive.receivedDate = null
  receive.visible = true
}
const onReceive = async () => {
  if (!receive.amount || Number(receive.amount) <= 0) return ElMessage.warning('请填写实收金额')
  if (!receive.method) return ElMessage.warning('请选择收款方式')
  const remain = remainOf(receive.plan)
  if (Number(receive.amount) > remain) return ElMessage.warning(`实收金额超出该期应收余额（余额 ${money(remain)}）`)
  receive.saving = true
  try {
    await receiveApi(receive.plan.contractId, {
      planId: receive.plan.id, amount: receive.amount, method: receive.method,
      ticketNo: receive.ticketNo || undefined, receivedDate: receive.receivedDate || undefined
    })
    ElMessage.success('实收已登记并核销应收')
    receive.visible = false
    reload()
  } finally {
    receive.saving = false
  }
}

const reload = () => {
  if (tab.value === 'plan') { loadPlan(); loadStats() }
  else if (tab.value === 'receipt') loadReceipt()
  else if (tab.value === 'incoming') reloadIncoming()
  else loadOverdue()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions().catch(() => [])
  if (projects.value.length) { projectId.value = projects.value[0].id; loadOverdue(); loadContracts() }
  loadPlan()
  loadStats()
})

// 切换 Tab 时按需加载
watch(tab, t => {
  if (t === 'incoming' && !incRows.value.length) reloadIncoming()
  if (t === 'overdue') loadOverdue()
  if (t === 'receipt' && !receiptRows.value.length) loadReceipt()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.inline-tabs :deep(.el-tabs__item) { font-size: 14px; }
.filter-line { display: flex; gap: 10px; margin-bottom: 12px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.stat-card .value { font-size: 24px; font-weight: 700; color: #1f2937; }
.stat-card .label { font-size: 12px; color: #909399; }
.inc-actions { display: flex; gap: 8px; margin-left: auto; flex-wrap: wrap; }
.suggest-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.suggest-empty { font-size: 13px; color: #909399; padding: 8px 0; }
.manual-line { display: flex; align-items: center; gap: 8px; margin-top: 12px; font-size: 13px; color: #606266; }
.num { font-variant-numeric: tabular-nums; }
</style>
