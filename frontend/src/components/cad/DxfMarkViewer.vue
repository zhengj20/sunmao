<template>
  <div class="dxf-mark-viewer" ref="rootRef">
    <div class="viewer-container" ref="containerRef">
      <DXFViewer
        ref="viewerRef"
        :url="fileUrl"
        show-fullscreen-button
        show-measure-button
        show-measure-area-button
        show-measure-angle-button
        show-coordinates
        show-zoom-level
        show-layer-panel
        auto-fit
        @dxf-loaded="onDxfLoaded"
        style="height:100%; width:100%; display:block" />
    </div>

    <div v-if="marking" class="mark-overlay" @click="onMarkClick">
      <div class="mark-hint">点击图面位置标记问题（记号会锚定在图纸上，随平移/缩放移动）</div>
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
import 'dxf-vuer/style.css'
import { ref, h, defineAsyncComponent, watch, onBeforeUnmount } from 'vue'
import * as THREE from 'three'

const props = defineProps({
  fileUrl: { type: String, required: true },
  marking: { type: Boolean, default: false },
  showMarkers: { type: Boolean, default: true },
  markers: { type: Array, default: () => [] },
  selectedId: { type: [String, Number], default: null }
})

const emit = defineEmits(['mark', 'selectMarker'])

const rootRef = ref(null)
const containerRef = ref(null)
const viewerRef = ref(null)
const previewRef = ref(null)
const previewMark = ref(null)
const previewLevel = 'MEDIUM'

const DXFViewer = defineAsyncComponent({
  loader: () => import('dxf-vuer').then(m => m.DXFViewer),
  delay: 200,
  timeout: 60000,
  loading: () => h('div', { class: 'dwg-tip' }, '正在加载 DXF 查看器...'),
  error: (err) => h('div', { class: 'dwg-tip error' },
    'DXF 查看器加载失败：' + ((err && err.message) || '未知错误'))
})

/* ---------- 捕获相机：记号随平移/缩放跟随的关键 ---------- */
let capturedCamera = null
let capturedRenderer = null
let rafId = null

/** dxf-vuer 每次渲染（平移/缩放/重置/自适应）都会调用 renderer.render，借此捕获相机 */
function onDxfLoaded() {
  const v = viewerRef.value
  if (!v || typeof v.getRenderer !== 'function') return
  const renderer = v.getRenderer()
  if (!renderer) return
  capturedRenderer = renderer
  if (!renderer.__markerHooked) {
    renderer.__markerHooked = true
    const origRender = renderer.render.bind(renderer)
    renderer.render = (scene, camera) => {
      const result = origRender(scene, camera)
      if (camera) capturedCamera = camera
      return result
    }
  }
  // 触发一次渲染以捕获相机
  try { v.resize && v.resize() } catch (e) { /* ignore */ }
  startRaf()
}

/** 连续 rAF 循环：每帧用已捕获相机把记号（场景坐标）投影到屏幕位置，确保始终贴合图纸 */
function startRaf() {
  if (rafId) return
  const loop = () => {
    if (capturedCamera) positionMarkers(capturedCamera)
    rafId = requestAnimationFrame(loop)
  }
  rafId = requestAnimationFrame(loop)
}

/** 将记号（场景坐标）投影为屏幕坐标，直接写 DOM，避免每帧 Vue 重渲染 */
function positionMarkers(cam) {
  if (!cam) return
  const renderer = capturedRenderer
  if (!renderer || !renderer.domElement) return
  const canvas = renderer.domElement
  const cw = canvas.clientWidth || canvas.getBoundingClientRect().width
  const ch = canvas.clientHeight || canvas.getBoundingClientRect().height
  if (!cw || !ch) return
  const base = rootRef.value
  if (!base) return
  const canvasRect = canvas.getBoundingClientRect()
  const baseRect = base.getBoundingClientRect()
  const ox = canvasRect.left - baseRect.left
  const oy = canvasRect.top - baseRect.top
  const v = new THREE.Vector3()
  const place = (x, y, div) => {
    if (div == null) return
    v.set(x, y, 0).project(cam)
    div.style.left = ((v.x + 1) / 2 * cw + ox) + 'px'
    div.style.top = ((-v.y + 1) / 2 * ch + oy) + 'px'
  }
  // 已保存记号
  const host = base.querySelector('.markers')
  if (host) {
    host.querySelectorAll('[data-mid]').forEach(div => {
      const m = props.markers.find(x => String(x.id) === div.dataset.mid)
      if (m) place(Number(m.markerX), Number(m.markerY), div)
    })
  }
  // 预览记号
  if (previewRef.value && previewMark.value) {
    place(previewMark.value.x, previewMark.value.y, previewRef.value)
  }
}

/** 点击图面 → 场景（图纸）坐标；记号锚定到图纸上 */
function onMarkClick(e) {
  if (!props.marking) return
  const cam = capturedCamera
  const renderer = capturedRenderer
  if (!cam || !renderer || !renderer.domElement) {
    // 相机尚未就绪时退回容器百分比（兜底）
    const el = containerRef.value
    if (!el) return
    const r = el.getBoundingClientRect()
    const x = r.width > 0 ? Math.max(0, Math.min(100, (e.clientX - r.left) / r.width * 100)) : 50
    const y = r.height > 0 ? Math.max(0, Math.min(100, (e.clientY - r.top) / r.height * 100)) : 50
    previewMark.value = { x: +x.toFixed(2), y: +y.toFixed(2) }
    emit('mark', { x: +x.toFixed(2), y: +y.toFixed(2) })
    return
  }
  const rect = renderer.domElement.getBoundingClientRect()
  const ndcX = (e.clientX - rect.left) / rect.width * 2 - 1
  const ndcY = -((e.clientY - rect.top) / rect.height) * 2 + 1
  const scene = new THREE.Vector3(ndcX, ndcY, 0).unproject(cam)
  previewMark.value = { x: +scene.x.toFixed(4), y: +scene.y.toFixed(4) }
  emit('mark', { x: +scene.x.toFixed(4), y: +scene.y.toFixed(4) })
}

// 记号列表变化（保存/删除/换图）后清除预览并重新定位
watch(() => props.markers, () => {
  previewMark.value = null
  positionMarkers(capturedCamera)
})
watch(() => props.showMarkers, (v) => { if (v) positionMarkers(capturedCamera) })

onBeforeUnmount(() => {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
  capturedCamera = null
  capturedRenderer = null
})
</script>

<style scoped>
.dxf-mark-viewer {
  width: 100%;
  height: 100%;
  position: relative;
  background: #fff;
}

.viewer-container {
  width: 100%;
  height: 100%;
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
