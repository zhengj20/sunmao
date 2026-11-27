<template>
  <div class="cm">
    <table v-if="buildings.length" class="cm-table">
      <thead>
        <tr><th>楼栋</th><th v-for="p in parts" :key="p">{{ p }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="b in buildings" :key="b">
          <td class="b">{{ b }}</td>
          <td v-for="p in parts" :key="p" class="c">
            <template v-if="cellOf(b, p)">
              <div class="c-pct num">{{ Math.round(Number(cellOf(b, p).confirmedPct)) }}%</div>
              <div class="c-bar"><div class="c-bar-in" :style="{ width: Math.min(100, Number(cellOf(b, p).confirmedPct)) + '%' }" /></div>
              <div class="c-date">{{ fmtDate(cellOf(b, p).confirmDate) }}</div>
            </template>
            <span v-else class="c-none">-</span>
          </td>
        </tr>
      </tbody>
    </table>
    <el-empty v-else description="暂无已确认形象进度" :image-size="60" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmtDate } from '../../utils/format'
import { IMAGE_PARTS } from '../../api/plan'

const props = defineProps({
  rows: { type: Array, default: () => [] }
})

const parts = IMAGE_PARTS

const cellMap = computed(() => {
  const m = {}
  for (const r of props.rows || []) {
    const key = (r.building || '') + '|' + (r.partName || '')
    const cur = m[key]
    if (!cur || Number(r.confirmedPct) > Number(cur.confirmedPct)) m[key] = r
  }
  return m
})

const buildings = computed(() => {
  const out = []
  for (const r of props.rows || []) {
    if (r.building && !out.includes(r.building)) out.push(r.building)
  }
  return out
})

const cellOf = (b, p) => cellMap.value[b + '|' + p] || null
</script>

<style scoped>
.cm-table { width: 100%; border-collapse: collapse; }
.cm-table th { font-size: 12px; color: #6b7280; font-weight: 600; padding: 8px 10px; border-bottom: 1px solid #eef0f3; text-align: center; }
.cm-table th:first-child { text-align: left; }
.cm-table td { padding: 10px; border-bottom: 1px dashed #eef0f3; text-align: center; vertical-align: top; }
.cm-table tr:last-child td { border-bottom: none; }
.b { font-weight: 700; color: #0c447c; text-align: left; white-space: nowrap; }
.c-pct { font-size: 14px; font-weight: 700; color: #1f2937; }
.num { font-variant-numeric: tabular-nums; }
.c-bar { width: 72%; height: 6px; background: #eef2f7; border-radius: 3px; overflow: hidden; margin: 5px auto 0; }
.c-bar-in { height: 100%; background: #185fa5; border-radius: 3px; }
.c-date { font-size: 11px; color: #909399; margin-top: 4px; }
.c-none { color: #c0c4cc; }
</style>
