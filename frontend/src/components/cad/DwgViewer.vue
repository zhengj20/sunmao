<template>
  <div class="dwg-viewer" ref="rootRef">
    <MlCadViewer v-if="fileUrl" :url="absFileUrl" locale="zh" class="ml-cad" />

    <div v-if="marking" class="mark-overlay" @click="onMarkClick">
      <div class="mark-hint">点击图面位置标记问题（记号锚定图纸，随平移/缩放移动）</div>
    </div>

    <!-- 记号：锚定图纸坐标，随平移/缩放跟随，始终在图面最上层 -->
    <div v-if="showMarkers && markers.length" class="markers">
      <div
        v-for="m in markers"
        :key="m.id"
        :data-mid="m.id"
        class="marker"
        :class="['lv-' + m.level, { sel: selectedId === m.id }]"
        :title="m.description"
        @click.stop="$emit('selectMarker', m)">
        {{ m.no }}
      </div>
    </div>

    <!-- 添加问题时即时预览记号（弹窗上方可见） -->
    <div v-if="previewMark" ref="previewRef" class="marker preview" :class="'lv-' + previewLevel">＋</div>
  </div>
</template>

<script setup>
import { ref, h, computed, getCurrentInstance, defineAsyncComponent, onMounted, onBeforeUnmount, watch } from 'vue'
import { AcApDocManager } from '@mlightcad/cad-simple-viewer'

const props = defineProps({
  fileUrl: { type: String, required: true },
  marking: { type: Boolean, default: false },
  showMarkers: { type: Boolean, default: true },
  markers: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null }
})

// MlCadViewer 的 openUrl 内部用 new URL(url)，要求"绝对 URL"。
// 后端返回的是相对路径（/files/...），这里统一补全为绝对地址，否则加载直接抛 Invalid URL。
const absFileUrl = computed(() => {
  if (!props.fileUrl) return props.fileUrl
  try { return new URL(props.fileUrl, window.location.origin).href } catch (e) { return props.fileUrl }
})

const emit = defineEmits(['mark', 'selectMarker'])

const rootRef = ref(null)
const previewRef = ref(null)
const previewMark = ref(null)
const previewLevel = 'MEDIUM'
let rafId = null

const MlCadViewer = defineAsyncComponent({
  loader: async () => {
    // 必须在 await 之前捕获组件实例（await 是异步边界，之后 getCurrentInstance() 为 null）
    const inst = getCurrentInstance()
    const app = inst && inst.appContext && inst.appContext.app
    const m = await import('@mlightcad/cad-viewer')
    // MlCadViewer 内部 useI18n() 依赖全局 i18n 实例，必须先安装
    if (m && m.i18n && app && !app.__VUE_I18N_SYMBOL__) app.use(m.i18n)
    return m.MlCadViewer
  },
  delay: 200,
  timeout: 60000,
  loading: () => h('div', { class: 'dwg-tip' }, '正在加载 CAD 查看器（首次加载较慢，请稍候）...'),
  error: (err) => h('div', { class: 'dwg-tip error' },
    'CAD 查看器加载失败：' + ((err && err.message) || '未知错误'))
})

/* ---------- 获取 mlightcad 活动视图（含 worldToScreen / screenToWorld） ---------- */
function getView() {
  try {
    const dm = AcApDocManager.tryGetInstance && AcApDocManager.tryGetInstance()
    if (!dm) return null
    const v = dm.curView || dm.mainView
    return (v && v.worldToScreen && v.screenToWorld) ? v : null
  } catch (e) { return null }
}

function getCanvas() {
  return rootRef.value ? rootRef.value.querySelector('canvas') : null
}



