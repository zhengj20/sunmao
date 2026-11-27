<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="page-sub">制度 / 指标 / 案例 · 知识图谱 · Wiki 阅读</p>
      </div>
      <el-button type="primary" @click="openForm()">新增文档</el-button>
    </div>

    <el-tabs v-model="activeTab" class="kb-tabs">
      <!-- ===== Tab 1: 文档列表 ===== -->
      <el-tab-pane label="文档列表" name="list">
        <div class="toolbar">
          <el-select v-model="query.category" placeholder="分类" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="c in KB_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-input v-model="query.kw" placeholder="关键词搜索" clearable style="width: 200px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">检索</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>

        <el-table v-loading="loading" :data="rows" border stripe class="mt-12">
          <el-table-column prop="docCode" label="编码" width="140" show-overflow-tooltip />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="80" align="center">
            <template #default="{ row }"><el-tag size="small" :type="catTag(row.category)">{{ row.category }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="wikiPath" label="Wiki路径" min-width="160" show-overflow-tooltip>
            <template #default="{ row }"><span class="wiki-path">{{ row.wikiPath || '—' }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }"><el-tag size="small" :type="statusMeta(row.status, KB_STATUS)[1]">{{ statusMeta(row.status, KB_STATUS)[0] }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="140" align="center">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="onView(row)">查看</el-button>
              <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
            :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="loadList" @size-change="onSearch" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 知识图谱 ===== -->
      <el-tab-pane label="知识图谱" name="graph">
        <div class="graph-wrapper">
          <KbGraph :nodes="graphNodes" :edges="graphEdges" @node-click="onGraphNodeClick" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: Wiki ===== -->
      <el-tab-pane label="Wiki" name="wiki">
        <div class="wiki-layout">
          <div class="wiki-sidebar">
            <div class="wiki-tree">
              <div v-for="group in wikiGroups" :key="group.path" class="wiki-group">
                <div class="wiki-group-label">{{ group.path }}</div>
                <div v-for="doc in group.docs" :key="doc.id"
                  class="wiki-item" :class="{ active: wikiCurrent?.id === doc.id }"
                  @click="openWikiDoc(doc)">
                  {{ doc.title }}
                </div>
              </div>
            </div>
          </div>
          <div class="wiki-reader" v-if="wikiCurrent">
            <div class="wiki-reader-head">
              <h3 class="wiki-title">{{ wikiCurrent.title }}</h3>
              <div class="wiki-meta">
                <el-tag size="small" :type="catTag(wikiCurrent.category)">{{ wikiCurrent.category }}</el-tag>
                <span class="wiki-path-text">{{ wikiCurrent.wikiPath }}</span>
              </div>
            </div>
            <div class="wiki-content" v-html="renderWikiContent(wikiCurrent.content)"></div>
            <div v-if="wikiRelated.length" class="wiki-related">
              <h4>关联文档</h4>
              <div class="related-list">
                <div v-for="r in wikiRelated" :key="r.doc.id" class="related-item" @click="openWikiDoc(r.doc)">
                  <span class="rel-dot" />
                  <span class="rel-title">{{ r.doc.title }}</span>
                  <span class="rel-label">{{ r.label || r.relation }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="wiki-empty">
            <el-icon :size="48" color="#cbd5e1"><Document /></el-icon>
            <p>从左侧选择文档开始阅读</p>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 查看抽屉 -->
    <el-drawer v-model="viewVisible" :title="viewTitle" size="55%">
      <div class="view-body">
        <div class="detail-item"><div class="dl">编码</div><div class="dv">{{ view?.docCode || '-' }}</div></div>
        <div class="detail-item"><div class="dl">分类</div><div class="dv">{{ view?.category || '-' }}</div></div>
        <div class="detail-item"><div class="dl">Wiki路径</div><div class="dv">{{ view?.wikiPath || '-' }}</div></div>
        <div class="detail-item"><div class="dl">内容</div><div class="dv content">{{ view?.content || '-' }}</div></div>
        <div v-if="viewRelated.length" class="detail-item">
          <div class="dl">关联文档</div>
          <div class="dv">
            <div v-for="r in viewRelated" :key="r.doc.id" class="rel-item">
              <el-tag size="small">{{ r.relation }}</el-tag>
              <span class="rel-name">{{ r.doc.title }}</span>
              <span v-if="r.label" class="rel-lbl">({{ r.label }})</span>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑文档' : '新增文档'" width="640px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required>
              <el-input v-model="form.docCode" maxlength="64" :disabled="!!form.id" placeholder="如 KB_POLICY_002" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%">
                <el-option v-for="c in KB_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="Wiki路径">
          <el-input v-model="form.wikiPath" placeholder="如 /制度/折扣审批" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="支持 [[doc_code]] 语法引用其他文档" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
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
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { fmtTime } from '../../../utils/format'
import {
  kbPage, kbDetail, kbCreate, kbUpdate, kbDelete,
  kbGraph, kbRelated, kbWiki,
  KB_CATEGORIES, KB_STATUS, statusMeta
} from '../../../api/ai'
import KbGraph from '../../../components/KbGraph.vue'

// ===== Tab =====
const activeTab = ref('list')

// ===== 列表 =====
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ category: null, kw: '', current: 1, size: 20 })
const catTag = c => ({ '制度': 'primary', '指标': 'success', '案例': 'warning' }[c] || 'info')

