<template>
  <div class="subway-wrap">
    <div class="subway">
      <div v-if="overallPct != null" class="train" :style="{ left: trainLeft }">
        <div class="train-tag">当前 {{ Math.round(overallPct) }}%</div>
        <div class="train-dot" />
      </div>
      <div
        v-for="(s, i) in stations" :key="s.id"
        class="station" :class="'st-' + stateOf(s)"
        :style="{ cursor: clickable ? 'pointer' : 'default' }"
        @click="clickable && $emit('select', s)"
      >
        <div v-if="i > 0" class="seg seg-l" :class="{ done: isDone(stations[i - 1]) }" />
        <div v-if="i < stations.length - 1" class="seg seg-r" :class="{ done: isDone(s) }" />
        <div class="node" />
        <div class="s-name">{{ s.taskName }}</div>
        <div class="s-date">计划 {{ fmtDate(s.planEnd) }}</div>
        <div class="s-sub" :class="'sub-' + stateOf(s)">{{ subText(s) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmtDate } from '../../utils/format'

const props = defineProps({
  stations: { type: Array, default: () => [] },
  overallPct: { type: Number, default: null },
  clickable: { type: Boolean, default: true }
})
defineEmits(['select'])

const trainLeft = computed(() =>
  `calc(${Math.min(100, Math.max(0, props.overallPct || 0))}%)`)

const isDone = s => s.status === 'COMPLETED'

const stateOf = s => {
  if (s.status === 'COMPLETED') return 'done'
  if (s.status === 'DELAYED' || s.alertStatus === 'RED') return 'red'
  if (s.alertStatus === 'YELLOW') return 'yellow'
  if (s.status === 'IN_PROGRESS') return 'doing'
  return 'plan'
}

const subText = s => {
  if (s.status === 'COMPLETED') return s.actualEnd ? fmtDate(s.actualEnd) : '已完成'
  if (Number(s.overdueDays) > 0) return `滞后 ${s.overdueDays} 天`
  if (s.status === 'IN_PROGRESS') return `进度 ${Number(s.progressPct) || 0}%`
  return '未开始'
}
</script>

<style scoped>
.subway-wrap { overflow-x: auto; }
.subway { position: relative; display: flex; padding: 34px 6px 4px; min-width: max-content; }
.station { position: relative; flex: 0 0 128px; display: flex; flex-direction: column; align-items: center; }
.seg { position: absolute; top: 7px; height: 4px; width: 50%; background: repeating-linear-gradient(90deg, #c9d4de 0 6px, transparent 6px 10px); }
.seg-l { left: 0; }
.seg-r { right: 0; }
.seg.done { background: #185fa5; }
.node { position: relative; z-index: 1; width: 18px; height: 18px; border-radius: 50%; background: #fff; border: 4px solid #c0c4cc; }
.st-done .node { background: #67c23a; border-color: #67c23a; }
.st-doing .node { background: #185fa5; border-color: #185fa5; box-shadow: 0 0 0 4px rgba(24, 95, 165, .15); }
.st-red .node { background: #f56c6c; border-color: #f56c6c; box-shadow: 0 0 0 4px rgba(245, 108, 108, .18); }
.st-yellow .node { background: #e6a23c; border-color: #e6a23c; }
.s-name { margin-top: 8px; font-size: 13px; font-weight: 700; color: #1f2937; white-space: nowrap; }
.station:hover .s-name { color: #185fa5; }
.s-date { margin-top: 3px; font-size: 11.5px; color: #909399; white-space: nowrap; }
.s-sub { margin-top: 3px; font-size: 11.5px; white-space: nowrap; }
.sub-done { color: #67c23a; }
.sub-doing { color: #185fa5; }
.sub-red { color: #f56c6c; font-weight: 700; }
.sub-yellow { color: #e6a23c; }
.sub-plan { color: #909399; }
.train { position: absolute; top: 2px; transform: translateX(-50%); z-index: 2; display: flex; flex-direction: column; align-items: center; pointer-events: none; }
.train-tag { font-size: 11px; color: #185fa5; background: #eaf2fb; border: 1px solid #bcd6ef; border-radius: 10px; padding: 1px 8px; white-space: nowrap; }
.train-dot { width: 2px; height: 19px; background: #185fa5; margin-top: 1px; }
</style>
