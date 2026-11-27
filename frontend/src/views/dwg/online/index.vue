<template>
  <div ref="rootRef" class="online-review">
    <div class="topbar">
      <div class="left">
        <span class="lbl">审查图纸</span>
        <el-select v-model="drawingId" filterable placeholder="选择图纸" style="width: 340px" @change="selectDrawing">
          <el-option v-for="d in drawings" :key="d.id" :label="d.drawingCode + '  ' + d.title" :value="d.id">
            <span>{{ d.drawingCode }}</span>
            <span style="float:right;color:#909399;font-size:12px">{{ d.title }}</span>
          </el-option>
        </el-select>
        <el-tag v-if="drawing" :type="drawingStatusMeta(drawing.status)[1]" size="small">{{ drawingStatusMeta(drawing.status)[0] }}</el-tag>
        <span class="meta">{{ drawing ? `${disciplineLabel(drawing.discipline)} · ${stageLabel(drawing.stage)} · ${drawing.projectName || ''}` : '' }}</span>
      </div>
      <div class="actions">
        <el-button size="small" type="primary" :loading="aiLoading" :disabled="!drawing" @click="onAiReview">AI/系统预审</el-button>
        <el-button size="small" type="success" :disabled="!drawing" @click="onPass">通过</el-button>
        <el-button size="small" type="danger" :disabled="!drawing" @click="onReject">驳回</el-button>
        <el-button size="small" :disabled="!drawing" @click="onReport">审查报告</el-button>
      </div>
    </div>

    <div class="body">
      <div class="viewer-pane">
        <div class="viewer-toolbar">
          <span class="lbl">图面</span>
          <template v-if="kind === 'image'">
            <el-button size="small" @click="zoomBy(-0.2)">－</el-button>
            <el-button size="small" @click="zoom = 1">重置</el-button>
            <el-button size="small" @click="zoomBy(0.2)">＋</el-button>
            <span class="zoom-val">{{ Math.round(zoom * 100) }}%</span>
            <el-switch v-model="showMarkers" size="small" active-text="标注" class="mk-switch" />
            <el-button size="small" type="primary" plain :disabled="!canMark" @click="startMark">＋ 标记问题</el-button>
          </template>
          <template v-else-if="kind === 'dxf' || kind === 'dwg'">
            <el-switch v-model="showMarkers" size="small" active-text="标注" class="mk-switch" />
            <el-button size="small" :type="marking ? 'warning' : 'primary'" plain :disabled="!drawing" @click="toggleMarking">
              {{ marking ? '退出标记' : '＋ 标记问题' }}
            </el-button>
            <span class="tip">点击图面位置即可标记问题；平移/缩放/测量/图层用查看器内置工具</span>
          </template>
          <span class="tip" v-else-if="kind === 'pdf'">PDF 在线查看（标注请用图片）</span>
          <el-button v-if="kind === 'pdf'" size="small" @click="toggleFullscreen">全屏</el-button>
        </div>

        <div class="viewport" ref="viewportEl">
          <el-empty v-if="!drawing" description="请选择要审查的图纸" />
          <div v-else-if="kind === 'image'" class="drawing-box">
            <img ref="imgEl" :src="fileUrl(drawing.filePath)" :alt="drawing.title" class="drawing-img"
                 :style="{ width: (baseW * zoom) + 'px' }" @click="onDrawClick" />
            <div class="markers" v-if="showMarkers">
              <div v-for="m in markers" :key="m.id" class="marker"
                   :class="['lv-' + m.level, { sel: selId === m.id }]"
                   :style="{ left: m.markerX + '%', top: m.markerY + '%' }"
                   :title="m.description" @click.stop="selectIssue(m)">{{ m.no }}</div>
            </div>
          </div>
          <div v-else-if="kind === 'dxf'" class="cad-viewer-wrap">
            <DxfMarkViewer
              :key="drawing.id"
              :file-url="fileUrl(drawing.filePath)"
              :marking="marking"
              :show-markers="showMarkers"
              :markers="markers"
              :selected-id="selId"
              @mark="onCadMark"
              @select-marker="selectIssue" />
          </div>
          <div v-else-if="kind === 'dwg'" class="cad-viewer-wrap">
            <DwgViewer
              :key="drawing.id"
              :file-url="fileUrl(drawing.filePath)"
              :marking="marking"
              :show-markers="showMarkers"
              :markers="markers"
              :selected-id="selId"
              @mark="onCadMark"
              @select-marker="selectIssue" />
          </div>
          <div v-else-if="kind === 'pdf'" class="pdf-box">
            <iframe :src="fileUrl(drawing.filePath)" class="pdf-frame" title="图纸预览" />
          </div>
        </div>
        <div class="hint" v-if="kind === 'image'">提示：点击图面任意位置即可在该处<strong>标记问题</strong>；点击编号标记可查看/编辑。</div>
        <div class="hint" v-else-if="kind === 'dxf' || kind === 'dwg'">提示：点「<strong>＋ 标记问题</strong>」进入标记模式，点击图面位置登记问题；标记编号在右侧清单对应。</div>
      </div>

      <div class="issue-pane">
        <div class="issue-header">
          <span>审查问题（{{ issues.length }}）</span>
          <span class="cnt">
            <b class="c-open">{{ openCount }}</b> 待解决 · <b class="c-res">{{ resolvedCount }}</b> 已解决
          </span>
        </div>
        <el-scrollbar class="issue-list" v-loading="loading">
          <div v-if="!issues.length" class="empty">
            <el-empty description="暂无审查问题，可点击「AI/系统预审」自动生成，或在图面上标记" :image-size="80" />
          </div>
          <div v-for="it in sortedIssues" :key="it.id" class="issue-item"
               :class="{ sel: selId === it.id, done: it.status !== 'OPEN' }" @click="selectIssue(it)">
            <div class="it-top">
              <span v-if="noOf(it)" class="mk-no" :class="'lv-' + it.level">{{ noOf(it) }}</span>
              <el-tag :type="levelMeta(it.level).type" size="small">{{ levelMeta(it.level).label }}</el-tag>
              <el-tag size="small" :type="it.source === 'AI' ? 'info' : 'success'" effect="plain">{{ it.source === 'AI' ? 'AI' : '人工' }}</el-tag>
              <span class="it-cat">{{ it.category || '未分类' }}</span>
              <el-tag v-if="it.status === 'RESOLVED'" type="success" size="small">已解决</el-tag>
              <el-tag v-else-if="it.status === 'REJECTED'" type="info" size="small">无需处理</el-tag>
            </div>
            <div class="it-desc">{{ it.description }}</div>
            <div class="it-sug" v-if="it.suggestion">建议：{{ it.suggestion }}</div>
            <div class="it-loc" v-if="it.location">位置：{{ it.location }}</div>
            <div class="it-actions" v-if="it.status === 'OPEN'" @click.stop>
              <el-button link type="primary" size="small" @click="resolve(it)">解决</el-button>
              <el-button link size="small" @click="dismiss(it)">无需处理</el-button>
              <el-button link type="danger" size="small" @click="del(it)">删除</el-button>
            </div>
            <div class="it-res" v-else>解决说明：{{ it.resolveNote || '—' }}</div>
          </div>
        </el-scrollbar>
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="editId ? '编辑问题' : '标记问题（图面标注）'" width="540px" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="10">
          <el-col :span="8"><el-form-item label="严重度">
            <el-select v-model="form.level" style="width:100%">
              <el-option v-for="l in LEVELS" :key="l.value" :label="l.label" :value="l.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="分类">
            <el-select v-model="form.category" allow-create filterable style="width:100%">
              <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="责任人">
            <el-input v-model="form.assigneeName" placeholder="选填" />
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="问题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="修改建议">
          <el-input v-model="form.suggestion" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-form-item label="标注位置">
          <span v-if="form.markerX != null" class="mk-pos">已标注：({{ form.markerX }}, {{ form.markerY }})（图面百分比）</span>
          <span v-else class="mk-pos muted">无图面标注（仅列表问题）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回原因" width="460px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因（将退回设计单位整改）" maxlength="200" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, defineAsyncComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  fetchDrawings, fetchDrawing, aiReview, addIssue, updateIssue, resolveIssue, dismissIssue, deleteIssue,
  passDrawing, rejectDrawing, buildReportHtml, fileUrl,
  LEVELS, CATEGORIES,
  disciplineLabel, stageLabel, levelMeta, drawingStatusMeta
} from '../../../api/dwg'

