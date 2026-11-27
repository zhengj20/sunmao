<template>
  <div class="page-container">
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.bizType" placeholder="业态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="b in BIZ_TYPES" :key="b" :label="b" :value="b" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="v in Object.keys(TEMPLATE_STATUS)" :key="v" :label="TEMPLATE_STATUS[v][0]" :value="v" />
          </el-select>
          <el-input v-model="query.kw" placeholder="模板名称" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openForm()">新增模板</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无模板，点击右上角新增">
        <el-table-column prop="name" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业态" width="100" align="center">
          <template #default="{ row }">{{ row.bizType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="scale" label="规模" width="100" align="center">
          <template #default="{ row }">{{ row.scale || '-' }}</template>
        </el-table-column>
        <el-table-column prop="tplVersion" label="版本" width="120" align="center">
          <template #default="{ row }">{{ row.tplVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="meta(row.status)[1]" size="small">{{ meta(row.status)[0] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openNodes(row)">节点库</el-button>
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
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

    <!-- 新增 / 编辑模板 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑模板' : '新增模板'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" placeholder="如：住宅标准计划模板" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="业态">
              <el-select v-model="form.bizType" clearable style="width: 100%">
                <el-option v-for="b in BIZ_TYPES" :key="b" :label="b" :value="b" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规模">
              <el-select v-model="form.scale" clearable style="width: 100%">
                <el-option v-for="s in SCALES" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="版本">
              <el-input v-model="form.tplVersion" placeholder="如 v1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="v in Object.keys(TEMPLATE_STATUS)" :key="v" :label="TEMPLATE_STATUS[v][0]" :value="v" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 节点库 -->
    <el-dialog v-model="nodesVisible" :title="`节点库（${nodesTpl?.name || ''}）`" width="900px">
      <div class="node-toolbar">
        <span class="hint">WBS 标准节点：生成项目计划时按序展开为任务（级别=里程碑的节点将标记为里程碑）。</span>
        <el-button type="primary" size="small" @click="openNodeForm()">新增节点</el-button>
      </div>
      <el-table v-loading="nodesLoading" :data="nodes" border stripe size="small" empty-text="该模板暂无节点">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="nodeName" label="节点名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="level" label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === '里程碑' ? 'danger' : 'info'" size="small">{{ row.level || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseDuration" label="基准工期(工天)" width="130" align="center">
          <template #default="{ row }">{{ row.baseDuration ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="respLine" label="责任条线" width="110" align="center">
          <template #default="{ row }">{{ row.respLine || '-' }}</template>
        </el-table-column>
        <el-table-column prop="alertRule" label="预警规则" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.alertRule || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortNo" label="顺序" width="70" align="center">
          <template #default="{ row }">{{ row.sortNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openNodeForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDeleteNode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="nodesVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增 / 编辑节点 -->
    <el-dialog v-model="nodeFormVisible" :title="nodeForm.id ? '编辑节点' : '新增节点'" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="节点名称" required>
          <el-input v-model="nodeForm.nodeName" placeholder="如：拿地 / 开工 / 正负零" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="级别" required>
              <el-select v-model="nodeForm.level" style="width: 100%">
                <el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基准工期(工天)">
              <el-input-number v-model="nodeForm.baseDuration" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="责任条线">
              <el-select v-model="nodeForm.respLine" clearable style="width: 100%">
                <el-option v-for="s in SPECIALTIES" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="顺序">
              <el-input-number v-model="nodeForm.sortNo" :controls="false" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="预警规则">
          <el-input v-model="nodeForm.alertRule" type="textarea" :rows="2" placeholder="如：滞后 > 14 天转黄灯" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="nodeSaving" @click="onSaveNode">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  templatePage, templateCreate, templateUpdate,
  templateNodes, nodeCreate, nodeUpdate, nodeDelete,
  TEMPLATE_STATUS, statusMeta, LEVELS, SPECIALTIES
} from '../../../api/plan'

const BIZ_TYPES = ['住宅', '商业', '车位', '综合体']
const SCALES = ['大型', '中型', '小型']
const meta = s => statusMeta(s, TEMPLATE_STATUS)

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ bizType: null, status: null, kw: '', current: 1, size: 20 })