const loadList = async () => {
  loading.value = true
  try {
    const page = await kbPage({ current: query.current, size: query.size, category: query.category || undefined, kw: query.kw || undefined })
    rows.value = page?.records || []
    total.value = Number(page?.total) || 0
  } finally { loading.value = false }
}
const onSearch = () => { query.current = 1; loadList() }
const onReset = () => { query.category = null; query.kw = ''; onSearch() }

// ===== 查看 =====
const viewVisible = ref(false)
const viewTitle = ref('')
const view = ref(null)
const viewRelated = ref([])
const onView = async row => {
  const d = await kbDetail(row.id)
  view.value = d
  viewTitle.value = d.title || '文档详情'
  viewRelated.value = (await kbRelated(row.id).catch(() => [])) || []
  viewVisible.value = true
}

// ===== 表单 =====
const formVisible = ref(false)
const saving = ref(false)
const empty = () => ({ id: null, docCode: '', title: '', category: '制度', content: '', status: 'ACTIVE', wikiPath: '' })
const form = reactive(empty())
const openForm = row => {
  Object.assign(form, row
    ? { id: row.id, docCode: row.docCode, title: row.title, category: row.category, content: row.content, status: row.status, wikiPath: row.wikiPath || '' }
    : empty())
  formVisible.value = true
}
const onSave = async () => {
  if (!form.docCode) return ElMessage.warning('请填写编码')
  if (!form.title) return ElMessage.warning('请填写标题')
  saving.value = true
  try {
    const data = { docCode: form.docCode, title: form.title, category: form.category, content: form.content, status: form.status, wikiPath: form.wikiPath }
    if (form.id) { await kbUpdate(form.id, data) } else { await kbCreate(data) }
    ElMessage.success('已保存')
    formVisible.value = false
    loadList()
    if (activeTab.value === 'graph') loadGraph()
    if (activeTab.value === 'wiki') loadWiki()
  } finally { saving.value = false }
}
const onDelete = async row => {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」？`, '提示', { type: 'warning' })
  await kbDelete(row.id)
  ElMessage.success('已删除')
  loadList()
}

// ===== 知识图谱 =====
const graphNodes = ref([])
const graphEdges = ref([])
const loadGraph = async () => {
  try {
    const data = await kbGraph()
    graphNodes.value = data?.nodes || []
    graphEdges.value = data?.edges || []
  } catch { /* ignore */ }
}
const onGraphNodeClick = (node) => {
  // 切换到 Wiki tab 并打开该文档
  activeTab.value = 'wiki'
  openWikiDocById(node.id)
}

// ===== Wiki =====
const wikiDocs = ref([])
const wikiCurrent = ref(null)
const wikiRelated = ref([])

const wikiGroups = computed(() => {
  const groups = {}
  for (const doc of wikiDocs.value) {
    const path = doc.wikiPath || '/未分类'
    const topPath = '/' + (path.split('/').filter(Boolean)[0] || '未分类')
    if (!groups[topPath]) groups[topPath] = { path: topPath, docs: [] }
    groups[topPath].docs.push(doc)
  }
  return Object.values(groups)
})

const loadWiki = async () => {
  wikiDocs.value = (await kbWiki().catch(() => [])) || []
}

const openWikiDoc = async (doc) => {
  wikiCurrent.value = doc
  wikiRelated.value = (await kbRelated(doc.id).catch(() => [])) || []
}

const openWikiDocById = async (id) => {
  const doc = wikiDocs.value.find(d => d.id === id)
  if (doc) { openWikiDoc(doc); return }
  // 如果 wiki 列表没有，从 detail 加载
  try {
    const d = await kbDetail(id)
    wikiCurrent.value = d
    wikiRelated.value = (await kbRelated(id).catch(() => [])) || []
  } catch { /* ignore */ }
}

// Wiki 内容渲染：将 [[doc_code]] 转为可点击链接
const renderWikiContent = (content) => {
  if (!content) return '<p class="wiki-empty-text">暂无内容</p>'
  const docMap = Object.fromEntries(wikiDocs.value.map(d => [d.docCode, d]))
  let html = content
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/(\n)/g, '<br/>')
    .replace(/\[\[(\w+)\]\]/g, (match, code) => {
      const target = docMap[code]
      if (target) {
        return `<a class="wiki-link" data-doc-id="${target.id}" href="javascript:void(0)">${target.title || code}</a>`
      }
      return `<span class="wiki-link-broken">[${code}]</span>`
    })
  return html
}

// Wiki 链接点击
const onWikiLinkClick = (e) => {
  const link = e.target.closest('.wiki-link')
  if (link?.dataset.docId) {
    openWikiDocById(Number(link.dataset.docId))
  }
}

watch(activeTab, (tab) => {
  if (tab === 'graph' && !graphNodes.value.length) loadGraph()
  if (tab === 'wiki' && !wikiDocs.value.length) loadWiki()
})

onMounted(() => {
  loadList()
  document.addEventListener('click', onWikiLinkClick)
})
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 700; color: #0c447c; margin: 0; }
.page-sub { font-size: 13px; color: #6b7280; margin: 4px 0 0; }
.kb-tabs { background: #fff; border-radius: 12px; padding: 4px 16px 16px; }
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; padding: 8px 0; }
.mt-12 { margin-top: 12px; }
.pagination-bar { margin-top: 14px; display: flex; justify-content: flex-end; }
.wiki-path { font-family: monospace; font-size: 12px; color: #64748b; }

/* 图谱 */
.graph-wrapper { height: 580px; }

/* Wiki */
.wiki-layout { display: flex; gap: 16px; min-height: 500px; }
.wiki-sidebar { width: 240px; flex-shrink: 0; border-right: 1px solid #e2e8f0; padding-right: 14px; }
.wiki-tree { display: flex; flex-direction: column; gap: 14px; }
.wiki-group-label { font-size: 12px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: .5px; margin-bottom: 6px; padding-left: 4px; }
.wiki-item {
  padding: 8px 12px; border-radius: 8px; cursor: pointer; font-size: 13px; color: #334155;
  transition: background .12s;
}
.wiki-item:hover { background: #f1f5f9; }
.wiki-item.active { background: #e0ecf7; color: #185fa5; font-weight: 600; }
.wiki-reader { flex: 1; min-width: 0; }
.wiki-reader-head { margin-bottom: 16px; }
.wiki-title { font-size: 20px; font-weight: 700; color: #1e293b; margin: 0 0 8px; }
.wiki-meta { display: flex; align-items: center; gap: 10px; }
.wiki-path-text { font-family: monospace; font-size: 12px; color: #64748b; }
.wiki-content { font-size: 14px; line-height: 1.8; color: #334155; }
.wiki-content :deep(.wiki-link) { color: #185fa5; cursor: pointer; text-decoration: underline; font-weight: 500; }
.wiki-content :deep(.wiki-link-broken) { color: #94a3b8; font-style: italic; }
.wiki-content :deep(.wiki-empty-text) { color: #94a3b8; }
.wiki-related { margin-top: 24px; padding-top: 16px; border-top: 1px solid #e2e8f0; }
.wiki-related h4 { font-size: 13px; font-weight: 700; color: #475569; margin: 0 0 10px; }
.related-list { display: flex; flex-direction: column; gap: 6px; }
.related-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 8px; cursor: pointer; transition: background .12s; }
.related-item:hover { background: #f1f5f9; }
.rel-dot { width: 6px; height: 6px; border-radius: 50%; background: #185fa5; }
.rel-title { font-size: 13px; color: #334155; flex: 1; }
.rel-label { font-size: 11px; color: #94a3b8; }
.wiki-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #94a3b8; }
.wiki-empty p { margin-top: 12px; font-size: 14px; }

/* 查看抽屉 */
.view-body .content { white-space: pre-wrap; line-height: 1.7; }
.rel-item { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.rel-name { font-size: 13px; color: #334155; }
.rel-lbl { font-size: 11px; color: #94a3b8; }
</style>