const DwgViewer = defineAsyncComponent(() => import('../../../components/cad/DwgViewer.vue'))
const DxfMarkViewer = defineAsyncComponent(() => import('../../../components/cad/DxfMarkViewer.vue'))

const route = useRoute()

const baseW = 880

const drawings = ref([])
const drawingId = ref(null)
const drawing = ref(null)
const issues = ref([])
const loading = ref(false)
const aiLoading = ref(false)
const zoom = ref(1)
const showMarkers = ref(true)
const selId = ref(null)
const imgEl = ref(null)
const viewportEl = ref(null)
const rootRef = ref(null)
const marking = ref(false)

const formVisible = ref(false)
const editId = ref(null)
const saving = ref(false)
const form = reactive({ level: 'MEDIUM', category: '图面表达', description: '', suggestion: '', assigneeName: '', markerX: null, markerY: null })
const rejectVisible = ref(false)
const rejectReason = ref('')

const kind = computed(() => {
  const f = ((drawing.value?.fileName) || (drawing.value?.filePath) || '').toLowerCase()
  if (/\.(png|jpe?g)$/.test(f)) return 'image'
  // DXF / DWG 统一走 dxf-vuer：它按"内容"解析、与扩展名无关、对不规范的 DXF 也更宽容。
  // mlightcad 依赖按扩展名选解析器且缺少 DWG 解析器，打开 .dwg 会直接"无法打开文件"。
  // （真·二进制 DWG 需后端转 DXF 或补 libredwg 解析器，属后续独立事项）
  if (/\.(dxf|dwg)$/.test(f)) return 'dxf'
  if (/\.pdf$/.test(f)) return 'pdf'
  return 'dwg'
})
const canMark = computed(() => !!drawing.value && kind.value === 'image')