const loadList = async () => {
  loading.value = true
  try {
    const page = await templatePage({
      current: query.current, size: query.size,
      bizType: query.bizType || undefined, status: query.status || undefined, kw: query.kw || undefined
    })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.bizType = null; query.status = null; query.kw = ''; onSearch() }

// ===== 模板表单 =====
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, name: '', bizType: '住宅', scale: '中型', tplVersion: '', status: 'ACTIVE' })
const openForm = row => {
  if (row) Object.assign(form, { id: row.id, name: row.name, bizType: row.bizType, scale: row.scale, tplVersion: row.tplVersion, status: row.status })
  else Object.assign(form, { id: null, name: '', bizType: '住宅', scale: '中型', tplVersion: '', status: 'ACTIVE' })
  formVisible.value = true
}
const onSave = async () => {
  if (!form.name) return ElMessage.warning('请填写模板名称')
  saving.value = true
  try {
    const payload = { name: form.name, bizType: form.bizType || undefined, scale: form.scale || undefined, tplVersion: form.tplVersion || undefined, status: form.status }
    if (form.id) await templateUpdate(form.id, payload)
    else await templateCreate(payload)
    ElMessage.success(form.id ? '模板已更新' : '模板已创建')
    formVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

// ===== 节点库 =====
const nodesVisible = ref(false)
const nodesLoading = ref(false)
const nodes = ref([])
const nodesTpl = ref(null)
const loadNodes = async id => {
  nodesLoading.value = true
  try {
    nodes.value = (await templateNodes(id).catch(() => [])) || []
  } finally {
    nodesLoading.value = false
  }
}
const openNodes = row => {
  nodesTpl.value = row
  loadNodes(row.id)
  nodesVisible.value = true
}

const nodeFormVisible = ref(false)
const nodeSaving = ref(false)
const nodeForm = reactive({ id: null, nodeName: '', level: '一级', baseDuration: 30, respLine: '工程', alertRule: '', sortNo: 1 })
const openNodeForm = row => {
  if (row) Object.assign(nodeForm, { id: row.id, nodeName: row.nodeName, level: row.level, baseDuration: row.baseDuration, respLine: row.respLine, alertRule: row.alertRule, sortNo: row.sortNo })
  else Object.assign(nodeForm, { id: null, nodeName: '', level: '一级', baseDuration: 30, respLine: '工程', alertRule: '', sortNo: (nodes.value.length + 1) })
  nodeFormVisible.value = true
}
const onSaveNode = async () => {
  if (!nodeForm.nodeName) return ElMessage.warning('请填写节点名称')
  if (!nodesTpl.value) return ElMessage.warning('缺少模板')
  nodeSaving.value = true
  try {
    const payload = { nodeName: nodeForm.nodeName, level: nodeForm.level, baseDuration: nodeForm.baseDuration ?? undefined, respLine: nodeForm.respLine || undefined, alertRule: nodeForm.alertRule || undefined, sortNo: nodeForm.sortNo ?? undefined }
    if (nodeForm.id) await nodeUpdate(nodeForm.id, payload)
    else await nodeCreate(nodesTpl.value.id, payload)
    ElMessage.success(nodeForm.id ? '节点已更新' : '节点已新增')
    nodeFormVisible.value = false
    loadNodes(nodesTpl.value.id)
  } finally {
    nodeSaving.value = false
  }
}
const onDeleteNode = async row => {
  await ElMessageBox.confirm(`确认删除节点「${row.nodeName}」？`, '提示', { type: 'warning' })
  await nodeDelete(row.id)
  ElMessage.success('节点已删除')
  loadNodes(nodesTpl.value.id)
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.node-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.hint { font-size: 12px; color: #909399; }
</style>
