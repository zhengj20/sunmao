<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filter-line">
          <el-input
            v-model="kw"
            placeholder="搜索模板编码/名称/单据类型"
            clearable
            style="width: 260px"
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        </div>
        <div class="toolbar-right">
          <el-tag type="info" effect="plain">同一单据类型仅保留一个启用版本，在途流程走旧版</el-tag>
          <el-button type="primary" :icon="EditPen" @click="openDesigner()">新建流程</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="templateCode" label="模板编码" width="170" show-overflow-tooltip />
        <el-table-column prop="name" label="流程名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="绑定单据" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tplVersion" label="业务版本" width="90" align="center" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewFlow(row)">查看流程</el-button>
            <el-button type="primary" link :icon="EditPen" @click="openDesigner(row)">设计</el-button>
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

    <!-- 流程可视化 -->
    <el-dialog v-model="flowDlg" :title="`审批链预览 - ${currentTpl?.name || ''}`" width="860px" destroy-on-close>
      <template v-if="currentTpl">
        <el-descriptions :column="3" border size="small" class="tpl-desc">
          <el-descriptions-item label="模板编码">{{ currentTpl.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="绑定单据">{{ bizLabel(currentTpl.bizType) }}</el-descriptions-item>
          <el-descriptions-item label="业务版本">V{{ currentTpl.tplVersion || 1 }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="steps.length" class="steps-wrap">
          <el-steps :active="steps.length" align-center>
            <el-step
              v-for="(s, i) in steps"
              :key="i"
              :title="s.title"
              :description="s.desc"
              :icon="s.icon"
            >
              <template #icon>
                <el-icon v-if="s.icon" :size="18" :color="s.color">
                  <component :is="s.icon" />
                </el-icon>
              </template>
            </el-step>
          </el-steps>
        </div>
        <el-empty v-else description="模板定义解析失败" :image-size="70" />

        <!-- 节点明细 -->
        <el-table :data="steps" size="small" border class="node-table">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="title" label="节点名称" width="150" />
          <el-table-column prop="typeLabel" label="节点类型" width="100" />
          <el-table-column prop="desc" label="审批人 / 路由规则" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>

    <!-- 流程设计器 -->
    <el-dialog
      v-model="designerDlg"
      :title="designerTplId ? '编辑流程模板' : '新建流程模板'"
      fullscreen
      destroy-on-close
      :close-on-click-modal="false"
    >
      <Designer
        v-if="designerDlg"
        :template-id="designerTplId"
        @saved="onDesignerSaved"
        @cancel="designerDlg = false"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { templatePage } from '../../../api/workflow'
import { BIZ_TYPE, fmtTime } from '../../../utils/format'

const bizLabel = t => BIZ_TYPE[t] || t || '-'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 20 })
const kw = ref('')

const search = () => {
  query.current = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await templatePage({ ...query, kw: kw.value || undefined })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

// ============ 流程可视化 ============
const flowDlg = ref(false)
const currentTpl = ref(null)
const steps = ref([])

const OP_LABEL = { '>=': '≥', '>': '>', '<=': '≤', '<': '<', '==': '=' }

const viewFlow = row => {
  currentTpl.value = row
  steps.value = buildSteps(row.definition)
  flowDlg.value = true
}

const buildSteps = defText => {
  let def
  try {
    def = JSON.parse(defText)
  } catch {
    return []
  }
  const nodes = def.nodes || []
  const byId = {}
  nodes.forEach(n => (byId[n.id] = n))

  const result = [{ title: '发起', typeLabel: '开始', desc: '提交人发起流程', icon: 'User', color: '#185fa5' }]
  for (const n of nodes) {
    const type = n.type || 'APPROVE'
    if (type === 'APPROVE') {
      const assignees = (n.assignees || []).join(' / ')
      const who = n.assigneeType === 'USER' ? `指定用户：${assignees}` : `角色：${assignees}`
      result.push({
        title: n.name || n.id,
        typeLabel: '审批',
        desc: `${who}（${n.countersign === 'ALL' ? '会签' : '或签'}）`,
        icon: 'User',
        color: '#2a74bd'
      })
    } else if (type === 'CONDITION') {
      const rules = (n.branches || [])
        .map(b => {
          const target = byId[b.next]?.name || b.next
          return b.when
            ? `金额${OP_LABEL[b.when.op] || b.when.op}${Number(b.when.value).toLocaleString()} → ${target}`
            : `默认 → ${target}`
        })
        .join('；')
      result.push({ title: n.name || n.id, typeLabel: '条件路由', desc: rules, icon: 'RefreshRight', color: '#e6a23c' })
    } else if (type === 'END') {
      result.push({ title: '流程结束', typeLabel: '结束', desc: '审批通过，流程归档', icon: 'CircleCheck', color: '#67c23a' })
    }
  }
  return result
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; }
.filter-line { display: flex; align-items: center; gap: 10px; }
.tpl-desc { margin-bottom: 22px; }
.steps-wrap { margin: 6px 4px 20px; }
.node-table { margin-top: 4px; }
</style>