const sortedIssues = computed(() => [...issues.value].sort((a, b) => (a.seq || 0) - (b.seq || 0)))
const openCount = computed(() => issues.value.filter(i => i.status === 'OPEN').length)
const resolvedCount = computed(() => issues.value.filter(i => i.status !== 'OPEN').length)
const levelRank = l => ({ HIGH: 0, MEDIUM: 1, LOW: 2 }[l] ?? 3)
const markers = computed(() =>
  issues.value
    .filter(i => i.markerX != null && i.markerY != null)
    .sort((a, b) => levelRank(a.level) - levelRank(b.level) || (a.seq || 0) - (b.seq || 0))
    .map((i, idx) => ({ ...i, no: idx + 1 }))
)
const noOf = it => { const m = markers.value.find(x => x.id === it.id); return m ? m.no : null }

const loadDrawings = async () => {
  const p = await fetchDrawings({ size: 200 })
  drawings.value = p?.records || []
}

const selectDrawing = async id => {
  if (!id) { drawing.value = null; issues.value = []; return }
  loading.value = true
  selId.value = null
  marking.value = false
  try {
    const d = await fetchDrawing(id)
    drawing.value = d.drawing
    issues.value = d.issues || []
  } finally {
    loading.value = false
  }
}

const toggleFullscreen = () => {
  const el = rootRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    if (el.requestFullscreen) el.requestFullscreen()
    else ElMessage.warning('当前浏览器不支持全屏')
  } else {
    document.exitFullscreen()
  }
}

// 图片：直接点击图面标记
const onDrawClick = ev => {
  if (!canMark.value) return
  const el = imgEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = Math.max(0, Math.min(100, (ev.clientX - rect.left) / rect.width * 100))
  const y = Math.max(0, Math.min(100, (ev.clientY - rect.top) / rect.height * 100))
  startMarkAt(+x.toFixed(2), +y.toFixed(2))
}
const startMark = () => { ElMessage.info('请点击图面位置标记问题') }
const startMarkAt = (x, y) => {
  Object.assign(form, { level: 'MEDIUM', category: '图面表达', description: '', suggestion: '', assigneeName: '', markerX: x, markerY: y })
  editId.value = null
  formVisible.value = true
}