/* ---------- 记号投影：世界坐标 → 屏幕坐标（直接写 DOM，避免每帧 Vue 重渲染） ---------- */
function positionMarkers() {
  const view = getView()
  const canvas = getCanvas()
  const base = rootRef.value
  if (!view || !canvas || !base) return
  const cr = canvas.getBoundingClientRect()
  const br = base.getBoundingClientRect()
  if (!cr.width || !cr.height) return
  const ox = cr.left - br.left
  const oy = cr.top - br.top
  const place = (wx, wy, div) => {
    if (!div) return
    let sp
    try { sp = view.worldToScreen({ x: wx, y: wy }) } catch (e) { return }
    div.style.left = (sp.x + ox) + 'px'
    div.style.top = (sp.y + oy) + 'px'
  }
  const host = base.querySelector('.markers')
  if (host) {
    host.querySelectorAll('[data-mid]').forEach(div => {
      const m = props.markers.find(x => String(x.id) === div.dataset.mid)
      if (m && m.markerX != null) place(Number(m.markerX), Number(m.markerY), div)
    })
  }
  if (previewRef.value && previewMark.value) place(previewMark.value.x, previewMark.value.y, previewRef.value)
}

/* ---------- rAF 循环：平移/缩放时每帧重新投影，确保记号始终贴合图纸 ---------- */
function startRaf() {
  if (rafId) return
  const loop = () => {
    positionMarkers()
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

/* ---------- 点击图面 → 世界坐标（记号锚定点） ---------- */
function onMarkClick(e) {
  if (!props.marking) return
  const view = getView()
  const canvas = getCanvas()
  if (!view || !canvas) return
  const rect = canvas.getBoundingClientRect()
  let world
  try { world = view.screenToWorld({ x: e.clientX - rect.left, y: e.clientY - rect.top }) } catch (err) { return }
  previewMark.value = { x: +world.x.toFixed(4), y: +world.y.toFixed(4) }
  emit('mark', { x: +world.x.toFixed(4), y: +world.y.toFixed(4) })
}

/* ---------- 轮询等待视图就绪后启动投影（首次加载较慢，给足时间窗口） ---------- */
let pollTimer = null
function startPolling() {
  stopPolling()
  let tries = 0
  const tick = () => {
    if (getView() && getCanvas()) { startRaf(); positionMarkers(); stopPolling(); return }
    if (++tries > 240) { stopPolling(); return }
    pollTimer = setTimeout(tick, 500)
  }
  tick()
}
function stopPolling() { if (pollTimer) { clearTimeout(pollTimer); pollTimer = null } }

onMounted(() => startPolling())
watch(() => props.markers, () => { previewMark.value = null; positionMarkers() })
watch(() => props.showMarkers, (v) => { if (v) positionMarkers() })
onBeforeUnmount(() => {
  stopPolling()
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
})
</script>

<style scoped>
.dwg-viewer {
  width: 100%;
  height: 100%;
  position: relative;
  background: #1e2228;
  min-height: 400px;
}

.ml-cad {
  width: 100%;
  height: 100%;
  display: block;
}

.dwg-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
  color: #909399;
  font-size: 14px;
}

.dwg-tip.error {
  color: #c45656;
}

.mark-overlay {
  position: absolute;
  inset: 0;
  z-index: 9999;
  cursor: crosshair;
  background: rgba(64, 158, 255, 0.06);
}

.mark-hint {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(64, 158, 255, 0.9);
  color: #fff;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
}

/* 记号层：覆盖整个查看器，始终位于图面最上层 */
.markers {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 5000;
  overflow: hidden;
}

.marker {
  position: absolute;
  transform: translate(-50%, -50%);
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 6px rgba(0,0,0,.4);
  z-index: 5001;
  left: 0;
  top: 0;
  will-change: left, top;
}

.marker.lv-HIGH { background: #c45656; }
.marker.lv-MEDIUM { background: #e6a23c; }
.marker.lv-LOW { background: #909399; }

.marker.sel {
  outline: 3px solid #409eff;
  outline-offset: 1px;
  transform: translate(-50%, -50%) scale(1.15);
}

.marker.preview {
  z-index: 6000;
  animation: mk-pulse 1s ease-out infinite;
  border-color: #fff;
}

@keyframes mk-pulse {
  0% { box-shadow: 0 0 0 0 rgba(64,158,255,.6); }
  100% { box-shadow: 0 0 0 12px rgba(64,158,255,0); }
}
</style>
