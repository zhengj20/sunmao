<template>
  <div class="page-container">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 200px" @change="onSearch">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="query.discipline" placeholder="专业" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="d in DISCIPLINES" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="(label, key) in DRAWING_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="query.kw" placeholder="单号/标题" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
        <el-button type="primary" @click="openUpload">上传图纸</el-button>
      </div>
    </el-card>

    <!-- 汇总统计 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">图纸（张）</div>
          <div class="value">{{ summary.totalDrawings ?? 0 }}<span class="sub">通过 {{ summary.passed ?? 0 }} · 驳回 {{ summary.rejected ?? 0 }}</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">审查中（张）</div>
          <div class="value">{{ summary.inReview ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">审查问题（条）</div>
          <div class="value">{{ summary.totalIssues ?? 0 }}<span class="sub">已解决 {{ summary.resolvedIssues ?? 0 }}</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="label">待解决（条）</div>
          <div class="value" :class="{ 'val-warn': (summary.openIssues ?? 0) > 0 }">{{ summary.openIssues ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" show-icon class="hint-alert">
      流程：上传 CAD/设计图纸 →「AI 预审」生成审查要点 → 审查人逐条提出/确认 → 逐条「解决」或「无需处理」→ 无「严重」问题未解决时「通过」，否则「驳回」退回整改。AI 仅提示、不代审。
    </el-alert>

    <!-- 审查单列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="drawingCode" label="审查单号" width="150" show-overflow-tooltip />
        <el-table-column prop="title" label="图纸标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="专业" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ disciplineLabel(row.discipline) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="drawingVersion" label="版本" width="70" align="center">
          <template #default="{ row }">{{ row.drawingVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="阶段" width="90" align="center">
          <template #default="{ row }">{{ stageLabel(row.stage) }}</template>
        </el-table-column>
        <el-table-column label="问题（总/待/已）" width="140" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.totalIssues || 0 }}</span>
            <span class="muted"> / </span>
            <span class="num" :class="{ 'val-warn': (row.openIssues || 0) > 0 }">{{ row.openIssues || 0 }}</span>
            <span class="muted"> / </span>
            <span class="num ok">{{ row.resolvedIssues || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="drawingStatusMeta(row.status)[1]" size="small">{{ drawingStatusMeta(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150" align="center">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">审查</el-button>
            <el-button link type="success" size="small" @click="goOnline(row)">在线审图</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="onSearch"
      />
    </div>

    <!-- 上传图纸 -->
    <el-dialog v-model="uploadVisible" title="上传图纸" width="600px">
      <el-form :model="upForm" label-width="90px">
        <el-form-item label="图纸文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".dwg,.dxf,.png,.jpg,.jpeg,.pdf"
            :on-change="onFileChange"
            :on-exceed="() => ElMessage.warning('一次仅上传一张图纸')"
          >
            <el-button>选择文件</el-button>
            <template #tip><div class="el-upload__tip">支持 .dwg / .dxf / .png / .jpg / .pdf，单文件 ≤ 50MB</div></template>
          </el-upload>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="项目" required>
              <el-select v-model="upForm.projectId" filterable style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="专业" required>
              <el-select v-model="upForm.discipline" style="width: 100%">
                <el-option v-for="d in DISCIPLINES" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图纸标题" required>
          <el-input v-model="upForm.title" maxlength="100" placeholder="如：1#楼首层平面图 / 基础平面布置图" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="版本">
              <el-input v-model="upForm.version" maxlength="20" placeholder="如 A、V1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="阶段">
              <el-select v-model="upForm.stage" clearable style="width: 100%">
                <el-option v-for="s in STAGES" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设计单位">
              <el-input v-model="upForm.designUnit" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="upForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onUpload">上传并创建审查单</el-button>
      </template>
    </el-dialog>

    <!-- 审查工作台（详情抽屉） -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="62%">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small" class="detail-desc">
          <el-descriptions-item label="审查单号">{{ detail.drawingCode }}</el-descriptions-item>
          <el-descriptions-item label="专业">{{ disciplineLabel(detail.discipline) }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ stageLabel(detail.stage) }}</el-descriptions-item>
          <el-descriptions-item label="图纸标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.drawingVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目" :span="2">{{ detail.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="drawingStatusMeta(detail.status)[1]" size="small">{{ drawingStatusMeta(detail.status)[0] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="设计单位">{{ detail.designUnit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件">{{ detail.fileName }}（{{ fmtSize(detail.fileSize) }}）</el-descriptions-item>
          <el-descriptions-item label="预审模式">
            <el-tag :type="detail.aiMode === 'LLM' ? 'primary' : 'info'" size="small">{{ detail.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 操作区 -->
        <div class="action-bar">
          <div class="action-left">
            <el-button type="primary" size="small" :loading="aiReviewing" :disabled="detail.status === 'PASSED'" @click="onAiReview">
              {{ detail.aiMode ? '重新 AI 预审' : 'AI / 系统预审' }}
            </el-button>
            <el-button size="small" @click="openPreview">预览图纸</el-button>
            <el-button size="small" type="success" plain @click="openReport">审查报告</el-button>
          </div>
          <div class="action-right">
            <el-button v-if="detail.status !== 'PASSED'" type="success" size="small" @click="onPass">审查通过</el-button>
            <el-button v-if="detail.status !== 'REJECTED'" type="danger" size="small" @click="openReject">驳回</el-button>
          </div>
        </div>

        <!-- AI 预审意见 -->
        <el-card v-if="detail.aiNote" shadow="never" class="ai-card" :class="{ 'ai-llm': detail.aiMode === 'LLM' }">
          <div class="ai-title">
            <span class="ai-badge">{{ detail.aiMode === 'LLM' ? 'LLM 大模型' : '规则引擎' }}预审意见</span>
          </div>
          <div class="ai-note">{{ detail.aiNote }}</div>
          <div class="ai-advisory">🛡 {{ advisory }}</div>
        </el-card>

        <!-- 问题清单 -->
        <div class="issue-header">
          <span>审查问题（{{ issues.length }}）</span>
          <el-button type="primary" size="small" @click="openIssueForm()">提出问题</el-button>
        </div>

        <el-empty v-if="issues.length === 0" description="暂无问题。可点击「AI 预审」生成审查要点，或「提出问题」人工录入" :image-size="80" />

        <el-table :data="sortedIssues" border size="small" class="issue-table">
          <el-table-column label="#" width="40" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="来源" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.source === 'AI' ? 'primary' : 'success'" size="small" effect="plain">{{ row.source === 'AI' ? 'AI' : '人工' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="严重度" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="levelMeta(row.level).type" size="small">{{ levelMeta(row.level).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="100">
            <template #default="{ row }">{{ row.category || '-' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="问题描述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="suggestion" label="建议" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.suggestion || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="issueStatusMeta(row.status)[1]" size="small">{{ issueStatusMeta(row.status)[0] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'OPEN'">
                <el-button link type="success" size="small" @click="openResolve(row)">解决</el-button>
                <el-button link size="small" @click="onDismiss(row)">无需处理</el-button>
                <el-button link type="danger" size="small" @click="onDeleteIssue(row)">删除</el-button>
              </template>
              <template v-else>
                <el-button link size="small" @click="openResolve(row)">{{ row.status === 'RESOLVED' ? '改回' : '重开' }}</el-button>
                <el-button link type="danger" size="small" @click="onDeleteIssue(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>

        <!-- 已解决/无需处理明细 -->
        <el-collapse v-if="resolvedIssues.length > 0" class="resolved-collapse">
          <el-collapse-item :title="`已处理问题（${resolvedIssues.length}）`">
            <div v-for="row in resolvedIssues" :key="row.id" class="resolved-item">
              <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'info'" size="small" style="margin-right: 8px">{{ issueStatusMeta(row.status)[0] }}</el-tag>
              <span class="resolved-desc">{{ row.description }}</span>
              <span v-if="row.resolveNote" class="resolved-note">→ {{ row.resolveNote }}</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>

    <!-- 提出/编辑问题 -->
    <el-dialog v-model="issueFormVisible" :title="issueEditId ? '编辑问题' : '提出问题'" width="560px">
      <el-form :model="issueForm" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="严重度">
              <el-select v-model="issueForm.level" style="width: 100%">
                <el-option v-for="l in LEVELS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="issueForm.category" allow-create filterable clearable style="width: 100%">
                <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置">
          <el-input v-model="issueForm.location" maxlength="50" placeholder="图号/部位/轴号，如：轴③/⑦ 交 A 轴" />
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="issueForm.description" type="textarea" :rows="3" maxlength="500" placeholder="描述发现的问题" />
        </el-form-item>
        <el-form-item label="修改建议">
          <el-input v-model="issueForm.suggestion" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="issueForm.assigneeName" maxlength="30" placeholder="如：结构专业张工" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveIssue">保存</el-button>
      </template>
    </el-dialog>

    <!-- 解决问题 -->
    <el-dialog v-model="resolveVisible" title="解决问题" width="480px">
      <el-form :model="resolveForm" label-width="90px">
        <el-form-item label="解决说明">
          <el-input v-model="resolveForm.note" type="textarea" :rows="4" maxlength="200" placeholder="说明如何修改/已确认无问题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onResolve">确认解决</el-button>
      </template>
    </el-dialog>

    <!-- 驳回 -->
    <el-dialog v-model="rejectVisible" title="审查驳回" width="480px">
      <el-form :model="rejectForm" label-width="90px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" maxlength="200" placeholder="说明驳回原因，退回设计单位整改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 预览图纸 -->
    <el-drawer v-model="previewVisible" title="图纸预览" size="70%">
      <div v-if="detail" class="preview-body">
        <div v-if="isImage" class="preview-img-wrap">
          <img :src="fileUrl(detail.filePath)" :alt="detail.title" class="preview-img" />
        </div>
        <div v-else-if="isPdf" class="preview-pdf-wrap">
          <iframe :src="fileUrl(detail.filePath)" class="preview-pdf" title="图纸预览" />
        </div>
        <div v-else class="preview-tip">
          <el-empty description="该格式（.dwg/.dxf）暂不支持在线预览，请下载后用专业软件打开" :image-size="80" />
          <el-button type="primary" @click="download">下载原文件</el-button>
        </div>
        <div class="preview-toolbar">
          <el-button @click="download">下载原文件</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '../../../utils/format'
import {
  fetchProjectOptions, fetchDrawings, fetchDrawing, deleteDrawing,
  uploadDrawing, aiReview, addIssue, updateIssue, resolveIssue, dismissIssue, deleteIssue,
  passDrawing, rejectDrawing, fetchSummary, fileUrl, buildReportHtml,
  DISCIPLINES, STAGES, LEVELS, CATEGORIES,
  disciplineLabel, stageLabel, levelMeta, drawingStatusMeta, issueStatusMeta
} from '../../../api/dwg'

const loading = ref(false)
const saving = ref(false)
const projects = ref([])
const rows = ref([])
const total = ref(0)
const summary = ref({})
const query = reactive({ projectId: null, discipline: null, status: null, kw: '', current: 1, size: 20 })

// 上传
const uploadVisible = ref(false)
const upForm = reactive({ projectId: null, discipline: 'ARCH', title: '', version: '', stage: 'CONSTRUCTION', designUnit: '', remark: '' })
const fileRef = ref(null)
const uploadRef = ref(null)

// 详情工作台
const detailVisible = ref(false)
const detail = ref(null)
const issues = ref([])
const advisory = ref('')
const aiReviewing = ref(false)
const previewVisible = ref(false)

// 问题表单
const issueFormVisible = ref(false)
const issueEditId = ref(null)
const issueForm = reactive({ level: 'MEDIUM', category: '规范符合性', location: '', description: '', suggestion: '', assigneeName: '' })
// 解决
const resolveVisible = ref(false)
const resolveForm = reactive({ id: null, note: '' })
// 驳回
const rejectVisible = ref(false)
const rejectForm = reactive({ id: null, reason: '' })

const detailTitle = computed(() => (detail.value ? `${detail.value.drawingCode} · ${detail.value.title}` : '审查工作台'))
const sortedIssues = computed(() => {
  const rank = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  return [...issues.value].sort((a, b) => (rank[a.level] ?? 9) - (rank[b.level] ?? 9) || (a.seq || 0) - (b.seq || 0))
})
const resolvedIssues = computed(() => issues.value.filter(i => i.status !== 'OPEN'))
const isImage = computed(() => /\.(png|jpe?g)$/i.test((detail.value?.fileName) || (detail.value?.filePath) || ''))
const isPdf = computed(() => /\.pdf$/i.test((detail.value?.fileName) || (detail.value?.filePath) || ''))

const fmtSize = n => {
  if (n == null) return '未知'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

const loadList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      fetchDrawings(query),
      fetchSummary({ projectId: query.projectId })
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
  query.discipline = null; query.status = null; query.kw = ''
  onSearch()
}

// ---------- 上传 ----------
const openUpload = () => {
  Object.assign(upForm, { projectId: query.projectId || (projects.value[0]?.id), discipline: 'ARCH', title: '', version: '', stage: 'CONSTRUCTION', designUnit: '', remark: '' })
  fileRef.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
  uploadVisible.value = true
}
const onFileChange = file => { fileRef.value = file.raw }
const onUpload = async () => {
  if (!fileRef.value) return ElMessage.warning('请选择图纸文件')
  if (!upForm.projectId) return ElMessage.warning('请选择项目')
  if (!upForm.title) return ElMessage.warning('请填写图纸标题')
  saving.value = true
  try {
    await uploadDrawing(fileRef.value, { ...upForm })
    ElMessage.success('图纸已上传并创建审查单')
    uploadVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

// ---------- 详情工作台 ----------
const openDetail = async row => {
  detailVisible.value = true
  detail.value = null
  issues.value = []
  const d = await fetchDrawing(row.id)
  detail.value = d.drawing
  issues.value = d.issues || []
  advisory.value = d.advisory || ''
}

const goOnline = row => {
  router.push({ path: '/dwg/online', query: { id: String(row.id) } })
}

const onAiReview = async () => {
  aiReviewing.value = true
  try {
    const r = await aiReview(detail.value.id)
    ElMessage.success(`预审完成（${r.aiMode === 'LLM' ? 'LLM' : '规则引擎'}），生成 ${r.pointCount} 条审查要点`)
    await refreshDetail()
  } finally {
    aiReviewing.value = false
  }
}

const refreshDetail = async () => {
  const d = await fetchDrawing(detail.value.id)
  detail.value = d.drawing
  issues.value = d.issues || []
  advisory.value = d.advisory || ''
}

// ---------- 问题管理 ----------
const openIssueForm = (row) => {
  if (row) {
    issueEditId.value = row.id
    Object.assign(issueForm, {
      level: row.level, category: row.category || '规范符合性', location: row.location || '',
      description: row.description, suggestion: row.suggestion || '', assigneeName: row.assigneeName || ''
    })
  } else {
    issueEditId.value = null
    Object.assign(issueForm, { level: 'MEDIUM', category: '规范符合性', location: '', description: '', suggestion: '', assigneeName: '' })
  }
  issueFormVisible.value = true
}

const onSaveIssue = async () => {
  if (!issueForm.description) return ElMessage.warning('请填写问题描述')
  saving.value = true
  try {
    if (issueEditId.value) {
      await updateIssue(issueEditId.value, { ...issueForm })
      ElMessage.success('问题已更新')
    } else {
      await addIssue(detail.value.id, { ...issueForm })
      ElMessage.success('问题已提出')
    }
    issueFormVisible.value = false
    await refreshDetail()
    await loadList()
  } finally {
    saving.value = false
  }
}

const openResolve = row => {
  resolveForm.id = row.id
  resolveForm.note = ''
  resolveVisible.value = true
}
const onResolve = async () => {
  saving.value = true
  try {
    await resolveIssue(resolveForm.id, resolveForm.note)
    ElMessage.success('已标记解决')
    resolveVisible.value = false
    await refreshDetail()
    await loadList()
  } finally {
    saving.value = false
  }
}

const onDismiss = async row => {
  await ElMessageBox.confirm(`确认将「${row.description.slice(0, 20)}…」标记为无需处理？`, '提示', { type: 'info' })
  await dismissIssue(row.id)
  ElMessage.success('已标记无需处理')
  await refreshDetail()
  await loadList()
}

const onDeleteIssue = async row => {
  await ElMessageBox.confirm(`确认删除该问题「${row.description.slice(0, 20)}…」？`, '提示', { type: 'warning' })
  await deleteIssue(row.id)
  ElMessage.success('已删除')
  await refreshDetail()
  await loadList()
}

// ---------- 审查结论 ----------
const onPass = async () => {
  await ElMessageBox.confirm('确认该图纸审查通过？（要求无「严重」级问题未解决）', '审查通过', { type: 'success' })
  await passDrawing(detail.value.id)
  ElMessage.success('审查通过')
  await refreshDetail()
  await loadList()
}

const openReject = () => {
  rejectForm.id = detail.value.id
  rejectForm.reason = ''
  rejectVisible.value = true
}
const onReject = async () => {
  if (!rejectForm.reason) return ElMessage.warning('请填写驳回原因')
  saving.value = true
  try {
    await rejectDrawing(rejectForm.id, rejectForm.reason)
    ElMessage.success('已驳回，退回整改')
    rejectVisible.value = false
    await refreshDetail()
    await loadList()
  } finally {
    saving.value = false
  }
}

// ---------- 预览/下载 ----------
const openPreview = () => { previewVisible.value = true }
const openReport = () => {
  const html = buildReportHtml(detail.value, issues.value, advisory.value)
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const win = window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
  ElMessage.success('审查报告已在新标签页打开，可打印或下载')
  void win
}
const download = () => {
  if (!detail.value) return
  const a = document.createElement('a')
  a.href = fileUrl(detail.value.filePath)
  a.download = detail.value.fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除审查单【${row.drawingCode}】？`, '提示', { type: 'warning' })
  await deleteDrawing(row.id)
  ElMessage.success('已删除')
  await loadList()
}

onMounted(async () => {
  projects.value = await fetchProjectOptions()
  await loadList()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.toolbar .filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.stat-row { margin-bottom: 12px; }
.hint-alert { margin-bottom: 12px; }
.stat-card .label { font-size: 13px; color: #909399; }
.stat-card .value { font-size: 26px; font-weight: 700; margin-top: 6px; color: #303133; }
.stat-card .sub { font-size: 12px; color: #a0a4ab; font-weight: 400; margin-left: 8px; }
.val-warn { color: #c45656; }
.ok { color: #529b2e; }
.num { font-weight: 600; }
.muted { color: #a0a4ab; }

.detail-desc { margin-bottom: 12px; }
.action-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.action-left, .action-right { display: flex; gap: 8px; }

.ai-card { margin-bottom: 14px; }
.ai-card.ai-llm { border-left: 3px solid #409eff; }
.ai-title { margin-bottom: 8px; }
.ai-badge { font-size: 12px; color: #409eff; background: #ecf5ff; border: 1px solid #d9ecff; padding: 1px 8px; border-radius: 4px; }
.ai-note { font-size: 13px; line-height: 1.8; color: #303133; white-space: pre-wrap; }
.ai-advisory { margin-top: 10px; font-size: 12px; color: #e6a23c; }

.issue-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-weight: 600; }
.issue-table { margin-bottom: 6px; }

.resolved-collapse { margin-top: 8px; }
.resolved-item { padding: 6px 0; border-bottom: 1px solid #f2f3f5; font-size: 13px; }
.resolved-item:last-child { border-bottom: none; }
.resolved-desc { color: #606266; margin-right: 6px; }
.resolved-note { color: #529b2e; font-size: 12px; }

.preview-body { padding: 0 8px; }
.preview-img-wrap { text-align: center; background: #f5f7fa; border-radius: 6px; padding: 16px; }
.preview-img { max-width: 100%; max-height: 70vh; object-fit: contain; }
.preview-pdf-wrap { background: #f5f7fa; border-radius: 6px; overflow: hidden; }
.preview-pdf { width: 100%; height: 70vh; border: none; background: #fff; }
.preview-tip { text-align: center; padding: 20px 0; }
.preview-toolbar { text-align: center; margin-top: 14px; }
</style>