// CAD（DWG/DXF）：标记模式 + 点击图面
const toggleMarking = () => {
  marking.value = !marking.value
}
const onCadMark = pos => {
  marking.value = false
  Object.assign(form, { level: 'MEDIUM', category: '图面表达', description: '', suggestion: '', assigneeName: '', markerX: pos.x, markerY: pos.y })
  editId.value = null
  formVisible.value = true
}
const selectIssue = it => { selId.value = selId.value === it.id ? null : it.id }

const onSave = async () => {
  if (!form.description) return ElMessage.warning('请填写问题描述')
  saving.value = true
  try {
    const payload = { ...form }
    if (editId.value) {
      await updateIssue(editId.value, payload)
      ElMessage.success('问题已更新')
    } else {
      await addIssue(drawingId.value, payload)
      ElMessage.success('问题已标记')
    }
    formVisible.value = false
    await selectDrawing(drawingId.value)
  } finally {
    saving.value = false
  }
}

const resolve = async it => {
  const { value } = await ElMessageBox.prompt('填写解决说明', '标记已解决', { inputType: 'textarea', inputPlaceholder: '如：已与设计院核实并修正' }).catch(() => ({ value: '' }))
  if (value === undefined) return
  await resolveIssue(it.id, value)
  ElMessage.success('已标记解决')
  await selectDrawing(drawingId.value)
}
const dismiss = async it => {
  await ElMessageBox.confirm('将该问题标记为「无需处理」（不适用/误报）？', '确认', { type: 'warning' })
  await dismissIssue(it.id)
  ElMessage.success('已标记无需处理')
  await selectDrawing(drawingId.value)
}
const del = async it => {
  await ElMessageBox.confirm('确认删除该未解决问题？', '确认', { type: 'warning' })
  await deleteIssue(it.id)
  ElMessage.success('已删除')
  await selectDrawing(drawingId.value)
}

const onAiReview = async () => {
  aiLoading.value = true
  try {
    await aiReview(drawingId.value)
    await selectDrawing(drawingId.value)
    ElMessage.success('AI/系统预审完成，请逐条核对')
  } finally {
    aiLoading.value = false
  }
}
const onPass = async () => {
  await ElMessageBox.confirm('确认通过审查？（要求无「高」级未解决问题）', '通过审查', { type: 'success' })
  await passDrawing(drawingId.value)
  await selectDrawing(drawingId.value)
  ElMessage.success('已通过审查')
}
const onReject = () => { rejectReason.value = ''; rejectVisible.value = true }
const confirmReject = async () => {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写驳回原因')
  await rejectDrawing(drawingId.value, rejectReason.value)
  rejectVisible.value = false
  await selectDrawing(drawingId.value)
  ElMessage.success('已驳回')
}
const onReport = () => {
  const html = buildReportHtml(drawing.value, issues.value, 'AI 审查仅供参考，以人工审查为准')
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank')
  setTimeout(() => URL.revokeObjectURL(url), 60000)
  ElMessage.success('审查报告已在新标签页打开')
}
const zoomBy = d => { zoom.value = Math.max(0.5, Math.min(4, +(zoom.value + d).toFixed(2))) }

onMounted(async () => {
  await loadDrawings()
  const qid = route.query.id ? Number(route.query.id) : (drawings.value[0]?.id ?? null)
  if (qid) {
    drawingId.value = qid
    await selectDrawing(qid)
  }
})
</script>

