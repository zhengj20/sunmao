<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="value num">{{ stats.requirementCount ?? '-' }}</div>
          <div class="label">采购需求总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="value num">{{ stats.projectCount ?? '-' }}</div>
          <div class="label">招采立项数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="value num">{{ stats.awardCount ?? '-' }}</div>
          <div class="label">已定标数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="value num">{{ money(stats.awardTotalAmount) }}</div>
          <div class="label">定标总额(元)</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-form inline @submit.prevent="load">
          <el-form-item label="关键词">
            <el-input v-model="query.kw" placeholder="立项编号/标的/定标编号" clearable style="width: 200px" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="query.projectId" placeholder="全部项目" clearable filterable style="width: 160px">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="采购方式">
            <el-select v-model="query.method" placeholder="全部方式" clearable style="width: 130px">
              <el-option v-for="m in PROC_METHODS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 台账列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="projectCode" label="立项编号" width="130" show-overflow-tooltip fixed="left" />
        <el-table-column label="项目" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标的名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="采购方式" width="95" align="center">
          <template #default="{ row }">{{ methodLabel(row.procureMethod) }}</template>
        </el-table-column>
        <el-table-column prop="reqCode" label="需求编号" width="125" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reqCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="planCode" label="计划编号" width="125" show-overflow-tooltip>
          <template #default="{ row }">{{ row.planCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="预算金额(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.budgetAmount) }}</template>
        </el-table-column>
        <el-table-column label="定标价(元)" width="130" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">{{ money(row.awardAmount) }}</template>
        </el-table-column>
        <el-table-column label="节约额(元)" width="125" align="right" class="num" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.budgetAmount && row.awardAmount" :style="savingStyle(row)">{{ money(row.budgetAmount - row.awardAmount) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="winnerSupplierName" label="中标供应商" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.winnerSupplierName || '-' }}</template>
        </el-table-column>
        <el-table-column label="全链路状态" width="220">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMeta(row.reqStatus, { APPROVED: ['需求√', 'success'], MERGED: ['需求√', 'success'] })[1]">
              需求{{ row.reqStatus ? '√' : '-' }}
            </el-tag>
            <el-tag size="small" :type="row.projectStatus && row.projectStatus !== 'DRAFT' ? 'success' : 'info'" style="margin-left: 3px">
              立项{{ row.projectStatus && row.projectStatus !== 'DRAFT' ? '√' : '-' }}
            </el-tag>
            <el-tag size="small" :type="row.awardStatus === 'APPROVED' ? 'success' : 'info'" style="margin-left: 3px">
              定标{{ row.awardStatus === 'APPROVED' ? '√' : '-' }}
            </el-tag>
            <el-tag size="small" :type="row.contractId ? 'success' : 'info'" style="margin-left: 3px">
              合同{{ row.contractId ? '√' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看链路</el-button>
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

    <!-- 全链路详情抽屉 -->
    <el-drawer v-model="detail.visible" title="采购全链路（一标一档）" size="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="立项编号">{{ detail.data.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="采购方式">{{ methodLabel(detail.data.procureMethod) }}</el-descriptions-item>
        <el-descriptions-item label="项目">{{ detail.data.projectName || projectName(detail.data.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="标的名称">{{ detail.data.title }}</el-descriptions-item>
        <el-descriptions-item label="预算金额(元)"><span class="num">{{ money(detail.data.budgetAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="定标价(元)"><span class="num">{{ money(detail.data.awardAmount) }}</span></el-descriptions-item>
        <el-descriptions-item label="中标供应商" :span="2">{{ detail.data.winnerSupplierName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block-title">全流程链路</div>
      <el-steps direction="vertical" :active="chainActive" space="70px">
        <el-step title="采购需求" :status="chainStatus(detail.chain.requirement)" :description="chainDesc('requirement', '需求')" />
        <el-step title="采购计划" :status="chainStatus(detail.chain.plan)" :description="chainDesc('plan', '计划')" />
        <el-step title="招采立项" :status="chainStatus(detail.chain.project)" :description="chainDesc('project', '立项')" />
        <el-step title="入围考察" :status="detail.chain.entries.length ? 'success' : 'wait'" :description="detail.chain.entries.length ? `${detail.chain.entries.length} 家入围（${detail.chain.entries.map(e => e.supplierName).join('、')}）` : '暂无入围记录'" />
        <el-step title="发标答疑" :status="chainStatus(detail.chain.tender)" :description="chainDesc('tender', '发标')" />
        <el-step title="回标" :status="detail.chain.bids.length ? 'success' : 'wait'" :description="detail.chain.bids.length ? `${detail.chain.bids.length} 家回标，最低报价 ${money(Math.min(...detail.chain.bids.map(b => Number(b.priceAmount) || Infinity)))} 元` : '暂无回标记录'" />
        <el-step title="评标" :status="chainStatus(detail.chain.evaluation)" :description="chainDesc('evaluation', '评标')" />
        <el-step title="定标" :status="chainStatus(detail.chain.award)" :description="chainDesc('award', '定标')" />
        <el-step title="合同" :status="detail.chain.contract ? 'success' : 'wait'" :description="detail.chain.contract ? `合同编号：${detail.chain.contract.contractCode || detail.chain.contract.id}｜状态：${contractStatusLabel(detail.chain.contract)}` : '未生成合同'" />
      </el-steps>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { money } from '../../../utils/format'
import {
  ledgerPage, ledgerStats, ledgerDetail, fetchProjectOptions,
  statusMeta, PROC_METHODS, methodLabel
} from '../../../api/proc'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const query = reactive({ current: 1, size: 20, kw: '', projectId: null, method: '' })

const projects = ref([])
const projectName = id => projects.value.find(p => p.id === id)?.name || '-'

const load = async () => {
  loading.value = true
  try {
    const page = await ledgerPage(query)
    list.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const loadStats = () => {
  ledgerStats().then(s => { stats.value = s || {} }).catch(() => {})
}
const reset = () => {
  query.current = 1; query.kw = ''; query.projectId = null; query.method = ''
  load()
}
const savingStyle = row => (row.budgetAmount - row.awardAmount >= 0 ? 'color:#67c23a;font-weight:600' : 'color:#f56c6c;font-weight:600')

// ============ 链路详情 ============
const detail = reactive({ visible: false, data: {}, chain: {} })
const chainActive = computed(() => {
  const c = detail.chain
  if (c.contract) return 9
  if (c.award) return 8
  if (c.evaluation) return 7
  if (c.bids?.length) return 6
  if (c.tender) return 5
  if (c.entries?.length) return 4
  if (c.project) return 3
  if (c.plan) return 2
  if (c.requirement) return 1
  return 0
})
const chainStatus = node => (node ? 'success' : 'wait')
const chainDesc = (key, label) => {
  const node = detail.chain[key]
  if (!node) return `暂无${label}记录`
  const code = node.reqCode || node.planCode || node.projectCode || node.tenderCode || node.awardCode || ''
  const st = node.status ? statusMeta(node.status)[0] : ''
  return `${code ? `编号：${code}｜` : ''}${st ? `状态：${st}` : ''}`.replace(/｜$/, '')
}
const contractStatusLabel = c => (c.status ? statusMeta(c.status, { SIGNING: ['审批中', 'warning'], ACTIVE: ['履行中', 'success'], SETTLED: ['已结算', 'success'] })[0] : '-')
const openDetail = async row => {
  const d = await ledgerDetail(row.procProjectId || row.id)
  detail.data = { ...row, ...(d?.project || d || {}) }
  detail.chain = {
    requirement: d?.requirement || null,
    plan: d?.plan || null,
    project: d?.project || null,
    entries: d?.entries || [],
    tender: d?.tender || null,
    bids: d?.bids || [],
    evaluation: d?.evaluation || null,
    award: d?.award || null,
    contract: d?.contract || null
  }
  detail.visible = true
}

onMounted(async () => {
  load()
  loadStats()
  fetchProjectOptions().then(rs => { projects.value = rs || [] }).catch(() => {})
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 8px; }
.block-title { margin: 16px 0 10px; font-size: 14px; font-weight: 600; color: #0c447c; }
</style>