<style scoped>
.online-review { display: flex; flex-direction: column; height: calc(100vh - 90px); background: #fff; }
.topbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-bottom: 1px solid #ebeef5; flex-wrap: wrap; gap: 8px; }
.topbar .left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lbl { font-weight: 600; color: #303133; }
.meta { color: #909399; font-size: 13px; }
.actions { display: flex; gap: 6px; }
.body { flex: 1; display: flex; min-height: 0; }
.viewer-pane { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; border-right: 1px solid #ebeef5; }
.viewer-toolbar { display: flex; align-items: center; gap: 6px; padding: 8px 12px; border-bottom: 1px solid #f0f2f5; flex-wrap: wrap; }
.zoom-val { font-size: 12px; color: #606266; min-width: 42px; }
.mk-switch { margin-left: 8px; }
.tip { color: #e6a23c; font-size: 12px; margin-left: 8px; }
.viewport { flex: 1; overflow: auto; background: #eef1f5; padding: 16px; display: flex; align-items: flex-start; justify-content: flex-start; }
.viewport :deep(.el-empty) { margin: auto; }
.cad-viewer-wrap { align-self: stretch; width: 100%; min-height: 60vh; background: #fff; box-shadow: 0 2px 12px rgba(0,0,0,.12); }
.drawing-box { position: relative; display: inline-block; line-height: 0; box-shadow: 0 2px 12px rgba(0,0,0,.12); background: #fff; }
.drawing-img { display: block; height: auto; cursor: crosshair; user-select: none; }
.markers { position: absolute; inset: 0; pointer-events: none; }
.marker { position: absolute; transform: translate(-50%, -50%); width: 26px; height: 26px; border-radius: 50%;
  color: #fff; font-size: 13px; font-weight: 700; display: flex; align-items: center; justify-content: center;
  pointer-events: auto; cursor: pointer; border: 2px solid #fff; box-shadow: 0 1px 6px rgba(0,0,0,.4); z-index: 5; }
.marker.lv-HIGH { background: #c45656; }
.marker.lv-MEDIUM { background: #e6a23c; }
.marker.lv-LOW { background: #909399; }
.marker.sel { outline: 3px solid #409eff; outline-offset: 1px; transform: translate(-50%, -50%) scale(1.15); }
.online-review:fullscreen { height: 100vh; width: 100vw; }
.pdf-box { width: 100%; height: 100%; }
.pdf-frame { width: 100%; height: 100%; border: none; background: #fff; }
.hint { padding: 8px 12px; color: #909399; font-size: 12px; border-top: 1px solid #f0f2f5; }
.issue-pane { flex: 0 1 380px; min-width: 240px; max-width: 420px; display: flex; flex-direction: column; background: #fafbfc; }
.issue-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; font-weight: 600; border-bottom: 1px solid #ebeef5; }
.issue-header .cnt { font-size: 12px; font-weight: 400; color: #909399; }
.c-open { color: #c45656; } .c-res { color: #529b2e; }
.issue-list { flex: 1; }
.issue-list :deep(.el-scrollbar__wrap) { overflow-x: hidden; }
.empty { padding: 40px 0; }
.issue-item { padding: 10px 14px; border-bottom: 1px solid #f0f2f5; cursor: pointer; transition: background .15s; }
.issue-item:hover { background: #f5f7fa; }
.issue-item.sel { background: #ecf5ff; }
.issue-item.done { opacity: .8; }
.it-top { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.it-cat { font-size: 12px; color: #606266; }
.it-desc { margin-top: 6px; font-size: 13px; color: #303133; line-height: 1.5; }
.it-sug { margin-top: 4px; font-size: 12px; color: #409eff; }
.it-loc { margin-top: 4px; font-size: 12px; color: #909399; }
.it-actions { margin-top: 6px; display: flex; gap: 4px; }
.it-res { margin-top: 6px; font-size: 12px; color: #529b2e; }
.mk-no { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; color: #fff; font-size: 12px; font-weight: 700; }
.mk-no.lv-HIGH { background: #c45656; } .mk-no.lv-MEDIUM { background: #e6a23c; } .mk-no.lv-LOW { background: #909399; }
.mk-pos { font-size: 12px; color: #409eff; } .mk-pos.muted { color: #909399; }

/* 窄视口（如内嵌浏览器 / 小窗口）：上下堆叠，保证图纸查看器有可用宽度 */
@media (max-width: 860px) {
  .online-review { height: auto; min-height: calc(100vh - 90px); }
  .body { flex-direction: column; overflow: auto; }
  .viewer-pane { border-right: none; border-bottom: 1px solid #ebeef5; min-height: 56vh; }
  .viewport { min-height: 48vh; }
  .issue-pane { flex: none; width: 100%; max-width: none; min-width: 0; max-height: 42vh; border-left: none; }
  .cad-viewer-wrap, .dxf-viewer-wrap { min-height: 44vh; }
}
</style>
